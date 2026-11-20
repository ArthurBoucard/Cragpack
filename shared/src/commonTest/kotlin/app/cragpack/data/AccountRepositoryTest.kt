package app.cragpack.data

import app.cragpack.domain.grade.Grade
import app.cragpack.domain.grade.GradeScale
import app.cragpack.domain.log.LogStyle
import app.cragpack.domain.objective.ClimbStyle
import app.cragpack.domain.settings.Theme
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
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.time.Clock

class AccountRepositoryTest {
    private val accountId = "6f1c1d1e-0000-4000-8000-000000000001"
    private val calls = mutableListOf<String>()
    private var signupConfirms = false

    private val engine = MockEngine { request ->
        val path = request.url.encodedPath.substringAfter("/auth/v1/") + request.url.encodedQuery.let { if (it.isEmpty()) "" else "?$it" }
        calls += path
        val session = """{"access_token":"access-$path","refresh_token":"refresh","expires_at":${Clock.System.now().epochSeconds + 3600},
            "user":{"id":"$accountId","email":"lea@example.com","user_metadata":{"name":"Léa"}}}"""
        val (status, body) = when (path) {
            "signup" -> HttpStatusCode.OK to if (signupConfirms) """{"id":"$accountId","email":"lea@example.com"}""" else session
            "token?grant_type=password", "token?grant_type=refresh_token" -> HttpStatusCode.OK to session
            "logout" -> HttpStatusCode.NoContent to ""
            else -> HttpStatusCode.BadRequest to """{"code":400,"error_code":"invalid_credentials","msg":"Invalid login credentials"}"""
        }
        respond(body, status, headersOf("Content-Type", "application/json"))
    }
    private val graph = AppGraph(testDriver(), Clock.System, SupabaseAuth(HttpClient(engine), "https://x.supabase.co", "key"))

    @Test
    fun signingInKeepsEverythingMadeOnThePhone() = runTest {
        val trip = graph.trips.create("Font Easter", "Fontainebleau", LocalDate.parse("2026-04-17"), LocalDate.parse("2026-04-21"))
        val objective = graph.objectives.add(trip, "La Joker", ClimbStyle.Boulder, Grade(GradeScale.Font, "7A"), null)
        graph.objectives.setStarred(objective, true)
        graph.logs.log(objective, LogStyle.Sent)

        graph.account.signIn("lea@example.com", "secret")

        assertEquals(accountId, graph.myId)
        assertEquals(Account("Léa", "lea@example.com"), graph.account.observe().first())
        assertEquals(accountId, graph.trips.observeTrips().first().single().crew.single().id)
        assertEquals(true, graph.objectives.observeWishlist(trip).first().single().starred)
        assertEquals(1, graph.logs.observeLogbook().first().size)
    }

    @Test
    fun signUpWaitsForTheEmailWhenConfirmationIsOn() = runTest {
        signupConfirms = true
        val localId = graph.myId

        assertFalse(graph.account.signUp("Léa", "lea@example.com", "secret"))

        assertEquals(localId, graph.myId)
        assertNull(graph.account.observe().first())
    }

    @Test
    fun signOutClearsThePhoneButKeepsPreferences() = runTest {
        graph.settings.setTheme(Theme.Dark)
        graph.account.signUp("Léa", "lea@example.com", "secret")
        graph.trips.create("Font Easter", "Fontainebleau", LocalDate.parse("2026-04-17"), LocalDate.parse("2026-04-21"))

        graph.account.signOut()

        assertNotEquals(accountId, graph.myId)
        assertNull(graph.account.observe().first())
        assertEquals(emptyList(), graph.trips.observeTrips().first())
        assertNull(graph.account.accessToken())
        assertEquals(Theme.Dark, graph.settings.current().theme)
        assertEquals("logout", calls.last())
    }

    @Test
    fun supabaseErrorsComeBackInItsWords() = runTest {
        val error = assertFailsWith<AuthException> { graph.account.resendConfirmation("lea@example.com") }
        assertEquals("Invalid login credentials", error.message)
        assertEquals("invalid_credentials", error.code)
    }
}
