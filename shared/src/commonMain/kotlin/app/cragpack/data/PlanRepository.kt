package app.cragpack.data

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.cragpack.data.db.CragpackDb
import app.cragpack.domain.grade.Grade
import app.cragpack.domain.grade.GradeScale
import app.cragpack.domain.objective.Sector
import app.cragpack.domain.plan.Move
import app.cragpack.domain.plan.PlanConflict
import app.cragpack.domain.trip.Person
import app.cragpack.domain.plan.keyBetween
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.LocalDate

data class PlanDay(val id: String, val date: LocalDate, val isRest: Boolean, val sector: Sector?, val notes: String)

/** An objective's place in the plan. */
data class Planned(val objectiveId: String, val dayId: String)

class PlanRepository internal constructor(private val db: CragpackDb, private val now: () -> Long) {
    private val io = Dispatchers.IO

    fun observeDays(tripId: String): Flow<List<PlanDay>> =
        db.tripQueries.selectDays(tripId).asFlow().mapToList(io).map { rows ->
            rows.map {
                PlanDay(
                    it.id, LocalDate.parse(it.date), it.is_rest != 0L,
                    if (it.sector_id != null && it.sector_name != null) Sector(it.sector_id, it.sector_name, it.approach_note) else null,
                    it.notes,
                )
            }
        }

    /** Every planned objective of the trip, in day order. */
    fun observePlan(tripId: String): Flow<List<Planned>> =
        db.objectiveQueries.selectPlan(tripId).asFlow().mapToList(io).map { rows -> rows.map { Planned(it.objective_id, it.day_id) } }

    /** Puts an objective on a day at [index] among that day's objectives (past the end = last). Moves it if planned elsewhere. */
    fun place(objectiveId: String, dayId: String, index: Int) = db.transaction {
        val others = db.objectiveQueries.selectDayPlan(dayId).executeAsList().filter { it.objective_id != objectiveId }
        val i = index.coerceIn(0, others.size)
        val key = keyBetween(others.getOrNull(i - 1)?.order_key, others.getOrNull(i)?.order_key)
        db.objectiveQueries.setPlan(objectiveId, dayId, key, now(), null)
    }

    fun unplan(objectiveId: String) = db.transaction {
        val item = db.objectiveQueries.selectPlanItem(objectiveId).executeAsOneOrNull() ?: return@transaction
        val t = now()
        db.objectiveQueries.setPlan(objectiveId, item.day_id, item.order_key, t, t)
    }

    /** Sync conflicts (19) on this trip, waiting for my choice. */
    fun observeConflicts(tripId: String): Flow<List<PlanConflict>> =
        db.syncQueries.selectConflicts(tripId).asFlow().mapToList(io).map { rows ->
            rows.map {
                PlanConflict(
                    it.objective_id, it.name, Grade(GradeScale.valueOf(it.grade_scale), it.grade_value),
                    mine = Move(it.my_date?.let(LocalDate::parse), it.my_sector, it.my_at, by = null),
                    theirs = Move(
                        it.their_date?.let(LocalDate::parse), it.their_sector, it.their_at,
                        by = it.their_id?.let { id -> Person(id, it.their_name, it.their_color.toInt()) },
                    ),
                )
            }
        }

    /** Keep my move: it goes up with the next sync and wins. */
    fun keepMine(objectiveId: String) = db.syncQueries.keepMine(objectiveId)

    /** Take their move: it lands here, and mine is dropped. */
    fun acceptTheirs(objectiveId: String) = db.syncQueries.acceptTheirs(objectiveId)

    fun updateDay(dayId: String, sectorId: String?, isRest: Boolean, notes: String) =
        db.tripQueries.updateDay(sectorId, if (isRest) 1 else 0, notes.trim(), now(), dayId)

    fun setApproach(sectorId: String, approach: String) =
        db.tripQueries.updateSectorApproach(approach.trim().ifBlank { null }, now(), sectorId)
}
