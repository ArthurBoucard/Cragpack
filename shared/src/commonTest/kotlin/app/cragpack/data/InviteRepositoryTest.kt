package app.cragpack.data

import app.cragpack.domain.invite.InviteExpiredException
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.time.Clock

class InviteRepositoryTest {
    private val lea = "00000000-0000-4000-8000-00000000000a"
    private val trip = "00000000-0000-4000-8000-0000000000f1"
    private val token = "7a2f00aa11bb22cc"
    private val calls = mutableListOf<String>()
    private var expired = false

    private val engine = MockEngine { request ->
        val path = request.url.encodedPath.substringAfterLast('/')
        calls += path
        val body = when (path) {
            "token" -> """{"access_token":"a","refresh_token":"r","expires_at":${Clock.System.now().epochSeconds + 3600},
                "user":{"id":"$lea","email":"lea@example.com","user_metadata":{"name":"Léa"}}}"""
            "invite_token" -> "\"$token\""
            "invite_preview" -> if (expired) """{"expired":true}""" else """{"expired":false,"trip_id":"$trip","name":"Font Easter",
                "crag":"Fontainebleau","start_date":"2026-04-17","end_date":"2026-04-21","inviter":{"name":"Léa","color_index":1},
                "crew":[{"name":"Léa","color_index":1},{"name":"Tom","color_index":2}]}"""
            "join_trip" -> if (expired) return@MockEngine respond(
                """{"code":"P0001","message":"This link has expired","hint":"invite_expired"}""", HttpStatusCode.BadRequest,
                headersOf("Content-Type", "application/json"),
            ) else "\"$trip\""
            // The joined trip arrives with the pull that follows.
            "sync_pull" -> if ("join_trip" in calls) """{"trips":["$trip"],"cursor":"2026-11-20T10:00:00+00:00",
                "trip":[{"id":"$trip","name":"Font Easter","crag":"Fontainebleau","start_date":"2026-04-17","end_date":"2026-04-21",
                "cover_media_id":null,"owner_id":"$lea","created_at":1,"updated_at":1,"deleted_at":null}]}"""
            else """{"trips":[],"cursor":"2026-11-20T10:00:00+00:00"}"""
            else -> ""
        }
        respond(body, HttpStatusCode.OK, headersOf("Content-Type", "application/json"))
    }
    private val graph = AppGraph(testDriver(), Clock.System, Supabase(HttpClient(engine), "https://x.supabase.co", "key"))

    @Test
    fun theLinkIsTheServersTokenOnTheInviteHost() = runTest {
        graph.account.signIn("lea@example.com", "secret")
        assertEquals("https://cragpack.app/j/$token", graph.invites.link(trip))
    }

    @Test
    fun aPreviewShowsTheTripWithoutAnAccount() = runTest {
        val preview = graph.invites.preview(token)
        assertEquals("Font Easter", preview.name)
        assertEquals(LocalDate.parse("2026-04-21"), preview.end)
        assertEquals(listOf("Léa" to 1, "Tom" to 2), preview.crew)
    }

    @Test
    fun joiningBringsTheTripOntoThePhone() = runTest {
        graph.account.signIn("lea@example.com", "secret")
        assertEquals(trip, graph.invites.join(token))
        assertEquals("Font Easter", graph.trips.observeTrips().first().single().trip.name)
    }

    @Test
    fun aResetLinkIsExpired() = runTest {
        expired = true
        graph.account.signIn("lea@example.com", "secret")
        assertFailsWith<InviteExpiredException> { graph.invites.preview(token) }
        assertFailsWith<InviteExpiredException> { graph.invites.join(token) }
    }
}
