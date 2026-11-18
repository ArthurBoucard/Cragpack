package app.cragpack.data

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
    fun rejectsInvalidTrips() {
        val d = LocalDate.parse("2026-04-17")
        assertFailsWith<IllegalArgumentException> { graph.trips.create(" ", "", d, d) }
        assertFailsWith<IllegalArgumentException> { graph.trips.create("x", "", d, LocalDate.parse("2026-04-16")) }
    }
}
