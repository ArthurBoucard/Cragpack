package app.cragpack.feature.join

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
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
import app.cragpack.designsystem.component.EmptyState
import app.cragpack.designsystem.icon.LucideIcon
import app.cragpack.designsystem.theme.CragpackTheme
import app.cragpack.designsystem.tokens.RadiusTokens
import app.cragpack.designsystem.tokens.SizeTokens
import app.cragpack.domain.format.formatDateRange
import app.cragpack.domain.invite.InviteExpiredException
import app.cragpack.domain.invite.InvitePreview
import app.cragpack.feature.account.AccountPromptSheet
import app.cragpack.feature.account.AccountReason
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
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

    /** The account prompt is up: join as soon as there's an account. */
    var askingForAccount by mutableStateOf(false)

    /** Set once joined: the screen moves on. [where] is the trip, or null when it isn't on the phone yet. */
    var joined by mutableStateOf<Joined?>(null)

    class Joined(val where: String?)

    init {
        load()
        viewModelScope.launch {
            graph.account.observeSignedIn().collect { signedIn -> if (signedIn && askingForAccount) join() }
        }
    }

    /** The dock's Join trip: straight in with an account, through the account prompt without one (D4). */
    fun onJoin() = viewModelScope.launch {
        if (graph.account.observeSignedIn().first()) join() else askingForAccount = true
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

    private suspend fun join() {
        if (joining) return
        askingForAccount = false
        joining = true
        error = null
        try {
            val trip = graph.invites.join(token)
            joined = Joined(trip.takeIf { graph.trips.observeTrip(it).first() != null })
        } catch (e: CancellationException) {
            throw e
        } catch (_: InviteExpiredException) {
            state = JoinState.Expired
        } catch (_: Exception) {
            error = "Can't join right now. Check your connection and try again."
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
    var prompt by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(vm.askingForAccount) { prompt = vm.askingForAccount }
    LaunchedEffect(vm.joined) { vm.joined?.let { it.where?.let(onJoined) ?: onTrips() } }
    when (val state = vm.state) {
        JoinState.Expired -> Expired(onTrips)
        else -> CtScreen(
            statusBarStrip = false,
            dock = (state as? JoinState.Open)?.let { { CtaDock("Join trip", LucideIcon.ArrowRight, { vm.onJoin() }, enabled = !vm.joining) } },
            overlay = {
                AccountPromptSheet(AccountReason.Join.takeIf { prompt }, onDismiss = { vm.askingForAccount = false }, onEmail = {
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
                else -> Message("Opening the invite…")
            }
        }
    }
}

@Composable
private fun Invitation(p: InvitePreview, error: String?) {
    LightStatusBarIcons()
    Column(verticalArrangement = Arrangement.spacedBy(SizeTokens.gap)) {
        PhotoSurface(Modifier.fillMaxWidth().fillMaxHeight(0.6f), RoundedCornerShape(bottomStart = RadiusTokens.xl, bottomEnd = RadiusTokens.xl)) {
            CtText("Cragpack", Modifier.statusBarsPadding().padding(SizeTokens.edge), style = CragpackTheme.type.title)
            Column(Modifier.align(Alignment.BottomStart).padding(SizeTokens.edge), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    val (inviter, color) = p.inviter
                    Avatar(inviter, color)
                    CtText(if (inviter.isBlank()) "You're invited" else "$inviter invited you")
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

/** The wordmark, then a message in the middle (Invite expired and the in-between states). */
@Composable
private fun Message(title: String, text: String? = null, icon: LucideIcon? = null, action: (@Composable () -> Unit)? = null) {
    Column(Modifier.fillMaxSize().statusBarsPadding().padding(SizeTokens.edge)) {
        CtText("Cragpack", style = CragpackTheme.type.title)
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            EmptyState(title, Modifier.fillMaxWidth().fillMaxHeight(0.55f), text = text, icon = icon, action = action)
        }
    }
}
