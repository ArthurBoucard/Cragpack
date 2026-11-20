package app.cragpack.data

import app.cragpack.domain.invite.inviteLink
import kotlinx.datetime.LocalDate
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/** What Join (10) shows about a trip before joining it. */
data class InvitePreview(
    val tripId: String,
    val name: String,
    val crag: String,
    val start: LocalDate,
    val end: LocalDate,
    val inviter: String,
    /** Name and crew colour, in join order. */
    val crew: List<Pair<String, Int>>,
)

/** The link is dead: reset by an organiser, or the trip was deleted (Invite expired, 17). */
class InviteExpiredException : Exception("This link has expired")

/**
 * Invite links (tech spec §5), through the functions in supabase/002_invites.sql. Online only:
 * a link has to come from the server to work for others.
 */
class InviteRepository internal constructor(
    private val supabase: Supabase?,
    private val account: AccountRepository,
    private val sync: SyncRepository,
) {
    /** The trip's link, made the first time someone asks. Signed in only. */
    suspend fun link(tripId: String): String = token("invite_token", tripId)

    /** Organisers only: the old link stops working (Trip settings → Reset). */
    suspend fun reset(tripId: String): String = token("reset_invite", tripId)

    /** Works without an account: Join shows the trip before asking for one. */
    suspend fun preview(token: String): InvitePreview {
        val json = api().rpc("invite_preview", buildJsonObject { put("token", token) }, account.accessToken())
        if (json is JsonNull || json.jsonObject.getValue("expired").jsonPrimitive.boolean) throw InviteExpiredException()
        val p = json.jsonObject
        fun text(key: String) = p.getValue(key).jsonPrimitive.content
        return InvitePreview(
            tripId = text("trip_id"), name = text("name"), crag = text("crag"),
            start = LocalDate.parse(text("start_date")), end = LocalDate.parse(text("end_date")),
            inviter = text("inviter"),
            crew = p.getValue("crew").jsonArray.map { (it as JsonObject).let { m -> m.getValue("name").jsonPrimitive.content to m.getValue("color_index").jsonPrimitive.int } },
        )
    }

    /** Joins and brings the whole trip onto this phone. Returns its id. */
    suspend fun join(token: String): String {
        val tripId = try {
            api().rpc("join_trip", buildJsonObject { put("token", token) }, signedIn()).jsonPrimitive.content
        } catch (e: SupabaseException) {
            throw if (e.code == "invite_expired") InviteExpiredException() else e
        }
        sync.syncNow()
        return tripId
    }

    private suspend fun token(function: String, tripId: String): String {
        sync.syncNow() // the trip has to be on the server before it can be shared
        return inviteLink(api().rpc(function, buildJsonObject { put("trip", tripId) }, signedIn()).jsonPrimitive.content)
    }

    private suspend fun signedIn() = account.accessToken() ?: throw SupabaseException("Sign in first", null)

    private fun api() = supabase ?: throw SupabaseException("Accounts aren't set up in this build", null)
}
