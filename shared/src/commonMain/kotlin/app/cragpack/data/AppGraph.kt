package app.cragpack.data

import app.cash.sqldelight.db.SqlDriver
import app.cragpack.data.db.CragpackDb
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.SupervisorJob
import app.cragpack.domain.invite.inviteToken
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
class AppGraph internal constructor(driver: SqlDriver, private val clock: Clock, supabase: Supabase?) {
    constructor(driver: SqlDriver) : this(driver, Clock.System, Supabase.fromConfig())

    val db = CragpackDb(driver)

    init {
        outboxTriggers().forEach { driver.execute(null, it, 0) }
    }

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
    val account = AccountRepository(db, supabase, me, ::nowMillis)
    val sync = SyncRepository(db, driver, supabase, account, me)
    val invites = InviteRepository(supabase, account, sync)
    val crew = CrewRepository(supabase, account, sync)

    /**
     * An invite link the phone opened the app with (Android App Links), waiting for the UI to show Join.
     * ponytail: iOS needs Universal Links (an associated domain, so the real domain and a paid Apple account);
     * until then iOS joins through Paste invite link.
     */
    val openedInvite = MutableStateFlow<String?>(null)

    fun openLink(url: String?) {
        url?.let(::inviteToken)?.let { openedInvite.value = it }
    }

    /** Called once by the platform entry point: syncing runs for as long as the app process does. */
    fun startSync() {
        sync.start(CoroutineScope(SupervisorJob() + Dispatchers.IO))
    }

    fun today(): LocalDate = clock.todayIn(TimeZone.currentSystemDefault())

    private fun nowMillis() = clock.now().toEpochMilliseconds()
}

@OptIn(ExperimentalUuidApi::class)
internal fun newId(): String = Uuid.random().toString()
