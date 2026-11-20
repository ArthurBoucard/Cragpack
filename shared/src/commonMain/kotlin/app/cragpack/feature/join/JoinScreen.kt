package app.cragpack.feature.join

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.cragpack.data.AppGraph
import app.cragpack.data.InviteExpiredException
import app.cragpack.data.InvitePreview
import app.cragpack.designsystem.component.Avatar
import app.cragpack.designsystem.component.AvatarSize
import app.cragpack.designsystem.component.AvatarStack
import app.cragpack.designsystem.component.CtButton
import app.cragpack.designsystem.component.CtButtonStyle
import app.cragpack.designsystem.component.CtIcon
import app.cragpack.designsystem.component.CtScreen
import app.cragpack.designsystem.component.CtText
import app.cragpack.designsystem.component.CtaDock
import app.cragpack.designsystem.component.LightStatusBarIcons
import app.cragpack.designsystem.component.PhotoSurface
import app.cragpack.designsystem.component.dashedBorder
import app.cragpack.designsystem.icon.LucideIcon
import app.cragpack.designsystem.theme.CragpackTheme
import app.cragpack.designsystem.tokens.RadiusTokens
import app.cragpack.designsystem.tokens.SizeTokens
import app.cragpack.domain.format.formatDateRange
import app.cragpack.feature.account.AccountPromptSheet
import app.cragpack.feature.account.AccountReason
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface JoinState {
    data object Loading : JoinState
    data class Open(val preview: InvitePreview) : JoinState
    data object Expired : JoinState
    data object Unavailable : JoinState
}

class JoinViewModel(private val graph: AppGraph, private val token: String) : ViewModel() {
    var state by mutableStateOf<JoinState>(JoinState.Loading)
    var joining by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)
    val signedIn = graph.account.observe().map { it != null }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    init {
        load()
    }

    fun load() = viewModelScope.launch {
        state = JoinState.Loading
        state = try {
            JoinState.Open(graph.invites.preview(token))
        } catch (e: CancellationException) {
            throw e
        } catch (_: InviteExpiredException) {
            JoinState.Expired
        } catch (_: Exception) {
            JoinState.Unavailable
        }
    }

    /** The trip's id once joined and on this phone; null if it didn't work (the state or [error] says why). */
    suspend fun join(): String? {
        joining = true
        error = null
        return try {
            graph.invites.join(token)
        } catch (e: CancellationException) {
            throw e
        } catch (_: InviteExpiredException) {
            state = JoinState.Expired
            null
        } catch (_: Exception) {
            error = "Can't join right now. Check your connection and try again."
            null
        } finally {
            joining = false
        }
    }
}

/**
 * Join (10) and Invite expired (17). Opened from an invite link (pasted, or opened on Android). Joining needs
 * an account (D4): the prompt comes first, and the join carries on once signed in.
 * ponytail: the "Save offline" switch arrives with the offline pack (Phase 3).
 */
@Composable
fun JoinScreen(vm: JoinViewModel, onJoined: (tripId: String) -> Unit, onTrips: () -> Unit, onCreateAccount: () -> Unit) {
    val signedIn by vm.signedIn.collectAsStateWithLifecycle()
    var prompt by rememberSaveable { mutableStateOf(false) }
    var joinAfterSignIn by rememberSaveable { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    fun join() {
        if (signedIn != true) {
            joinAfterSignIn = true
            prompt = true
        } else if (!vm.joining) scope.launch { vm.join()?.let(onJoined) }
    }
    LaunchedEffect(signedIn) {
        if (signedIn == true && joinAfterSignIn) {
            joinAfterSignIn = false
            vm.join()?.let(onJoined)
        }
    }
    when (val state = vm.state) {
        JoinState.Expired -> Expired(onTrips)
        else -> CtScreen(
            statusBarStrip = false,
            dock = (state as? JoinState.Open)?.let { { CtaDock("Join trip", LucideIcon.ArrowRight, ::join, enabled = !vm.joining) } },
            overlay = {
                AccountPromptSheet(AccountReason.Join.takeIf { prompt }, onDismiss = { prompt = false; joinAfterSignIn = false }, onEmail = {
                    prompt = false
                    onCreateAccount()
                })
            },
        ) {
            when (state) {
                is JoinState.Open -> Invitation(state.preview, vm.error)
                JoinState.Unavailable -> Message("Can't open this link right now", "Check your connection and try again.") {
                    CtButton("Try again", { vm.load() }, style = CtButtonStyle.Ghost)
                }
                else -> Message("Opening the invite…", null)
            }
        }
    }
}

@Composable
private fun Invitation(p: InvitePreview, error: String?) {
    LightStatusBarIcons()
    Column(verticalArrangement = Arrangement.spacedBy(SizeTokens.gap)) {
        PhotoSurface(Modifier.fillMaxWidth().height(500.dp), RoundedCornerShape(bottomStart = RadiusTokens.xl, bottomEnd = RadiusTokens.xl)) {
            CtText("Cragpack", Modifier.statusBarsPadding().padding(SizeTokens.edge), style = CragpackTheme.type.title)
            Column(Modifier.align(Alignment.BottomStart).padding(SizeTokens.edge), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Avatar(p.inviter, p.crew.firstOrNull { it.first == p.inviter }?.second ?: 1)
                    CtText(if (p.inviter.isBlank()) "You're invited" else "${p.inviter} invited you")
                }
                CtText(p.name, Modifier.padding(top = 8.dp), style = CragpackTheme.type.titleXl)
                CtText(listOf(p.crag, formatDateRange(p.start, p.end)).filter { it.isNotBlank() }.joinToString(" · "))
                AvatarStack(p.crew, Modifier.padding(top = 8.dp), size = AvatarSize.Xl)
            }
        }
        error?.let { CtText(it, Modifier.padding(horizontal = SizeTokens.edge + 8.dp), color = CragpackTheme.colors.dangerInk) }
    }
}

/** Invite expired (17): the link was reset, or the trip is gone. */
@Composable
private fun Expired(onTrips: () -> Unit) {
    CtScreen(dock = { CtaDock("Go to my trips", LucideIcon.ArrowRight, onTrips) }) {
        Message("This link has expired", "Ask the organiser for a new one.", icon = LucideIcon.Link)
    }
}

@Composable
private fun Message(title: String, text: String?, icon: LucideIcon? = null, action: (@Composable () -> Unit)? = null) {
    Column(Modifier.fillMaxSize().statusBarsPadding().padding(SizeTokens.edge)) {
        CtText("Cragpack", style = CragpackTheme.type.title)
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            Column(
                Modifier.fillMaxWidth().height(360.dp).dashedBorder(CragpackTheme.colors.ink, RadiusTokens.lg).padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterVertically),
            ) {
                icon?.let { CtIcon(it, null) }
                CtText(title, style = CragpackTheme.type.h3, textAlign = TextAlign.Center)
                text?.let { CtText(it, color = CragpackTheme.colors.ink2, textAlign = TextAlign.Center) }
                action?.invoke()
            }
        }
    }
}
