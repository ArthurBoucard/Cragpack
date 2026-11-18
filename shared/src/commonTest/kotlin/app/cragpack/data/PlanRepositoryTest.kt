package app.cragpack.data

import app.cragpack.domain.grade.Grade
import app.cragpack.domain.grade.GradeScale
import app.cragpack.domain.objective.ClimbStyle
import app.cragpack.domain.plan.keyBetween
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PlanRepositoryTest {
    private val graph = AppGraph(testDriver())
    private val tripId = graph.trips.create("Font Easter", "Fontainebleau", LocalDate.parse("2026-04-17"), LocalDate.parse("2026-04-21"))
    private fun objective(name: String) = graph.objectives.add(tripId, name, ClimbStyle.Boulder, Grade(GradeScale.Font, "7A"), null)
    private suspend fun days() = graph.plan.observeDays(tripId).first()
    private suspend fun plan() = graph.plan.observePlan(tripId).first()

    @Test
    fun keysAlwaysFitBetweenTheirNeighbours() {
        val keys = mutableListOf(keyBetween(null, null))
        repeat(500) {
            val i = Random(it).nextInt(keys.size + 1)
            val k = keyBetween(keys.getOrNull(i - 1), keys.getOrNull(i))
            keys.add(i, k)
        }
        assertEquals(keys.sorted(), keys)
        assertEquals(keys.size, keys.toSet().size)
        assertTrue(keys.none { it.endsWith('0') })
    }

    @Test
    fun placeReorderMoveAndUnplan() = runTest {
        val (d1, d2) = days().take(2).map { it.id }
        val a = objective("A"); val b = objective("B"); val c = objective("C")
        graph.plan.place(a, d1, Int.MAX_VALUE)
        graph.plan.place(b, d1, Int.MAX_VALUE)
        graph.plan.place(c, d1, 0)
        assertEquals(listOf(c, a, b), plan().map { it.objectiveId })
        assertEquals(0, graph.trips.observeTrip(tripId).first()!!.stats.unplanned)

        graph.plan.place(c, d1, 2) // drag C to the end
        assertEquals(listOf(a, b, c), plan().map { it.objectiveId })

        graph.plan.place(b, d2, 0) // move B to day 2
        assertEquals(d2, plan().single { it.objectiveId == b }.dayId)

        graph.plan.unplan(a)
        assertEquals(setOf(b, c), plan().map { it.objectiveId }.toSet())
        assertEquals(1, graph.trips.observeTrip(tripId).first()!!.stats.unplanned)
    }

    @Test
    fun dayGetsSectorRestAndNotesAndTheSectorItsApproach() = runTest {
        val day = days().first()
        val sector = graph.objectives.addSector(tripId, "Bas Cuvier")
        graph.plan.updateDay(day.id, sector, isRest = false, notes = " Warm up first ")
        graph.plan.setApproach(sector, "5 min approach")

        val updated = days().first()
        assertEquals("Bas Cuvier", updated.sector?.name)
        assertEquals("5 min approach", updated.sector?.approach)
        assertEquals("Warm up first", updated.notes)
        assertEquals("Bas Cuvier", graph.trips.observeDay(tripId, day.date).first()?.sector)
    }
}
