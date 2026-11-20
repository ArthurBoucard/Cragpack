package app.cragpack.data

import app.cash.sqldelight.db.SqlDriver
import app.cragpack.data.db.CragpackDb
import kotlinx.coroutines.flow.MutableStateFlow
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
class AppGraph internal constructor(driver: SqlDriver, private val clock: Clock, auth: SupabaseAuth?) {
    constructor(driver: SqlDriver) : this(driver, Clock.System, SupabaseAuth.fromConfig())

    val db = CragpackDb(driver)

    /**
     * The person using this phone. Created on first launch, no account needed (D4). It becomes the
     * account's id on sign-in and a fresh local id on sign-out, so read it when you need it.
     */
    private val me = MutableStateFlow(
        db.userQueries.selectMe().executeAsOneOrNull()?.id
            ?: newId().also { val now = nowMillis(); db.userQueries.insertMe(it, now, now) },
    )
    val myId: String get() = me.value

    val trips = TripRepository(db, me, ::nowMillis)
    val objectives = ObjectiveRepository(db, me, ::nowMillis)
    val logs = LogRepository(db, me, ::nowMillis, ::today)
    val plan = PlanRepository(db, ::nowMillis)
    val gear = GearRepository(db, me, ::nowMillis)
    val settings = SettingsRepository(db)
    val account = AccountRepository(db, auth, me, ::nowMillis)

    fun today(): LocalDate = clock.todayIn(TimeZone.currentSystemDefault())

    private fun nowMillis() = clock.now().toEpochMilliseconds()
}

@OptIn(ExperimentalUuidApi::class)
internal fun newId(): String = Uuid.random().toString()
