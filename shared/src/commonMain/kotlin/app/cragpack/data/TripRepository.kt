package app.cragpack.data

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.cash.sqldelight.coroutines.mapToOne
import app.cash.sqldelight.coroutines.mapToOneOrNull
import app.cragpack.data.db.CragpackDb
import app.cragpack.domain.trip.Person
import app.cragpack.domain.trip.Trip
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus

data class TripListItem(val trip: Trip, val crew: List<Person>, val sends: Int)

data class TripStats(
    val objectives: Int,
    val sent: Int,
    val unplanned: Int,
    val gear: Int,
    val gearCovered: Int,
    val beta: Int,
)

/** What a given date looks like in the plan: a sector, a rest day, or nothing planned yet. */
data class PlannedDay(val isRest: Boolean, val sector: String?)

data class TripDetail(val trip: Trip, val crew: List<Person>, val stats: TripStats)

class TripRepository internal constructor(
    private val db: CragpackDb,
    private val myId: String,
    private val now: () -> Long,
) {
    private val io = Dispatchers.IO

    fun observeTrips(): Flow<List<TripListItem>> = combine(
        db.tripQueries.selectAll(::toTrip).asFlow().mapToList(io),
        observeCrews(),
        db.tripQueries.selectSendCounts().asFlow().mapToList(io).map { rows -> rows.associate { it.trip_id to it.sends.toInt() } },
    ) { trips, crews, sends ->
        trips.map { TripListItem(it, crews[it.id].orEmpty(), sends[it.id] ?: 0) }
    }

    fun observeTrip(id: String): Flow<TripDetail?> = combine(
        db.tripQueries.selectById(id, ::toTrip).asFlow().mapToOneOrNull(io),
        observeCrews(),
        db.tripQueries.selectStats(id) { objectives, sent, unplanned, gear, covered, beta ->
            TripStats(objectives.toInt(), sent.toInt(), unplanned.toInt(), gear.toInt(), covered.toInt(), beta.toInt())
        }.asFlow().mapToOne(io),
    ) { trip, crews, stats -> trip?.let { TripDetail(it, crews[id].orEmpty(), stats) } }

    fun observeDay(tripId: String, date: LocalDate): Flow<PlannedDay?> =
        db.tripQueries.selectDayOn(tripId, date.toString()) { isRest, sector -> PlannedDay(isRest != 0L, sector) }
            .asFlow().mapToOneOrNull(io)

    /** Creates the trip with you as organiser and one day per date. Returns the new trip id. */
    fun create(name: String, crag: String, start: LocalDate, end: LocalDate): String {
        require(name.isNotBlank()) { "A trip needs a name" }
        require(end >= start) { "A trip can't end before it starts" }
        val id = newId()
        val t = now()
        db.transaction {
            db.tripQueries.insert(id, name.trim(), crag.trim(), start.toString(), end.toString(), myId, t, t)
            db.tripQueries.insertMember(id, myId, "organiser", 1, t, t)
            var date = start
            while (date <= end) {
                db.tripQueries.insertDay(newId(), id, date.toString(), t, t)
                date = date.plus(DatePeriod(days = 1))
            }
        }
        return id
    }

    private fun observeCrews(): Flow<Map<String, List<Person>>> =
        db.tripQueries.selectMembers().asFlow().mapToList(io).map { rows ->
            rows.groupBy({ it.trip_id }, { Person(it.user_id, it.name, it.color_index.toInt()) })
        }

    private fun toTrip(id: String, name: String, crag: String, start: String, end: String) =
        Trip(id, name, crag, LocalDate.parse(start), LocalDate.parse(end))
}
