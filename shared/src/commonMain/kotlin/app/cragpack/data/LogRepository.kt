package app.cragpack.data

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.cragpack.data.db.CragpackDb
import app.cragpack.domain.grade.Grade
import app.cragpack.domain.grade.GradeScale
import app.cragpack.domain.log.LogStyle
import app.cragpack.domain.trip.Person
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.LocalDate

/** One climber's entry on an objective. */
data class ObjectiveLog(val person: Person, val style: LogStyle)

data class TripLogEntry(
    val person: Person,
    val style: LogStyle,
    val date: LocalDate,
    val objectiveId: String?,
    val name: String,
    val grade: Grade,
    val sector: String?,
)

data class LogbookEntry(
    val id: String,
    val style: LogStyle,
    val date: LocalDate,
    val name: String,
    val grade: Grade,
    /** The trip's name, or null for "Outside a trip" (or a deleted trip: then [crag] is shown). */
    val trip: String?,
    val crag: String,
    /** Set while the objective still exists, so the entry can open it. */
    val objectiveId: String?,
)

/** What a log action changed, so the toast's Undo can put it back. */
data class LogUndo(val added: String?, val removed: List<String>)

class LogRepository internal constructor(
    private val db: CragpackDb,
    private val myId: String,
    private val now: () -> Long,
    private val today: () -> LocalDate,
) {
    private val io = Dispatchers.IO
    private val q get() = db.logEntryQueries

    /**
     * Logs your [style] on an objective, dated today. One send per climber per objective:
     * Sent or Flash replaces your earlier send, and tapping the send you already have removes it.
     * Each Attempt adds one attempt.
     */
    fun log(objectiveId: String, style: LogStyle): LogUndo = db.transactionWithResult {
        val mine = q.selectMySends(objectiveId, myId).executeAsList()
        if (style != LogStyle.Attempt && mine.any { it.style == style.key }) {
            val ids = mine.map { it.id }
            q.setDeleted(now(), now(), ids)
            return@transactionWithResult LogUndo(null, ids)
        }
        val removed = if (style == LogStyle.Attempt) emptyList() else mine.map { it.id }
        if (removed.isNotEmpty()) q.setDeleted(now(), now(), removed)
        val s = q.selectSnapshot(objectiveId).executeAsOne()
        val id = newId()
        val t = now()
        q.insertLog(id, myId, style.key, today().toString(), s.trip_id, objectiveId, s.name, s.grade_scale, s.grade_value, s.crag, t, t)
        LogUndo(id, removed)
    }

    /** A climb outside any trip ("Log a send" in My logbook). */
    fun logOutside(name: String, grade: Grade, crag: String, date: LocalDate, style: LogStyle) {
        require(name.isNotBlank()) { "A climb needs a name" }
        val t = now()
        q.insertLog(newId(), myId, style.key, date.toString(), null, null, name.trim(), grade.scale.name, grade.value, crag.trim(), t, t)
    }

    fun undo(u: LogUndo) = db.transaction {
        u.added?.let { q.setDeleted(now(), now(), listOf(it)) }
        if (u.removed.isNotEmpty()) q.setDeleted(null, now(), u.removed)
    }

    fun observeForObjective(objectiveId: String): Flow<List<ObjectiveLog>> =
        q.selectForObjective(objectiveId).asFlow().mapToList(io).map { rows ->
            rows.map { ObjectiveLog(Person(it.user_id, it.name, it.color_index.toInt()), LogStyle.of(it.style)) }
        }

    fun observeTripLog(tripId: String): Flow<List<TripLogEntry>> =
        q.selectForTrip(tripId).asFlow().mapToList(io).map { rows ->
            rows.map {
                TripLogEntry(
                    Person(it.user_id, it.name, it.color_index.toInt()),
                    LogStyle.of(it.style),
                    LocalDate.parse(it.date),
                    it.objective_id,
                    it.objective_name,
                    Grade(GradeScale.valueOf(it.grade_scale), it.grade_value),
                    it.sector_name,
                )
            }
        }

    fun observeLogbook(): Flow<List<LogbookEntry>> =
        q.selectMine(myId).asFlow().mapToList(io).map { rows ->
            rows.map {
                LogbookEntry(
                    it.id, LogStyle.of(it.style), LocalDate.parse(it.date), it.snapshot_name,
                    Grade(GradeScale.valueOf(it.snapshot_grade_scale), it.snapshot_grade_value),
                    it.trip_name, it.snapshot_crag, it.objective_id,
                )
            }
        }
}
