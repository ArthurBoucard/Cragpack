package app.cragpack.data

import app.cash.sqldelight.db.SqlDriver
import app.cragpack.data.db.CragpackDb
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import kotlin.time.Clock
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Everything the app needs, built once per process by the platform entry point.
 * ponytail: hand-wired; add a DI library when wiring by hand gets painful.
 */
class AppGraph(driver: SqlDriver, private val clock: Clock = Clock.System) {
    val db = CragpackDb(driver)

    /** The person using this phone. Created on first launch, no account needed (D4). */
    val myId: String = db.userQueries.selectMe().executeAsOneOrNull()?.id
        ?: newId().also { val now = nowMillis(); db.userQueries.insertMe(it, now, now) }

    val trips = TripRepository(db, myId, ::nowMillis)
    val objectives = ObjectiveRepository(db, myId, ::nowMillis)
    val logs = LogRepository(db, myId, ::nowMillis, ::today)
    val plan = PlanRepository(db, ::nowMillis)
    val gear = GearRepository(db, myId, ::nowMillis)
    val settings = SettingsRepository(db)

    fun today(): LocalDate = clock.todayIn(TimeZone.currentSystemDefault())

    private fun nowMillis() = clock.now().toEpochMilliseconds()
}

@OptIn(ExperimentalUuidApi::class)
internal fun newId(): String = Uuid.random().toString()
