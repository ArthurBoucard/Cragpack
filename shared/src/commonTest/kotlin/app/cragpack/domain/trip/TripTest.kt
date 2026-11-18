package app.cragpack.domain.trip

import app.cragpack.domain.format.formatDateRange
import app.cragpack.domain.format.formatDayShort
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

class TripTest {
    private fun trip(id: String, start: String, end: String) =
        Trip(id, id, "Fontainebleau", LocalDate.parse(start), LocalDate.parse(end))

    private val font = trip("font", "2026-04-17", "2026-04-21")

    @Test
    fun timing() {
        assertEquals(TripTiming.Upcoming(2), font.timing(LocalDate.parse("2026-04-15")))
        assertEquals(TripTiming.Now(day = 1, days = 5), font.timing(LocalDate.parse("2026-04-17")))
        assertEquals(TripTiming.Now(day = 5, days = 5), font.timing(LocalDate.parse("2026-04-21")))
        assertEquals(TripTiming.Past, font.timing(LocalDate.parse("2026-04-22")))
    }

    @Test
    fun homeOrderIsNowThenSoonestThenMostRecentPast() {
        val today = LocalDate.parse("2026-04-18")
        val trips = listOf(
            trip("old", "2025-10-01", "2025-10-08"),
            trip("later", "2026-09-01", "2026-09-05"),
            trip("recent", "2026-03-01", "2026-03-04"),
            font,
            trip("soon", "2026-07-10", "2026-07-19"),
        )
        assertEquals(listOf("font", "soon", "later", "recent", "old"), trips.sortedWith(homeOrder(today)).map { it.id })
    }

    @Test
    fun dateRanges() {
        fun r(a: String, b: String) = formatDateRange(LocalDate.parse(a), LocalDate.parse(b))
        assertEquals("17–21 Apr", r("2026-04-17", "2026-04-21"))
        assertEquals("28 Apr – 3 May", r("2026-04-28", "2026-05-03"))
        assertEquals("30 Dec 2026 – 2 Jan 2027", r("2026-12-30", "2027-01-02"))
        assertEquals("17 Apr", r("2026-04-17", "2026-04-17"))
        assertEquals("Fri 17 Apr", formatDayShort(LocalDate.parse("2026-04-17")))
    }
}
