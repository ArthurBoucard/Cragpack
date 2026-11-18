package app.cragpack.data

import app.cragpack.domain.grade.Grade
import app.cragpack.domain.grade.GradeScale
import app.cragpack.domain.log.LogStyle
import app.cragpack.domain.log.PyramidRow
import app.cragpack.domain.log.hardest
import app.cragpack.domain.log.pyramid
import app.cragpack.domain.objective.ClimbStyle
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

class LogRepositoryTest {
    private val graph = AppGraph(testDriver())
    private val logs = graph.logs
    private val tripId = graph.trips.create("Font Easter", "Fontainebleau", LocalDate.parse("2026-04-17"), LocalDate.parse("2026-04-21"))
    private val joker = graph.objectives.add(tripId, "La Joker", ClimbStyle.Boulder, Grade(GradeScale.Font, "7A"), null)

    private suspend fun mine() = logs.observeForObjective(joker).first().map { it.style }

    @Test
    fun oneSendPerClimberAndTappingItAgainRemovesIt() = runTest {
        logs.log(joker, LogStyle.Attempt)
        logs.log(joker, LogStyle.Attempt)
        logs.log(joker, LogStyle.Sent)
        logs.log(joker, LogStyle.Flash)
        assertEquals(listOf(LogStyle.Attempt, LogStyle.Attempt, LogStyle.Flash), mine())
        assertEquals(1, graph.trips.observeTrip(tripId).first()!!.stats.sent)

        logs.log(joker, LogStyle.Flash)
        assertEquals(listOf(LogStyle.Attempt, LogStyle.Attempt), mine())
    }

    @Test
    fun undoPutsBackWhatWasThere() = runTest {
        logs.log(joker, LogStyle.Sent)
        val undo = logs.log(joker, LogStyle.Flash)
        logs.undo(undo)
        assertEquals(listOf(LogStyle.Sent), mine())
    }

    @Test
    fun logbookKeepsSendsAfterTheTripIsGoneAndIncludesOutsideClimbs() = runTest {
        logs.log(joker, LogStyle.Sent)
        logs.logOutside("Las Cuevas", Grade(GradeScale.French, "6c+"), "", LocalDate.parse("2026-04-02"), LogStyle.Sent)
        graph.objectives.delete(joker)

        val book = logs.observeLogbook().first()
        assertEquals(listOf("La Joker", "Las Cuevas"), book.map { it.name })
        assertEquals(listOf("Font Easter", null), book.map { it.trip })
        assertEquals(null, book.first().objectiveId)
        assertEquals(emptyList(), logs.observeTripLog(tripId).first())
    }

    @Test
    fun pyramidGroupsBandsAndHardestPicksTheTopGrade() {
        val sends = listOf("7A", "6A", "6C+", "4", "3").map { Grade(GradeScale.Font, it) }
        assertEquals(
            listOf(PyramidRow("7A–7C+", 7, 1), PyramidRow("6A–6C+", 6, 2), PyramidRow("≤4+", 4, 2)),
            pyramid(sends, GradeScale.Font),
        )
        assertEquals("7A", hardest(sends)?.value)
    }
}
