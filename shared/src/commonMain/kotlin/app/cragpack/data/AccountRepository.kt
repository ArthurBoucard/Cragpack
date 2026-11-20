package app.cragpack.data

import app.cash.sqldelight.TransactionWithoutReturn
import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToOneOrNull
import app.cragpack.data.db.CragpackDb
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject

data class Account(val name: String, val email: String, val provider: String)

/**
 * The account behind this phone's user (D3, D4). Without one, everything stays on this phone.
 * Signing in turns the local user into the account; signing out clears the phone.
 * The session lives in the device-only setting table.
 */
class AccountRepository internal constructor(
    private val db: CragpackDb,
    private val auth: Supabase?,
    private val me: MutableStateFlow<String>,
    private val now: () -> Long,
) {
    private val io = Dispatchers.IO
    private val refreshing = Mutex()

    /** Held by a sync and by sign out, so a pull can't land on a phone that was just cleared. */
    internal val session = Mutex()

    /** False in builds without Supabase keys: accounts can't be created there. */
    val available: Boolean get() = auth != null

    fun observeSignedIn(): Flow<Boolean> = observe().map { it != null }.distinctUntilChanged()

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

    /** The phone got an ID token from Google's own sign-in; [nonce] is the raw one whose hash Google signed in. */
    suspend fun signInWithGoogle(idToken: String, nonce: String) =
        adopt(auth().signInWithIdToken("google", idToken, nonce), "google")

    suspend fun resendConfirmation(email: String) = auth().resendConfirmation(email.trim())

    /** Clears the phone and starts again as a new local user. The account's trips stay on the server. */
    suspend fun signOut() = session.withLock {
        runCatching { accessToken()?.let { auth?.signOut(it) } } // offline: the session just expires
        clearPhone()
    }

    /**
     * Settings → Delete account (tech spec §2): the server deletes the account and its climbs, stars and claims
     * (supabase/003_crew.sql), then the phone is cleared. Online only: throws when it can't reach Supabase.
     */
    suspend fun deleteAccount() = session.withLock {
        val token = accessToken() ?: throw SupabaseException("Sign in first", null)
        auth().rpc("delete_account", JsonObject(emptyMap()), token)
        clearPhone()
    }

    private suspend fun clearPhone() {
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

    private fun auth() = auth ?: throw SupabaseException("Accounts aren't set up in this build", null)

    private companion object {
        const val LOCAL = "local"
        const val ACCESS = "auth_access"
        const val REFRESH = "auth_refresh"
        const val EXPIRES = "auth_expires"
    }
}
