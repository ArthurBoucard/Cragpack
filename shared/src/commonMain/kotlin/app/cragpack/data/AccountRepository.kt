package app.cragpack.data

import app.cash.sqldelight.TransactionWithoutReturn
import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToOneOrNull
import app.cragpack.data.db.CragpackDb
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

data class Account(val name: String, val email: String, val provider: String)

/**
 * The account behind this phone's user (D3, D4). Without one, everything stays on this phone.
 * Signing in turns the local user into the account; signing out clears the phone.
 * The session lives in the device-only setting table.
 */
class AccountRepository internal constructor(
    private val db: CragpackDb,
    private val auth: SupabaseAuth?,
    private val me: MutableStateFlow<String>,
    private val now: () -> Long,
) {
    private val io = Dispatchers.IO
    private val refreshing = Mutex()

    /** False in builds without Supabase keys: accounts can't be created there. */
    val available: Boolean get() = auth != null

    fun observe(): Flow<Account?> = db.userQueries.selectMe().asFlow().mapToOneOrNull(io).map { u ->
        u?.takeIf { it.auth_provider != LOCAL }?.let { Account(it.name, it.email.orEmpty(), it.auth_provider) }
    }

    /** True once signed in; false when the email has to be confirmed first. */
    suspend fun signUp(name: String, email: String, password: String): Boolean {
        val session = auth().signUp(email.trim(), password, name.trim()) ?: return false
        adopt(session, "email")
        return true
    }

    suspend fun signIn(email: String, password: String) = adopt(auth().signIn(email.trim(), password), "email")

    suspend fun resendConfirmation(email: String) = auth().resendConfirmation(email.trim())

    /** Clears the phone and starts again as a new local user. The account's trips stay on the server. */
    suspend fun signOut() {
        runCatching { accessToken()?.let { auth?.signOut(it) } } // offline: the session just expires
        val id = newId()
        withContext(io) {
            val t = now()
            db.transaction {
                db.userQueries.wipe()
                db.settingQueries.forgetSession()
                db.userQueries.insertMe(id, t, t)
                switchTo(id)
            }
        }
        // ponytail: beta files stay in app storage, unreferenced; Storage management (Phase 3) clears them.
    }

    /** A valid access token, refreshed when it's about to run out; null without an account. */
    internal suspend fun accessToken(): String? = refreshing.withLock {
        val (access, refresh, expires) = withContext(io) { listOf(ACCESS, REFRESH, EXPIRES).map { db.settingQueries.get(it).executeAsOneOrNull() } }
        if (access == null || refresh == null) return@withLock null
        if ((expires?.toLongOrNull() ?: 0) * 1000 - now() > 60_000) return@withLock access
        auth().refresh(refresh).also { withContext(io) { save(it) } }.accessToken
    }

    private suspend fun adopt(session: Session, provider: String) = withContext(io) {
        db.transaction {
            db.userQueries.adoptAccount(
                new = session.userId, old = me.value, name = session.name, email = session.email, provider = provider, now = now(),
            )
            save(session)
            switchTo(session.userId)
        }
    }

    /** Set before the commit wakes the screens up, so they already see the new id; put back on rollback. */
    private fun TransactionWithoutReturn.switchTo(id: String) {
        val old = me.value
        me.value = id
        afterRollback { me.value = old }
    }

    private fun save(session: Session) {
        db.settingQueries.put(ACCESS, session.accessToken)
        db.settingQueries.put(REFRESH, session.refreshToken)
        db.settingQueries.put(EXPIRES, session.expiresAt.toString())
    }

    private fun auth() = auth ?: throw AuthException("Accounts aren't set up in this build", null)

    private companion object {
        const val LOCAL = "local"
        const val ACCESS = "auth_access"
        const val REFRESH = "auth_refresh"
        const val EXPIRES = "auth_expires"
    }
}
