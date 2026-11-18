package app.cragpack.domain.trip

import kotlinx.datetime.LocalDate
import kotlinx.datetime.daysUntil

data class Trip(val id: String, val name: String, val crag: String, val start: LocalDate, val end: LocalDate) {
    val days: Int get() = start.daysUntil(end) + 1
}

/** A climber shown as an avatar. [colorIndex] is the join order (1..5 then repeat). Blank [name] = no name yet. */
data class Person(val id: String, val name: String, val colorIndex: Int) {
    /** For lists and filters: you have no name until you create an account. */
    val label: String get() = name.ifBlank { "You" }
}

sealed interface TripTiming {
    /** On the trip today: day [day] of [days]. */
    data class Now(val day: Int, val days: Int) : TripTiming
    data class Upcoming(val inDays: Int) : TripTiming
    data object Past : TripTiming
}

fun Trip.timing(today: LocalDate): TripTiming = when {
    today < start -> TripTiming.Upcoming(today.daysUntil(start))
    today > end -> TripTiming.Past
    else -> TripTiming.Now(day = start.daysUntil(today) + 1, days = days)
}

/** Trips home order: trips on now, then upcoming (soonest first), then past (most recent first). */
fun homeOrder(today: LocalDate): Comparator<Trip> = compareBy<Trip> {
    when (it.timing(today)) {
        is TripTiming.Now -> 0
        is TripTiming.Upcoming -> 1
        TripTiming.Past -> 2
    }
}.thenBy { if (it.timing(today) == TripTiming.Past) -it.end.toEpochDays() else it.start.toEpochDays() }
