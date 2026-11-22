package app.cragpack.data

import app.cragpack.domain.grade.Grade
import app.cragpack.domain.grade.GradeScale
import app.cragpack.domain.log.LogStyle
import app.cragpack.domain.objective.BetaType
import app.cragpack.domain.objective.ClimbStyle
import kotlinx.datetime.LocalDate
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

internal enum class ColType { Text, Int, Real }

/**
 * A table that syncs with Supabase. The server table ([remote]) has the same columns (supabase/001_sync.sql),
 * plus trip_id and server_updated_at, which only the server fills. [mine] limits which local rows are pushed.
 * [readable]: a pulled row this app can show. Anything else (a newer app's grade, a bad write) is skipped, so one
 * row can't crash every phone in the crew.
 */
internal class SyncTable(
    val name: String,
    val keys: List<String>,
    val columns: Map<String, ColType>,
    val remote: String = name,
    val mine: String? = null,
    val readable: (JsonObject) -> Boolean = { true },
) {
    /** The outbox row_key: the key columns joined with '|', prefixed with NEW./OLD. in triggers. */
    fun keyExpr(prefix: String = "") = keys.joinToString(" || '|' || ") { "$prefix$it" }
}

private fun cols(vararg pairs: Pair<String, ColType>) = linkedMapOf(*pairs)
private val text = ColType.Text
private val int = ColType.Int
private val real = ColType.Real
private fun JsonObject.text(key: String) = (get(key) as? JsonPrimitive)?.takeIf { it !is JsonNull }?.content
private fun JsonObject.isDate(key: String) = text(key)?.let { runCatching { LocalDate.parse(it) }.isSuccess } == true
private fun JsonObject.isGrade(scale: String, value: String): Boolean {
    val s = GradeScale.entries.firstOrNull { it.name == text(scale) } ?: return false
    return text(value)?.let { Grade.of(s, it) } != null
}
private inline fun <reified E : Enum<E>> JsonObject.isOneOf(key: String) = enumValues<E>().any { it.name.equals(text(key), ignoreCase = true) }

private fun stamps(created: Boolean = true): Array<Pair<String, ColType>> =
    (if (created) arrayOf("created_at" to int) else emptyArray()) + arrayOf("updated_at" to int, "deleted_at" to int)

/**
 * Everything that syncs, parents before children: a push sends them in this order, so a row's parent
 * is always on the server first (the server's foreign keys and access rules need it).
 * ponytail: media rows and files sync in Phase 3.
 */
internal val syncTables = listOf(
    SyncTable("user", listOf("id"), cols("id" to text, "name" to text, "updated_at" to int), remote = "profile", mine = "is_me = 1"),
    SyncTable(
        "trip", listOf("id"),
        cols("id" to text, "name" to text, "crag" to text, "start_date" to text, "end_date" to text, "cover_media_id" to text, "owner_id" to text, *stamps()),
        readable = { it.isDate("start_date") && it.isDate("end_date") },
    ),
    SyncTable(
        "trip_member", listOf("trip_id", "user_id"),
        cols("trip_id" to text, "user_id" to text, "role" to text, "color_index" to int, "joined_at" to int, *stamps(created = false)),
    ),
    SyncTable("sector", listOf("id"), cols("id" to text, "trip_id" to text, "name" to text, "approach_note" to text, *stamps())),
    SyncTable(
        "day", listOf("id"),
        cols("id" to text, "trip_id" to text, "date" to text, "sector_id" to text, "is_rest" to int, "notes" to text, *stamps()),
        readable = { it.isDate("date") },
    ),
    SyncTable(
        "objective", listOf("id"),
        cols(
            "id" to text, "trip_id" to text, "name" to text, "style" to text, "grade_scale" to text, "grade_value" to text,
            "sector_id" to text, "lat" to real, "lng" to real, "created_by" to text, *stamps(),
        ),
        readable = { it.isOneOf<ClimbStyle>("style") && it.isGrade("grade_scale", "grade_value") },
    ),
    SyncTable("interest", listOf("objective_id", "user_id"), cols("objective_id" to text, "user_id" to text, *stamps(created = false))),
    SyncTable(
        "plan_item", listOf("objective_id"),
        // updated_by: the server overwrites it with whoever pushed (supabase/004_conflicts.sql).
        cols("objective_id" to text, "day_id" to text, "order_key" to text, *stamps(created = false), "updated_by" to text),
    ),
    SyncTable(
        "beta_item", listOf("id"),
        cols(
            "id" to text, "objective_id" to text, "type" to text, "media_id" to text, "url" to text, "text" to text,
            "duration_sec" to int, "created_by" to text, *stamps(),
        ),
        readable = { it.isOneOf<BetaType>("type") },
    ),
    SyncTable(
        "gear_item", listOf("id"),
        cols("id" to text, "trip_id" to text, "name" to text, "quantity_needed" to int, "day_id" to text, "created_by" to text, *stamps()),
    ),
    SyncTable("gear_claim", listOf("gear_item_id", "user_id"), cols("gear_item_id" to text, "user_id" to text, "quantity" to int, *stamps(created = false))),
    SyncTable(
        "log_entry", listOf("id"),
        cols(
            "id" to text, "user_id" to text, "style" to text, "attempts" to int, "date" to text, "trip_id" to text, "objective_id" to text,
            "snapshot_name" to text, "snapshot_grade_scale" to text, "snapshot_grade_value" to text, "snapshot_crag" to text, *stamps(),
        ),
        readable = { row ->
            LogStyle.entries.any { it.key == row.text("style") } && row.isDate("date") && row.isGrade("snapshot_grade_scale", "snapshot_grade_value")
        },
    ),
)

/** Quiet while a pull is being written: those changes came from the server. */
private const val NOT_APPLYING = "NOT EXISTS (SELECT 1 FROM sync_state WHERE name = 'applying')"

/**
 * The triggers that fill the outbox: a new row queues '*', an update queues each field that changed
 * (so two people editing different fields of one row both win, tech spec §3). Rebuilt on every start,
 * so they always match [syncTables].
 */
internal fun outboxTriggers(): List<String> = syncTables.flatMap { t ->
    val mine = t.mine?.let { " AND NEW.$it" }.orEmpty()
    val updates = t.columns.keys.joinToString("\n") { c ->
        "INSERT OR REPLACE INTO outbox (tbl, row_key, col) SELECT '${t.name}', ${t.keyExpr("NEW.")}, '$c' WHERE NEW.$c IS NOT OLD.$c;"
    }
    listOf(
        "DROP TRIGGER IF EXISTS outbox_${t.name}_insert",
        """CREATE TRIGGER outbox_${t.name}_insert AFTER INSERT ON ${t.name} WHEN $NOT_APPLYING$mine BEGIN
          |INSERT OR REPLACE INTO outbox (tbl, row_key, col) VALUES ('${t.name}', ${t.keyExpr("NEW.")}, '*');
          |END""".trimMargin(),
        "DROP TRIGGER IF EXISTS outbox_${t.name}_update",
        "CREATE TRIGGER outbox_${t.name}_update AFTER UPDATE ON ${t.name} WHEN $NOT_APPLYING$mine BEGIN\n$updates\nEND",
    )
}

/**
 * Before my first unsynced move of an objective, remember the day it was on (plan_base, Sync.sq): a conflict is
 * two moves away from that same day (tech spec §3). BEFORE, because INSERT OR REPLACE deletes the old row.
 */
internal fun planBaseTriggers(): List<String> = listOf(
    "DROP TRIGGER IF EXISTS plan_base_on_place",
    """CREATE TRIGGER plan_base_on_place BEFORE INSERT ON plan_item WHEN $NOT_APPLYING BEGIN
      |INSERT OR IGNORE INTO plan_base (objective_id, day_id)
      |SELECT NEW.objective_id, (SELECT day_id FROM plan_item WHERE objective_id = NEW.objective_id AND deleted_at IS NULL);
      |END""".trimMargin(),
    "DROP TRIGGER IF EXISTS plan_base_on_change",
    """CREATE TRIGGER plan_base_on_change BEFORE UPDATE OF day_id, deleted_at ON plan_item WHEN $NOT_APPLYING BEGIN
      |INSERT OR IGNORE INTO plan_base (objective_id, day_id) SELECT OLD.objective_id, CASE WHEN OLD.deleted_at IS NULL THEN OLD.day_id END;
      |END""".trimMargin(),
)

/** Signing up uploads what's on the phone (D4): every row is queued whole. */
internal fun queueEverything(): List<String> = syncTables.map { t ->
    "INSERT OR REPLACE INTO outbox (tbl, row_key, col) SELECT '${t.name}', ${t.keyExpr()}, '*' FROM ${t.name}" +
        (t.mine?.let { " WHERE $it" }.orEmpty())
}
