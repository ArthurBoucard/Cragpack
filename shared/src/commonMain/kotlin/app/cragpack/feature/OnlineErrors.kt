package app.cragpack.feature

import app.cragpack.data.SupabaseException

/**
 * What to tell someone when an online action (invite, crew, account) fails: Supabase's own words when it said no
 * ("Only organisers can do that"), otherwise that the connection is the likely problem.
 */
fun explainFailure(e: Throwable, action: String): String =
    (e as? SupabaseException)?.takeIf { it.refused }?.message ?: "Can't $action right now. Check your connection and try again."
