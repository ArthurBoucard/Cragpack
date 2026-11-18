package app.cragpack.feature.recap

import app.cragpack.data.PlanDay
import app.cragpack.data.TripLogEntry
import app.cragpack.domain.grade.Grade
import app.cragpack.domain.log.hardest
import app.cragpack.domain.trip.Person
import kotlinx.datetime.LocalDate

data class DaySends(val date: LocalDate, val isRest: Boolean, val sends: Int)

data class ClimberRecap(val person: Person, val sends: Int, val hardest: Grade?)

/** Everything the recap (08) shows. Sends = sent + flash entries; the log keeps one send per climber per objective. */
data class Recap(
    val sends: Int,
    val flashes: Int,
    val sentGrades: List<Grade>,
    val days: List<DaySends>,
    /** The whole crew, most sends first; climbers with no send yet included. */
    val climbers: List<ClimberRecap>,
)

fun recap(entries: List<TripLogEntry>, days: List<PlanDay>, crew: List<Person>): Recap {
    val sends = entries.filter { it.style.isSend }
    val byDate = sends.groupingBy { it.date }.eachCount()
    val byPerson = sends.groupBy { it.person.id }
    return Recap(
        sends = sends.size,
        flashes = sends.count { it.style == app.cragpack.domain.log.LogStyle.Flash },
        sentGrades = sends.map { it.grade },
        days = days.map { DaySends(it.date, it.isRest, byDate[it.date] ?: 0) },
        climbers = crew
            .map { p -> byPerson[p.id].orEmpty().let { ClimberRecap(p, it.size, hardest(it.map { e -> e.grade })) } }
            .sortedByDescending { it.sends },
    )
}
