package app.cragpack.data

import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
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
class SupabaseException(message: String, val code: String?) : Exception(message)

/** Supabase over plain HTTP (Auth and the REST API): only the calls the app makes. */
internal class Supabase(private val http: HttpClient, private val url: String, private val key: String) {

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

    /** Calls a Postgres function (from the supabase folder) as the signed-in user. */
    suspend fun rpc(name: String, args: JsonObject, accessToken: String): JsonObject =
        post("rest/v1/rpc/$name", args.toString(), accessToken)

    /**
     * Inserts [rows], or updates the columns they carry where a row with the same [keys] exists.
     * Every row must carry the same columns (PostgREST's rule for a batch).
     */
    suspend fun upsert(table: String, keys: List<String>, rows: JsonArray, accessToken: String) {
        post("rest/v1/$table?on_conflict=${keys.joinToString(",")}", rows.toString(), accessToken, "resolution=merge-duplicates,return=minimal")
    }

    private suspend fun call(path: String, body: JsonObject, bearer: String = key): JsonObject = post("auth/v1/$path", body.toString(), bearer)

    private suspend fun post(path: String, body: String, bearer: String, prefer: String? = null): JsonObject {
        val response = http.post("$url/$path") {
            header("apikey", key)
            header("Authorization", "Bearer $bearer")
            prefer?.let { header("Prefer", it) }
            contentType(ContentType.Application.Json)
            setBody(body)
        }
        val text = response.bodyAsText()
        val json = runCatching { Json.parseToJsonElement(text).jsonObject }.getOrDefault(JsonObject(emptyMap()))
        if (!response.status.isSuccess()) {
            // Auth errors use msg + error_code (token errors: error_description + error); the REST API, message + code.
            val message = json.string("msg") ?: json.string("error_description") ?: json.string("message") ?: "Something went wrong (${response.status.value})"
            throw SupabaseException(message, json.string("error_code") ?: json.string("error") ?: json.string("code"))
        }
        return json
    }

    private fun toSession(json: JsonObject): Session = runCatching {
        val user = json.getValue("user").jsonObject
        Session(
            userId = user.string("id")!!,
            email = user.string("email").orEmpty(),
            name = user["user_metadata"]?.jsonObject?.string("name").orEmpty(),
            accessToken = json.string("access_token")!!,
            refreshToken = json.string("refresh_token")!!,
            expiresAt = json.getValue("expires_at").jsonPrimitive.long,
        )
    }.getOrElse { throw SupabaseException("Supabase sent an answer the app doesn't understand", null) }

    private fun JsonObject.string(name: String) = (get(name) as? JsonPrimitive)?.takeIf { it.isString }?.content

    companion object {
        /** Null when this build has no Supabase keys in local.properties: the app stays on this phone. */
        fun fromConfig(): Supabase? =
            if (SupabaseConfig.URL.isBlank() || SupabaseConfig.KEY.isBlank()) null
            else Supabase(HttpClient { install(HttpTimeout) { requestTimeoutMillis = 20_000 } }, SupabaseConfig.URL.trimEnd('/'), SupabaseConfig.KEY)
    }
}
