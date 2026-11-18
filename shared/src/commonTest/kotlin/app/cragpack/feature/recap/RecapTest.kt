package app.cragpack.feature.recap

import app.cragpack.data.AppGraph
import app.cragpack.data.testDriver
import app.cragpack.domain.grade.Grade
import app.cragpack.domain.grade.GradeDisplay
import app.cragpack.domain.grade.GradeScale
import app.cragpack.domain.log.LogStyle
import app.cragpack.domain.objective.ClimbStyle
import app.cragpack.domain.settings.Theme
import app.cragpack.domain.settings.formatBytes
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

class RecapTest {
    private val graph = AppGraph(testDriver())
    private val tripId = graph.trips.create("Albarracín", "Albarracín", LocalDate.parse("2026-04-17"), LocalDate.parse("2026-04-19"))

    @Test
    fun countsSendsFlashesPerDayAndPerClimber() = runTest {
        val joker = graph.objectives.add(tripId, "La Joker", ClimbStyle.Boulder, Grade(GradeScale.Font, "7A"), null)
        val rose = graph.objectives.add(tripId, "La Marie-Rose", ClimbStyle.Boulder, Grade(GradeScale.Font, "6A"), null)
        val carnage = graph.objectives.add(tripId, "Carnage", ClimbStyle.Boulder, Grade(GradeScale.Font, "7B+"), null)
        graph.logs.log(joker, LogStyle.Flash)
        graph.logs.log(rose, LogStyle.Sent)
        graph.logs.log(carnage, LogStyle.Attempt) // not a send
        val days = graph.plan.observeDays(tripId).first()
        graph.plan.updateDay(days[1].id, null, isRest = true, notes = "")

        // Logs are dated today; re-date them onto the trip's first day for a stable test.
        val log = graph.logs.observeTripLog(tripId).first().map { it.copy(date = days[0].date) }
        val r = recap(log, graph.plan.observeDays(tripId).first(), graph.trips.observeTrip(tripId).first()!!.crew)

        assertEquals(2, r.sends)
        assertEquals(1, r.flashes)
        assertEquals(listOf(2, 0, 0), r.days.map { it.sends })
        assertEquals(listOf(false, true, false), r.days.map { it.isRest })
        val me = r.climbers.single()
        assertEquals(2, me.sends)
        assertEquals("7A", me.hardest?.value)
    }

    @Test
    fun settingsKeepChoicesAndFallBackToDefaults() = runTest {
        val settings = graph.settings
        assertEquals(GradeDisplay.AsEntered, settings.current().gradeDisplay)
        settings.setGradeDisplay(GradeDisplay.V)
        settings.setTheme(Theme.Sunlight)
        settings.setWifiOnly(false)
        val s = settings.observe().first()
        assertEquals(GradeDisplay.V, s.gradeDisplay)
        assertEquals(Theme.Sunlight, s.theme)
        assertEquals(false, s.wifiOnly)
        assertEquals("V6", Grade(GradeScale.Font, "7A").display(s.gradeDisplay))
    }

    @Test
    fun bytesReadLikeStorageSizes() {
        assertEquals("1 KB", formatBytes(1))
        assertEquals("12 MB", formatBytes(12_300_000))
        assertEquals("1.2 GB", formatBytes(1_234_000_000))
    }
}
