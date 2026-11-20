package app.cragpack.feature

import androidx.compose.runtime.compositionLocalOf
import app.cragpack.data.SyncState
import app.cragpack.designsystem.component.SyncStatus

/** What every top bar's status pill shows; App() provides it from the outbox (tech spec §3). */
val LocalSyncStatus = compositionLocalOf<SyncStatus> { SyncStatus.OnThisPhone }

fun SyncState.toStatus(): SyncStatus = when {
    !signedIn -> SyncStatus.OnThisPhone
    pending > 0 -> SyncStatus.Pending(pending)
    else -> SyncStatus.Synced
}
