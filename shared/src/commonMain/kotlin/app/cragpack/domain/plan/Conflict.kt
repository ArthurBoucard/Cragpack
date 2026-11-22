package app.cragpack.domain.plan

import app.cragpack.domain.grade.Grade
import app.cragpack.domain.trip.Person
import kotlinx.datetime.LocalDate

/**
 * The one real sync conflict (tech spec §3): while offline, I moved an objective from [base] to [mine] and someone
 * else moved it from [base] to [theirs], a different day. Day ids; null = not planned. A reorder within the same
 * day, or both of us picking the same day, is no conflict: last write wins.
 */
fun isPlanConflict(base: String?, mine: String?, theirs: String?): Boolean =
    mine != base && theirs != base && mine != theirs

/** One side of a conflict: where the objective went (null date = unplanned), when, and who moved it (null = you). */
data class Move(val date: LocalDate?, val sector: String?, val atMillis: Long, val by: Person?)

/** Sync conflict (19): which move to keep. */
data class PlanConflict(val objectiveId: String, val name: String, val grade: Grade, val mine: Move, val theirs: Move)
