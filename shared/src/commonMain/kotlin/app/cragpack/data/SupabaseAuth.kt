package app.cragpack.data

import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.put

/** A signed-in Supabase session. [expiresAt] is in epoch seconds. */
internal data class Session(
    val userId: String,
    val email: String,
    val name: String,
    val accessToken: String,
    val refreshToken: String,
    val expiresAt: Long,
)

/** What Supabase said went wrong, in its own words ("Invalid login credentials"). */
class AuthException(message: String, val code: String?) : Exception(message)

/** Supabase Auth over plain HTTP: only the calls the app makes. */
internal class SupabaseAuth(private val http: HttpClient, private val url: String, private val key: String) {

    /** Null when the account must be confirmed from the email first (Supabase's "Confirm email" is on). */
    suspend fun signUp(email: String, password: String, name: String): Session? = call(
        "signup",
        buildJsonObject {
            put("email", email)
            put("password", password)
            put("data", buildJsonObject { put("name", name) })
        },
    ).takeIf { "access_token" in it }?.let(::toSession)

    suspend fun signIn(email: String, password: String): Session =
        toSession(call("token?grant_type=password", buildJsonObject { put("email", email); put("password", password) }))

    suspend fun refresh(refreshToken: String): Session =
        toSession(call("token?grant_type=refresh_token", buildJsonObject { put("refresh_token", refreshToken) }))

    suspend fun resendConfirmation(email: String) {
        call("resend", buildJsonObject { put("type", "signup"); put("email", email) })
    }

    /** Revokes the session on the server. */
    suspend fun signOut(accessToken: String) {
        call("logout", JsonObject(emptyMap()), accessToken)
    }

    private suspend fun call(path: String, body: JsonObject, bearer: String = key): JsonObject {
        val response = http.post("$url/auth/v1/$path") {
            header("apikey", key)
            header("Authorization", "Bearer $bearer")
            contentType(ContentType.Application.Json)
            setBody(body.toString())
        }
        val text = response.bodyAsText()
        val json = runCatching { Json.parseToJsonElement(text).jsonObject }.getOrDefault(JsonObject(emptyMap()))
        if (!response.status.isSuccess()) {
            // Newer Supabase errors use msg + error_code; token errors still use error_description + error.
            val message = json.string("msg") ?: json.string("error_description") ?: json.string("message") ?: "Something went wrong (${response.status.value})"
            throw AuthException(message, json.string("error_code") ?: json.string("error"))
        }
        return json
    }

    private fun toSession(json: JsonObject): Session {
        val user = json.getValue("user").jsonObject
        return Session(
            userId = user.string("id")!!,
            email = user.string("email").orEmpty(),
            name = user["user_metadata"]?.jsonObject?.string("name").orEmpty(),
            accessToken = json.string("access_token")!!,
            refreshToken = json.string("refresh_token")!!,
            expiresAt = json.getValue("expires_at").jsonPrimitive.long,
        )
    }

    private fun JsonObject.string(name: String) = (get(name) as? JsonPrimitive)?.takeIf { it.isString }?.content

    companion object {
        /** Null when this build has no Supabase keys in local.properties: the app stays on this phone. */
        fun fromConfig(): SupabaseAuth? =
            if (SupabaseConfig.URL.isBlank() || SupabaseConfig.KEY.isBlank()) null
            else SupabaseAuth(HttpClient(), SupabaseConfig.URL.trimEnd('/'), SupabaseConfig.KEY)
    }
}
