package app.cragpack.feature.crew

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.cragpack.data.AppGraph
import app.cragpack.designsystem.component.Avatar
import app.cragpack.designsystem.component.AvatarSize
import app.cragpack.designsystem.component.CtButton
import app.cragpack.designsystem.component.CtButtonStyle
import app.cragpack.designsystem.component.CtCard
import app.cragpack.designsystem.component.CtIconButton
import app.cragpack.designsystem.component.CtScreen
import app.cragpack.designsystem.component.CtText
import app.cragpack.designsystem.component.ListRow
import app.cragpack.designsystem.component.SyncStatusPill
import app.cragpack.designsystem.component.TopBar
import app.cragpack.designsystem.icon.LucideIcon
import app.cragpack.designsystem.theme.CragpackTheme
import app.cragpack.designsystem.tokens.SizeTokens
import app.cragpack.feature.LocalSyncStatus
import app.cragpack.feature.account.AccountPromptSheet
import app.cragpack.feature.account.AccountReason
import app.cragpack.feature.rememberShareText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface LinkState {
    data object Loading : LinkState
    data class Ready(val url: String) : LinkState
    data class Failed(val message: String) : LinkState
}

class CrewViewModel(private val graph: AppGraph, private val tripId: String) : ViewModel() {
    val crew = graph.trips.observeCrew(tripId).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Null until known, so the invite area doesn't flash the wrong state. */
    val signedIn = graph.account.observe().map { it != null }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val myId get() = graph.myId
    var link by mutableStateOf<LinkState>(LinkState.Loading)

    fun loadLink() = viewModelScope.launch {
        link = LinkState.Loading
        link = try {
            LinkState.Ready(graph.invites.link(tripId))
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            LinkState.Failed("Can't get the link right now. Check your connection and try again.")
        }
    }
}

/**
 * Crew and invite (07). Inviting needs an account (D4): without one, the invite area opens the account prompt,
 * and once signed in the link appears here.
 * ponytail: each climber's offline status (Saved / Saving 62%) arrives with the offline pack (Phase 3).
 */
@Composable
fun CrewScreen(vm: CrewViewModel, onBack: () -> Unit, onSettings: () -> Unit, onCreateAccount: () -> Unit) {
    val crew by vm.crew.collectAsStateWithLifecycle()
    val signedIn by vm.signedIn.collectAsStateWithLifecycle()
    var prompt by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(signedIn) { if (signedIn == true) vm.loadLink() }
    CtScreen(
        overlay = {
            AccountPromptSheet(AccountReason.Invite.takeIf { prompt }, onDismiss = { prompt = false }, onEmail = { prompt = false; onCreateAccount() })
        },
    ) { bottom ->
        LazyColumn(
            contentPadding = PaddingValues(start = SizeTokens.edge, end = SizeTokens.edge, bottom = bottom.calculateBottomPadding()),
            verticalArrangement = Arrangement.spacedBy(SizeTokens.gap),
        ) {
            item {
                TopBar(
                    leading = { CtIconButton(LucideIcon.ChevronLeft, "Back", onBack) },
                    center = { SyncStatusPill(LocalSyncStatus.current) },
                    trailing = { CtIconButton(LucideIcon.EllipsisVertical, "Trip settings", onSettings) },
                )
            }
            item { CtText("Crew", Modifier.padding(top = 24.dp, bottom = 8.dp, start = 4.dp), style = CragpackTheme.type.h1) }
            item {
                when (signedIn) {
                    null -> Unit
                    false -> CtButton("Invite your crew", { prompt = true }, Modifier.fillMaxWidth(), style = CtButtonStyle.Accent, icon = LucideIcon.Link)
                    true -> InviteLink(vm.link, onRetry = { vm.loadLink() })
                }
            }
            items(crew, key = { it.person.id }) { member ->
                val me = member.person.id == vm.myId
                ListRow(
                    member.person.label,
                    meta = listOfNotNull("You".takeIf { me && member.person.name.isNotBlank() }, "Organiser".takeIf { member.organiser }).joinToString(" · ").ifEmpty { null },
                    leading = { Avatar(member.person.name, member.person.colorIndex, size = AvatarSize.Xl) },
                )
            }
        }
    }
}

/** `.link` — the link in mono with Copy inside the pill, Share beside it. */
@Composable
private fun InviteLink(state: LinkState, onRetry: () -> Unit) {
    val clipboard = LocalClipboardManager.current
    val share = rememberShareText()
    val scope = rememberCoroutineScope()
    var copied by rememberSaveable { mutableStateOf(false) }
    when (state) {
        LinkState.Loading -> CtText("Getting the invite link…", Modifier.padding(start = 8.dp), color = CragpackTheme.colors.ink2)
        is LinkState.Failed -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(SizeTokens.gap)) {
            CtText(state.message, Modifier.weight(1f).padding(start = 8.dp), color = CragpackTheme.colors.ink2)
            CtButton("Try again", onRetry, style = CtButtonStyle.Ghost)
        }
        is LinkState.Ready -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(SizeTokens.gap)) {
            CtCard(Modifier.weight(1f).heightIn(min = SizeTokens.tap), radius = SizeTokens.tap) {
                Row(Modifier.padding(start = 18.dp, end = 6.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    CtText(state.url.substringAfter("://"), Modifier.weight(1f), style = CragpackTheme.type.mono, maxLines = 1)
                    CtButton(if (copied) "Copied" else "Copy", {
                        clipboard.setText(AnnotatedString(state.url))
                        copied = true
                        scope.launch { delay(2_000); copied = false }
                    }, style = CtButtonStyle.Accent, icon = LucideIcon.Copy)
                }
            }
            CtIconButton(LucideIcon.Share, "Share invite link", { share(state.url) })
        }
    }
}
