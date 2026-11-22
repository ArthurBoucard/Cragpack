package app.cragpack.data

import app.cragpack.domain.invite.InviteExpiredException
import app.cragpack.domain.invite.InvitePreview
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
            inviter = p.getValue("inviter").jsonObject.let { it.getValue("name").jsonPrimitive.content to it.getValue("color_index").jsonPrimitive.int },
            crew = p.getValue("crew").jsonArray.map { (it as JsonObject).let { m -> m.getValue("name").jsonPrimitive.content to m.getValue("color_index").jsonPrimitive.int } },
        )
    }

    /**
     * Joins, then tries to bring the whole trip onto this phone. Returns its id. If that first pull fails
     * (the connection dropped just after joining), you're still in: the trip arrives with a later sync.
     */
    suspend fun join(token: String): String {
        val tripId = try {
            api().rpc("join_trip", buildJsonObject { put("token", token) }, signedIn()).jsonPrimitive.content
        } catch (e: SupabaseException) {
            throw if (e.code == "invite_expired") InviteExpiredException() else e
        }
        runCatching { sync.syncNow() }.onFailure { if (it is kotlinx.coroutines.CancellationException) throw it }
        return tripId
    }

    private suspend fun token(function: String, tripId: String): String {
        sync.syncNow() // the trip has to be on the server before it can be shared
        return inviteLink(api().rpc(function, buildJsonObject { put("trip", tripId) }, signedIn()).jsonPrimitive.content)
    }

    private suspend fun signedIn() = account.accessToken() ?: throw SupabaseException("Sign in first", null)

    private fun api() = supabase ?: throw SupabaseException("Accounts aren't set up in this build", null)
}
