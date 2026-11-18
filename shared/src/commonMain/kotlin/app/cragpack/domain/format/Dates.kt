package app.cragpack.domain.format

import kotlinx.datetime.LocalDate

// ponytail: English only. Move to string resources when French lands (tech spec §8).
private fun LocalDate.mon() = month.name.take(3).lowercase().replaceFirstChar { it.uppercase() }

/** "17–21 Apr", "28 Apr – 3 May", "30 Dec 2026 – 2 Jan 2027". */
fun formatDateRange(start: LocalDate, end: LocalDate): String = when {
    start.year != end.year -> "${start.day} ${start.mon()} ${start.year} – ${end.day} ${end.mon()} ${end.year}"
    start.month != end.month -> "${start.day} ${start.mon()} – ${end.day} ${end.mon()}"
    start == end -> "${start.day} ${start.mon()}"
    else -> "${start.day}–${end.day} ${end.mon()}"
}

/** "Sat 17 Apr" for date fields. */
fun formatDayShort(date: LocalDate): String =
    "${date.dayOfWeek.name.take(3).lowercase().replaceFirstChar { it.uppercase() }} ${date.day} ${date.mon()}"

/** "Oct", or "Oct 2025" when not this year. */
fun formatMonth(date: LocalDate, today: LocalDate): String =
    if (date.year == today.year) date.mon() else "${date.mon()} ${date.year}"

/** "Sun 18" for trip log headers: the trip's dates are already on screen. */
fun formatWeekday(date: LocalDate): String =
    "${date.dayOfWeek.name.take(3).lowercase().replaceFirstChar { it.uppercase() }} ${date.day}"

/** "2 Apr", or "2 Apr 2025" when not this year: logbook entries. */
fun formatDay(date: LocalDate, today: LocalDate): String =
    if (date.year == today.year) "${date.day} ${date.mon()}" else "${date.day} ${date.mon()} ${date.year}"
