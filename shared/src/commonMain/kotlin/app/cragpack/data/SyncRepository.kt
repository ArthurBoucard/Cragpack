package app.cragpack.data

import app.cash.sqldelight.Query
import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlCursor
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.db.SqlPreparedStatement
import app.cragpack.data.db.CragpackDb
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long

/** What the status pill shows (tech spec §3): no account, or how many rows still wait to reach Supabase. */
data class SyncState(val signedIn: Boolean, val pending: Int)

/**
 * Local-first sync with Supabase (tech spec §3). Every write lands in SQLite first and its triggers queue it in
 * the outbox (SyncTables.kt). While signed in, a sync pulls what changed on my trips since each trip's cursor,
 * then pushes the outbox field by field, so the last write to reach the server wins per field.
 * A pulled row never overwrites a field still waiting in the outbox: that edit is newer and goes up next.
 */
class SyncRepository internal constructor(
    private val db: CragpackDb,
    private val driver: SqlDriver,
    private val supabase: Supabase?,
    private val account: AccountRepository,
    private val me: StateFlow<String>,
) {
    private val io = Dispatchers.IO
    /**
     * The outbox is filled by triggers SQLDelight doesn't know about, so it never hears of those writes:
     * count again whenever any synced table (or the outbox itself) changes.
     */
    private val pending: Flow<Int> = callbackFlow {
        val listener = Query.Listener { trySend(Unit) }
        val tables = syncTables.map { it.name }.toTypedArray() + "outbox"
        driver.addListener(*tables, listener = listener)
        send(Unit)
        awaitClose { driver.removeListener(*tables, listener = listener) }
    }.conflate().map { db.syncQueries.countPending().executeAsOne().toInt() }.flowOn(io).distinctUntilChanged()

    fun observe(): Flow<SyncState> = combine(account.observe().map { it != null }, pending) { signedIn, n -> SyncState(signedIn, n) }

    /** While signed in: a sync soon after each edit, and at least once a minute to fetch the crew's changes. */
    fun start(scope: CoroutineScope) = scope.launch {
        account.observe().map { it != null }.distinctUntilChanged().collectLatest { signedIn ->
            while (signedIn) {
                val ok = try {
                    syncNow()
                    true
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    false // offline, or the server said no: try again later; the edits stay queued
                }
                if (ok) withTimeoutOrNull(60_000) { pending.first { it > 0 } } else delay(15_000)
                delay(2_000) // let a burst of edits gather into one push
            }
        }
    }

    /** One pull and push. Throws when offline or when Supabase refuses; nothing is lost, the outbox keeps it. */
    suspend fun syncNow() = account.session.withLock {
        val api = supabase ?: return@withLock
        val token = account.accessToken() ?: return@withLock
        withContext(io) { queueUploadOnce() }
        applyPull(api.rpc("sync_pull", buildJsonObject { put("cursors", JsonObject(withContext(io) { cursors() })) }, token).jsonObject)
        push(api, token)
    }

    /** The first sync after signing in uploads everything that was made on this phone (D4). */
    private fun queueUploadOnce() {
        if (db.syncQueries.selectState(UPLOADED).executeAsOneOrNull() == me.value) return
        db.transaction {
            queueEverything().forEach { driver.execute(null, it, 0) }
            db.syncQueries.putState(UPLOADED, me.value)
        }
    }

    private fun cursors(): Map<String, JsonElement> =
        db.syncQueries.selectCursors().executeAsList().associate { it.name to JsonPrimitive(it.data_) }

    // ── Pull ───────────────────────────────────────────────────────────────────────

    private suspend fun applyPull(pull: JsonObject) = withContext(io) {
        db.transaction {
            // Read inside the transaction, so an edit made meanwhile can't be overwritten by this pull.
            val waiting = db.syncQueries.selectOutbox().executeAsList().groupBy({ it.tbl to it.row_key }, { it.col })
            db.syncQueries.putState(APPLYING, "1") // the outbox triggers stay quiet: this came from the server
            for (table in syncTables) {
                pull[table.remote]?.jsonArray?.forEach { row ->
                    val values = row.jsonObject
                    val key = table.keys.joinToString("|") { values.getValue(it).jsonPrimitive.content }
                    if (table.name == "user" && key == me.value) return@forEach // my own name is mine to change
                    writeRow(table, values, waiting[table.name to key].orEmpty())
                }
            }
            val cursor = pull.getValue("cursor").jsonPrimitive.content
            val mine = pull.getValue("trips").jsonArray.map { it.jsonPrimitive.content }.toSet()
            // A trip pulled before (it has a cursor) that the server no longer lists: I left or was removed.
            // Trips without a cursor were made here and haven't gone up yet: they stay.
            db.syncQueries.selectCursors().executeAsList().map { it.name }.filter { it != ME && it !in mine }
                .forEach { db.tripQueries.forgetTrip(tripId = it, me = me.value) }
            mine.forEach { db.syncQueries.putState(it, cursor) }
            db.syncQueries.putState(ME, cursor)
            db.syncQueries.deleteState(APPLYING)
        }
        driver.notifyListeners(*syncTables.map { it.name }.toTypedArray())
    }

    /** Adds the row if it's new, then sets every field that isn't waiting to be pushed from here. */
    private fun writeRow(table: SyncTable, row: JsonObject, waiting: List<String>) {
        if ("*" in waiting) return // made here and not sent yet: this phone's copy goes up next
        val columns = table.columns.keys.filter { it in row }
        driver.execute(
            null,
            "INSERT OR IGNORE INTO ${table.name} (${columns.joinToString()}) VALUES (${columns.joinToString { "?" }})",
            columns.size,
        ) { columns.forEachIndexed { i, c -> bind(i, table.columns.getValue(c), row.getValue(c)) } }
        val fields = columns.filter { it !in table.keys && it !in waiting }
        if (fields.isEmpty()) return
        driver.execute(
            null,
            "UPDATE ${table.name} SET ${fields.joinToString { "$it = ?" }} WHERE ${table.keys.joinToString(" AND ") { "$it = ?" }}",
            fields.size + table.keys.size,
        ) {
            (fields + table.keys).forEachIndexed { i, c -> bind(i, table.columns.getValue(c), row.getValue(c)) }
        }
    }

    private fun SqlPreparedStatement.bind(index: Int, type: ColType, value: JsonElement) {
        val v = value as? JsonPrimitive
        when {
            v == null || v is JsonNull -> bindString(index, null)
            type == ColType.Text -> bindString(index, v.content)
            type == ColType.Int -> bindLong(index, v.long)
            else -> bindDouble(index, v.double)
        }
    }

    // ── Push ───────────────────────────────────────────────────────────────────────

    private suspend fun push(api: Supabase, token: String) {
        val byTable = withContext(io) { db.syncQueries.selectOutbox().executeAsList() }.groupBy { it.tbl }
        for (table in syncTables) {
            val entries = byTable[table.name] ?: continue
            val newRows = mutableListOf<JsonObject>()
            val edits = mutableListOf<Pair<Map<String, String>, JsonObject>>()
            withContext(io) {
                entries.groupBy { it.row_key }.forEach { (key, fields) ->
                    val row = readRow(table, key) ?: return@forEach // gone from this phone (signed out, re-keyed): nothing to send
                    if (fields.any { it.col == "*" }) newRows += JsonObject(row)
                    else edits += table.keys.associateWith { row.getValue(it).jsonPrimitive.content } to
                        JsonObject((fields.map { it.col }.filter { it in table.columns && it !in table.keys } + "updated_at").associateWith { row.getValue(it) })
                }
            }
            newRows.chunked(500).forEach { batch ->
                // One refused row mustn't hold back the rest: on a refusal, send the batch row by row.
                unlessRefused { api.upsert(table.remote, table.keys, JsonArray(batch), token) }
                    ?: batch.forEach { unlessRefused { api.upsert(table.remote, table.keys, JsonArray(listOf(it)), token) } }
            }
            // ponytail: one request per edited row; a push function on the server if this gets slow.
            edits.forEach { (keys, fields) -> unlessRefused { api.update(table.remote, keys, fields, token) } }
            // Only what was read: a field edited again meanwhile has a new id and waits for the next push.
            withContext(io) { db.syncQueries.deleteSent(entries.map { it.id }) }
        }
    }

    /**
     * Runs [send]; null if Supabase refused it. A refused change is dropped (ponytail: silently; e.g. an edit to
     * a trip you were just removed from) so it can't block the outbox. Offline and server errors still throw.
     */
    private inline fun unlessRefused(send: () -> Unit): Unit? = try {
        send()
    } catch (e: SupabaseException) {
        if (!e.refused) throw e
        null
    }

    /** The row as JSON, or null when it's no longer on this phone (or isn't this phone's to send). */
    private fun readRow(table: SyncTable, key: String): Map<String, JsonElement>? {
        val columns = table.columns.entries.toList()
        val mine = table.mine?.let { " AND $it" }.orEmpty()
        return driver.executeQuery(
            null,
            "SELECT ${columns.joinToString { it.key }} FROM ${table.name} WHERE ${table.keyExpr()} = ?$mine",
            { cursor: SqlCursor ->
                QueryResult.Value(
                    if (!cursor.next().value) null
                    else columns.withIndex().associate { (i, c) ->
                        c.key to when (c.value) {
                            ColType.Text -> JsonPrimitive(cursor.getString(i))
                            ColType.Int -> JsonPrimitive(cursor.getLong(i))
                            ColType.Real -> JsonPrimitive(cursor.getDouble(i))
                        }
                    },
                )
            },
            1,
        ) { bindString(0, key) }.value
    }

    private companion object {
        const val UPLOADED = "uploaded"
        const val APPLYING = "applying"
        const val ME = "me"
    }
}
