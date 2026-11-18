package app.cragpack.data

import app.cragpack.domain.grade.Grade
import app.cragpack.domain.grade.GradeScale
import app.cragpack.domain.log.LogStyle
import app.cragpack.domain.objective.ClimbStyle
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class TripRepositoryTest {
    private val graph = AppGraph(testDriver())

    @Test
    fun localUserIsCreatedOnceAndReused() {
        val driver = testDriver()
        assertEquals(AppGraph(driver).myId, AppGraph(driver).myId)
    }

    @Test
    fun createMakesOrganiserAndOneDayPerDate() = runTest {
        val id = graph.trips.create(" Font Easter ", "Fontainebleau", LocalDate.parse("2026-04-17"), LocalDate.parse("2026-04-21"))

        val item = graph.trips.observeTrips().first().single()
        assertEquals("Font Easter", item.trip.name)
        assertEquals(1, item.crew.single().colorIndex)
        assertEquals(5, item.trip.days)
        assertEquals(false, graph.trips.observeDay(id, LocalDate.parse("2026-04-21")).first()?.isRest)
        assertEquals(null, graph.trips.observeDay(id, LocalDate.parse("2026-04-22")).first())

        val stats = graph.trips.observeTrip(id).first()!!.stats
        assertEquals(TripStats(objectives = 0, sent = 0, unplanned = 0, gear = 0, gearCovered = 0, beta = 0), stats)
    }

    @Test
    fun shorterDatesUnplanAndMoveGearToTheWholeTrip() = runTest {
        val id = graph.trips.create("Font Easter", "Fontainebleau", LocalDate.parse("2026-04-17"), LocalDate.parse("2026-04-21"))
        val lastDay = graph.plan.observeDays(id).first().last()
        val joker = graph.objectives.add(id, "La Joker", ClimbStyle.Boulder, Grade(GradeScale.Font, "7A"), null)
        graph.plan.place(joker, lastDay.id, 0)
        graph.gear.add(id, "Stove", 1, lastDay.id, iBringIt = false)

        graph.trips.changeDates(id, LocalDate.parse("2026-04-16"), LocalDate.parse("2026-04-20"))

        assertEquals((16..20).map { LocalDate(2026, 4, it) }, graph.plan.observeDays(id).first().map { it.date })
        assertEquals(emptyList(), graph.plan.observePlan(id).first())
        assertEquals(null, graph.gear.observeGear(id).first().single().dayId)
        assertEquals(5, graph.trips.observeTrip(id).first()!!.trip.days)
    }

    @Test
    fun deletedTripLeavesHomeButSendsStayInTheLogbook() = runTest {
        val id = graph.trips.create("Font Easter", "Fontainebleau", LocalDate.parse("2026-04-17"), LocalDate.parse("2026-04-21"))
        graph.trips.rename(id, "Font Easter 2026", "Font")
        val joker = graph.objectives.add(id, "La Joker", ClimbStyle.Boulder, Grade(GradeScale.Font, "7A"), null)
        graph.logs.log(joker, LogStyle.Sent)
        graph.trips.setCover(id, newId(), "image/jpeg", 10, "media/c.jpg")
        assertEquals("media/c.jpg", graph.trips.observeTrip(id).first()!!.trip.coverPath)

        graph.trips.delete(id)

        assertEquals(emptyList(), graph.trips.observeTrips().first())
        assertEquals(null, graph.trips.observeTrip(id).first())
        val kept = graph.logs.observeLogbook().first().single()
        assertEquals("La Joker", kept.name)
        assertEquals("Font", kept.crag) // the trip's name is gone, its crag snapshot stays
        assertEquals(true, kept.onTrip)
    }

    @Test
    fun rejectsInvalidTrips() {
        val d = LocalDate.parse("2026-04-17")
        assertFailsWith<IllegalArgumentException> { graph.trips.create(" ", "", d, d) }
        assertFailsWith<IllegalArgumentException> { graph.trips.create("x", "", d, LocalDate.parse("2026-04-16")) }
    }
}
