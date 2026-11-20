package app.cragpack.data

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Clock

class CrewRepositoryTest {
    private val lea = "00000000-0000-4000-8000-00000000000a"
    private val trip = "00000000-0000-4000-8000-0000000000f1"
    private val calls = mutableListOf<String>()
    private var pullsFail = false

    private val engine = MockEngine { request ->
        val name = request.url.encodedPath.substringAfterLast('/')
        calls += if (name.startsWith("sync")) name else "$name ${(request.body as? TextContent)?.text}"
        when {
            name == "token" -> respond(
                """{"access_token":"a","refresh_token":"r","expires_at":${Clock.System.now().epochSeconds + 3600},
                "user":{"id":"$lea","email":"lea@example.com","user_metadata":{"name":"Léa"}}}""",
                HttpStatusCode.OK, headersOf("Content-Type", "application/json"),
            )
            name == "sync_pull" && pullsFail && calls.any { it.startsWith("leave_trip") } ->
                respond("", HttpStatusCode.ServiceUnavailable)
            name == "sync_pull" -> respond("""{"trips":[],"cursor":"2026-11-20T10:00:00+00:00"}""", HttpStatusCode.OK, headersOf("Content-Type", "application/json"))
            else -> respond("", HttpStatusCode.NoContent)
        }
    }
    private val graph = AppGraph(testDriver(), Clock.System, Supabase(HttpClient(engine), "https://x.supabase.co", "key"))

    @Test
    fun eachChangeIsTheServersAndASyncBringsItBack() = runTest {
        graph.account.signIn("lea@example.com", "secret")
        calls.clear()

        graph.crew.remove(trip, "tom")

        // (the first sync also uploads my profile: not what this test is about)
        assertEquals(listOf("sync_pull", """remove_climber {"trip":"$trip","climber":"tom"}""", "sync_pull"), calls.filter { !it.startsWith("profile") })
    }

    @Test
    fun leavingCountsEvenIfTheSyncAfterItFails() = runTest {
        graph.account.signIn("lea@example.com", "secret")
        pullsFail = true

        graph.crew.leave(trip) // doesn't throw: the server already has it

        assertEquals("""leave_trip {"trip":"$trip"}""", calls.single { it.startsWith("leave_trip") })
    }
}
