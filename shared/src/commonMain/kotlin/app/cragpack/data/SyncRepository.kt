package app.cragpack.data

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToOne
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
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
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
    private val running = Mutex()
    private val pending = db.syncQueries.countPending().asFlow().mapToOne(io).map { it.toInt() }

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
    suspend fun syncNow() = running.withLock {
        val api = supabase ?: return@withLock
        val token = account.accessToken() ?: return@withLock
        withContext(io) { queueUploadOnce() }
        apply(api.rpc("sync_pull", buildJsonObject { put("cursors", JsonObject(withContext(io) { cursors() })) }, token))
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

    private suspend fun apply(pull: JsonObject) = withContext(io) {
        val waiting = db.syncQueries.selectOutbox().executeAsList().groupBy({ it.tbl to it.row_key }, { it.col })
        db.transaction {
            db.syncQueries.putState(APPLYING, "1") // the outbox triggers stay quiet: this came from the server
            for (table in syncTables) {
                pull[table.remote]?.jsonArray?.forEach { row ->
                    val values = row.jsonObject
                    val key = table.keys.joinToString("|") { values.getValue(it).jsonPrimitive.content }
                    if (table.name == "user" && key == me.value) return@forEach // my own name is mine to change
                    write(table, values, waiting[table.name to key].orEmpty())
                }
            }
            val cursor = pull.getValue("cursor").jsonPrimitive.content
            pull.getValue("trips").jsonArray.forEach { db.syncQueries.putState(it.jsonPrimitive.content, cursor) }
            db.syncQueries.putState(ME, cursor)
            db.syncQueries.deleteState(APPLYING)
        }
        driver.notifyListeners(*syncTables.map { it.name }.toTypedArray())
    }

    /** Adds the row if it's new, then sets every field that isn't waiting to be pushed from here. */
    private fun write(table: SyncTable, row: JsonObject, waiting: List<String>) {
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
            val batches = linkedMapOf<Set<String>, MutableList<JsonObject>>()
            withContext(io) {
                entries.groupBy { it.row_key }.forEach { (key, fields) ->
                    val row = read(table, key) ?: return@forEach // gone from this phone (signed out, re-keyed): nothing to send
                    val columns = if (fields.any { it.col == "*" }) table.columns.keys
                    else table.keys + fields.map { it.col }.filter { it in table.columns } + "updated_at"
                    batches.getOrPut(columns.toSet()) { mutableListOf() } += JsonObject(columns.associateWith { row.getValue(it) })
                }
            }
            for (rows in batches.values) rows.chunked(500).forEach { api.upsert(table.remote, table.keys, JsonArray(it), token) }
            // Only what was read: a field edited again meanwhile has a new id and waits for the next push.
            withContext(io) { db.syncQueries.deleteSent(entries.map { it.id }) }
        }
    }

    /** The row as JSON, or null when it's no longer on this phone (or isn't this phone's to send). */
    private fun read(table: SyncTable, key: String): Map<String, JsonElement>? {
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
