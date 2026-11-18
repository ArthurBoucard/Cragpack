package app.cragpack.data

import app.cragpack.feature.gear.GearUi
import app.cragpack.feature.gear.shown
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class GearRepositoryTest {
    private val graph = AppGraph(testDriver())
    private val gear = graph.gear
    private val tripId = graph.trips.create("Font Easter", "Fontainebleau", LocalDate.parse("2026-04-17"), LocalDate.parse("2026-04-21"))

    private suspend fun items() = gear.observeGear(tripId).first()
    private suspend fun stats() = graph.trips.observeTrip(tripId).first()!!.stats

    @Test
    fun bringOneFillsTheGapAndClaimsMayGoOver() = runTest {
        val pads = gear.add(tripId, " Crash pads ", needed = 2, dayId = null, iBringIt = false)
        assertEquals(2, items().single().missing)
        assertEquals(0, stats().gearCovered)

        gear.changeMine(pads, +1)
        gear.changeMine(pads, +1)
        assertEquals(2, items().single().mine)
        assertEquals(1, stats().gearCovered)

        gear.changeMine(pads, +1) // bringing extra is fine (tech spec §3)
        assertEquals(3, items().single().claimed)
        assertEquals(0, items().single().missing)
    }

    @Test
    fun iBringItClaimsEverythingAndZeroWithdraws() = runTest {
        val rope = gear.add(tripId, "Rope 70 m", needed = 1, dayId = null, iBringIt = true)
        assertEquals(1, items().single().mine)

        gear.changeMine(rope, -1)
        gear.changeMine(rope, -1) // never below 0
        val item = items().single()
        assertEquals(0, item.mine)
        assertEquals(emptyList(), item.bringers)
        assertEquals(1, item.missing)
    }

    @Test
    fun neededAndDeleteUpdateTheTripCounts() = runTest {
        val tape = gear.add(tripId, "Tape", needed = 1, dayId = null, iBringIt = true)
        gear.changeNeeded(tape, +2)
        gear.changeNeeded(tape, -5) // never below 1
        gear.changeNeeded(tape, +2)
        assertEquals(0, stats().gearCovered)
        gear.delete(tape)
        assertEquals(emptyList(), items())
        assertEquals(0, stats().gear)
    }

    @Test
    fun aDayFilterShowsThatDayPlusWholeTripGear() = runTest {
        val (sun, wed) = graph.plan.observeDays(tripId).first().let { it[1] to it[4] }
        gear.add(tripId, "Pads", 3, null, false)
        gear.add(tripId, "Brush", 1, sun.id, false)
        gear.add(tripId, "Stove", 1, wed.id, false)
        val ui = GearUi(items(), graph.plan.observeDays(tripId).first())

        assertEquals(listOf(sun.id, wed.id), ui.filterDays.map { it.id })
        assertEquals(listOf("Pads", "Brush"), ui.shown(sun.id).map { it.name })
        assertEquals(3, ui.shown(null).size)
    }

    @Test
    fun rejectsBadInput() {
        assertFailsWith<IllegalArgumentException> { gear.add(tripId, " ", 1, null, false) }
        assertFailsWith<IllegalArgumentException> { gear.add(tripId, "x", 0, null, false) }
    }
}
