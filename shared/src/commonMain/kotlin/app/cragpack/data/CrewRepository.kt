package app.cragpack.data

import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Crew management (07, 29, 31) through supabase/003_crew.sql, which holds the rules: organisers make
 * organisers and remove climbers, anyone can leave, a trip always keeps an organiser. Online only, like invites.
 */
class CrewRepository internal constructor(
    private val supabase: Supabase?,
    private val account: AccountRepository,
    private val sync: SyncRepository,
) {
    suspend fun makeOrganiser(tripId: String, userId: String) = call("make_organiser", tripId, userId)

    suspend fun remove(tripId: String, userId: String) = call("remove_climber", tripId, userId)

    /** The trip leaves this phone with the sync that follows; your sends stay in your logbook (D2). */
    suspend fun leave(tripId: String) = call("leave_trip", tripId, null)

    private suspend fun call(function: String, tripId: String, userId: String?) {
        val api = supabase ?: throw SupabaseException("Accounts aren't set up in this build", null)
        val token = account.accessToken() ?: throw SupabaseException("Sign in first", null)
        sync.syncNow() // my own pending edits to the trip go up first
        api.rpc(function, buildJsonObject { put("trip", tripId); userId?.let { put("climber", it) } }, token)
        sync.syncNow()
    }
}
