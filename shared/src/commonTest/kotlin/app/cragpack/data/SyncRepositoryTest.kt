package app.cragpack.data

import app.cragpack.domain.grade.Grade
import app.cragpack.domain.grade.GradeScale
import app.cragpack.domain.log.LogStyle
import app.cragpack.domain.objective.ClimbStyle
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectIndexed
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.datetime.LocalDate
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Clock

class SyncRepositoryTest {
    private val lea = "00000000-0000-4000-8000-00000000000a"
    private val tom = "00000000-0000-4000-8000-00000000000b"

    /** New rows (upserts) per server table, in the order they arrived. */
    private val pushed = mutableMapOf<String, MutableList<JsonObject>>()

    /** Field edits (PATCH): "table?filter" to the fields sent. */
    private val edited = mutableListOf<Pair<String, JsonObject>>()
    private var pull = """{"trips":[],"cursor":"2026-11-20T10:00:00+00:00"}"""
    private var refuseEdits = false

    private val engine = MockEngine { request ->
        val path = request.url.encodedPath
        val body = when {
            path.endsWith("/token") -> """{"access_token":"a","refresh_token":"r","expires_at":${Clock.System.now().epochSeconds + 3600},
                "user":{"id":"$lea","email":"lea@example.com","user_metadata":{"name":"Léa"}}}"""
            path.endsWith("/rpc/sync_pull") -> pull
            request.method == HttpMethod.Patch && refuseEdits ->
                return@MockEngine respond("""{"code":"42501","message":"new row violates row-level security policy"}""", HttpStatusCode.Forbidden)
            request.method == HttpMethod.Patch -> {
                edited += "${path.substringAfterLast('/')}?${request.url.encodedQuery}" to
                    Json.parseToJsonElement((request.body as TextContent).text).jsonObject
                ""
            }
            else -> {
                val rows = Json.parseToJsonElement((request.body as TextContent).text).jsonArray.map { it.jsonObject }
                pushed.getOrPut(path.substringAfterLast('/')) { mutableListOf() } += rows
                ""
            }
        }
        respond(body, HttpStatusCode.OK, headersOf("Content-Type", "application/json"))
    }
    private val graph = AppGraph(testDriver(), Clock.System, Supabase(HttpClient(engine), "https://x.supabase.co", "key"))

    private suspend fun signedInWithATrip(): String {
        val trip = graph.trips.create("Font Easter", "Fontainebleau", LocalDate.parse("2026-04-17"), LocalDate.parse("2026-04-18"))
        graph.account.signIn("lea@example.com", "secret")
        return trip
    }

    @Test
    fun firstSyncUploadsWhatWasMadeOnThePhone() = runTest {
        val trip = signedInWithATrip()

        graph.sync.syncNow()

        assertEquals("Font Easter", pushed.getValue("trip").single().str("name"))
        assertEquals(lea, pushed.getValue("trip").single().str("owner_id"))
        assertEquals(listOf(trip to lea), pushed.getValue("trip_member").map { it.str("trip_id") to it.str("user_id") })
        assertEquals(2, pushed.getValue("day").size)
        assertEquals("Léa", pushed.getValue("profile").single().str("name"))
        // Parents before children: the server's access rules need the trip first.
        assertEquals(listOf("profile", "trip", "trip_member", "day"), pushed.keys.toList())
        assertEquals(SyncState(signedIn = true, pending = 0), graph.sync.observe().first())
    }

    @Test
    fun anEditSendsOnlyTheFieldThatChanged() = runTest {
        val trip = signedInWithATrip()
        graph.sync.syncNow()
        pushed.clear()

        graph.trips.rename(trip, "Font Easter 2", "Fontainebleau")
        assertEquals(1, graph.sync.observe().first().pending)
        graph.sync.syncNow()

        assertEquals(emptyMap(), pushed)
        assertEquals("trip?id=eq.$trip", edited.single().first)
        assertEquals(setOf("name", "updated_at"), edited.single().second.keys)
        assertEquals(0, graph.sync.observe().first().pending)
    }

    @Test
    fun pulledChangesLandWithoutBeingSentBackAndKeepMyPendingEdits() = runTest {
        val trip = signedInWithATrip()
        graph.sync.syncNow()
        graph.trips.rename(trip, "Mine", "Fontainebleau") // not pushed yet
        pull = """{"trips":["$trip"],"cursor":"2026-11-20T10:00:00+00:00",
            "trip":[{"id":"$trip","trip_id":"$trip","name":"Theirs","crag":"Bleau","start_date":"2026-04-17","end_date":"2026-04-18",
                "cover_media_id":null,"owner_id":"$lea","created_at":1,"updated_at":9,"deleted_at":null}],
            "trip_member":[{"trip_id":"$trip","user_id":"$tom","role":"member","color_index":2,"joined_at":4102444800000,"updated_at":5,"deleted_at":null}],
            "profile":[{"id":"$tom","name":"Tom","updated_at":5},{"id":"$lea","name":"Someone else","updated_at":5}]}"""
        pushed.clear()

        graph.sync.syncNow()

        val item = graph.trips.observeTrips().first().single()
        assertEquals("Mine", item.trip.name) // my edit is newer: it goes up, theirs doesn't overwrite it
        assertEquals("Bleau", item.trip.crag) // their edit to another field lands
        assertEquals(listOf("Léa", "Tom"), item.crew.map { it.name })
        assertEquals(emptyMap(), pushed) // nothing pulled is echoed back…
        assertEquals(listOf("trip?id=eq.$trip" to "Mine"), edited.map { it.first to it.second.str("name") }) // …only my rename goes up
    }

    @Test
    fun theStatusPillHearsAboutEditsAsTheyHappen() = runTest {
        val trip = signedInWithATrip()
        graph.sync.syncNow()
        val seen = mutableListOf<Int>()

        withContext(Dispatchers.Default) {
            withTimeout(5_000) {
                graph.sync.observe().map { it.pending }.distinctUntilChanged().take(2).collectIndexed { i, n ->
                    seen += n
                    if (i == 0) graph.trips.rename(trip, "Font Easter 2", "Fontainebleau") // while someone is watching
                }
            }
        }

        assertEquals(listOf(0, 1), seen)
    }

    @Test
    fun aRefusedEditIsDroppedInsteadOfBlockingTheRest() = runTest {
        val trip = signedInWithATrip()
        graph.sync.syncNow()
        graph.trips.rename(trip, "Font Easter 2", "Fontainebleau")
        refuseEdits = true

        graph.sync.syncNow() // doesn't throw

        assertEquals(0, graph.sync.observe().first().pending)
    }

    @Test
    fun aTripILeftLeavesThePhoneButMySendsStay() = runTest {
        val trip = signedInWithATrip()
        val objective = graph.objectives.add(trip, "La Joker", ClimbStyle.Boulder, Grade(GradeScale.Font, "7A"), null)
        graph.logs.log(objective, LogStyle.Sent)
        pull = """{"trips":["$trip"],"cursor":"2026-11-20T10:00:00+00:00"}"""
        graph.sync.syncNow() // pulled once: the phone knows the server has it

        pull = """{"trips":[],"cursor":"2026-11-20T10:05:00+00:00"}"""
        graph.sync.syncNow()

        assertEquals(emptyList(), graph.trips.observeTrips().first())
        assertEquals(listOf("La Joker"), graph.logs.observeLogbook().first().map { it.name })
        assertEquals(0, graph.sync.observe().first().pending) // forgetting isn't an edit to send
    }

    @Test
    fun aTripNotYetUploadedIsKeptEvenThoughTheServerDoesntListIt() = runTest {
        signedInWithATrip()
        graph.sync.syncNow()
        graph.trips.create("Magic Wood", "Averstal", LocalDate.parse("2026-07-01"), LocalDate.parse("2026-07-05"))

        graph.sync.syncNow()

        assertEquals(2, graph.trips.observeTrips().first().size)
    }

    /** Planned on day 1 and synced; then I move it to day 2 offline while Tom moves it to day 3. */
    private suspend fun twoMovesAtOnce(): Pair<String, List<String>> {
        val trip = graph.trips.create("Font Easter", "Fontainebleau", LocalDate.parse("2026-04-17"), LocalDate.parse("2026-04-19"))
        graph.account.signIn("lea@example.com", "secret")
        val objective = graph.objectives.add(trip, "Carnage", ClimbStyle.Boulder, Grade(GradeScale.Font, "7B+"), null)
        val days = graph.plan.observeDays(trip).first().map { it.id }
        graph.plan.place(objective, days[0], 0)
        pull = """{"trips":["$trip"],"cursor":"2026-11-20T10:00:00+00:00"}"""
        graph.sync.syncNow()

        graph.plan.place(objective, days[1], 0) // mine, not pushed yet
        pull = """{"trips":["$trip"],"cursor":"2026-11-20T10:05:00+00:00",
            "trip_member":[{"trip_id":"$trip","user_id":"$tom","role":"member","color_index":2,"joined_at":4102444800000,"updated_at":5,"deleted_at":null}],
            "profile":[{"id":"$tom","name":"Tom","updated_at":5}],
            "plan_item":[{"objective_id":"$objective","trip_id":"$trip","day_id":"${days[2]}","order_key":"a","updated_at":1795000000000,
                "deleted_at":null,"updated_by":"$tom"}]}"""
        pushed.clear()
        edited.clear()
        graph.sync.syncNow()
        return trip to days
    }

    @Test
    fun twoMovesToDifferentDaysWaitForAChoice() = runTest {
        val (trip, days) = twoMovesAtOnce()

        val conflict = graph.plan.observeConflicts(trip).first().single()
        assertEquals("Carnage", conflict.name)
        assertEquals(LocalDate.parse("2026-04-18"), conflict.mine.date)
        assertEquals(LocalDate.parse("2026-04-19"), conflict.theirs.date)
        assertEquals("Tom", conflict.theirs.by?.name)
        assertEquals(days[1], graph.plan.observePlan(trip).first().single().dayId) // still mine on this phone
        assertEquals(null, pushed["plan_item"]) // and mine waits for the choice
    }

    @Test
    fun keepMineSendsMyMove() = runTest {
        val (trip, days) = twoMovesAtOnce()

        graph.plan.keepMine(graph.plan.observeConflicts(trip).first().single().objectiveId)
        graph.sync.syncNow()

        assertEquals(emptyList(), graph.plan.observeConflicts(trip).first())
        assertEquals(days[1], pushed.getValue("plan_item").single().str("day_id"))
    }

    @Test
    fun acceptTheirsTakesTheirMoveAndDropsMine() = runTest {
        val (trip, days) = twoMovesAtOnce()

        graph.plan.acceptTheirs(graph.plan.observeConflicts(trip).first().single().objectiveId)

        assertEquals(days[2], graph.plan.observePlan(trip).first().single().dayId)
        assertEquals(0, graph.sync.observe().first().pending)
    }

    @Test
    fun aReorderWithinTheSameDayIsNoConflict() = runTest {
        val trip = graph.trips.create("Font Easter", "Fontainebleau", LocalDate.parse("2026-04-17"), LocalDate.parse("2026-04-18"))
        graph.account.signIn("lea@example.com", "secret")
        val objective = graph.objectives.add(trip, "Carnage", ClimbStyle.Boulder, Grade(GradeScale.Font, "7B+"), null)
        val days = graph.plan.observeDays(trip).first().map { it.id }
        graph.plan.place(objective, days[0], 0)
        pull = """{"trips":["$trip"],"cursor":"2026-11-20T10:00:00+00:00"}"""
        graph.sync.syncNow()
        graph.plan.place(objective, days[1], 0)
        pull = """{"trips":["$trip"],"cursor":"2026-11-20T10:05:00+00:00",
            "plan_item":[{"objective_id":"$objective","trip_id":"$trip","day_id":"${days[0]}","order_key":"b","updated_at":1795000000000,
                "deleted_at":null,"updated_by":"$tom"}]}"""

        graph.sync.syncNow()

        assertEquals(emptyList(), graph.plan.observeConflicts(trip).first())
        assertEquals(days[1], pushed.getValue("plan_item").last().str("day_id")) // mine goes up and wins
    }

    private fun JsonObject.str(key: String) = getValue(key).toString().trim('"')
}
