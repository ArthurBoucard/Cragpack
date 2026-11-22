package app.cragpack.feature.crew

import app.cragpack.designsystem.component.ActionRow
import app.cragpack.designsystem.component.ErrorRow
import app.cragpack.domain.trip.CrewMember
import app.cragpack.designsystem.component.CtButtonSize
import app.cragpack.designsystem.component.CtBottomSheet
import androidx.compose.foundation.layout.BoxScope
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleStartEffect
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
import app.cragpack.feature.explainFailure
import app.cragpack.feature.account.AccountPromptSheet
import app.cragpack.feature.account.AccountReason
import app.cragpack.feature.rememberShareText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
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
    val signedIn = graph.account.observeSignedIn().stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val myId get() = graph.myId
    var link by mutableStateOf<LinkState>(LinkState.Loading)
    private var loading: Job? = null

    /** Fetched each time Crew shows (signed in), so a link reset in Trip settings never lingers here. */
    fun loadLink() {
        loading?.cancel()
        loading = viewModelScope.launch {
            if (link !is LinkState.Ready) link = LinkState.Loading // keep the old link on screen while it refreshes
            link = fetchLink()
        }
    }

    /** The climber whose sheet (31) is open; it closes once their change has worked. */
    var climberId by mutableStateOf<String?>(null)
    var sheetError by mutableStateOf<String?>(null)
    var changing by mutableStateOf(false)

    fun makeOrganiser(userId: String) = change { graph.crew.makeOrganiser(tripId, userId) }
    fun remove(userId: String) = change { graph.crew.remove(tripId, userId) }

    fun closeSheet() {
        climberId = null
        sheetError = null
    }

    private fun change(action: suspend () -> Unit) = viewModelScope.launch {
        changing = true
        sheetError = null
        try {
            action()
            closeSheet()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            sheetError = explainFailure(e, "change the crew")
        } finally {
            changing = false
        }
    }

    private suspend fun fetchLink(): LinkState = try {
        LinkState.Ready(graph.invites.link(tripId))
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        LinkState.Failed(explainFailure(e, "get the link"))
    }
}

/**
 * Crew and invite (07). Inviting needs an account (D4): without one, + on the trip opens the account prompt
 * here straight away (and the invite area does too); once signed in, the link appears.
 * ponytail: each climber's offline status (Saved / Saving 62%) arrives with the offline pack (Phase 3).
 */
@Composable
fun CrewScreen(vm: CrewViewModel, invite: Boolean, onBack: () -> Unit, onSettings: () -> Unit, onCreateAccount: () -> Unit) {
    val crew by vm.crew.collectAsStateWithLifecycle()
    val signedIn by vm.signedIn.collectAsStateWithLifecycle()
    var prompt by rememberSaveable { mutableStateOf(false) }
    var asked by rememberSaveable { mutableStateOf(false) } // came from + : ask once, not again after Not now
    val iOrganise = crew.any { it.person.id == vm.myId && it.organiser }
    LifecycleStartEffect(signedIn) {
        if (signedIn == true) vm.loadLink()
        onStopOrDispose {}
    }
    LaunchedEffect(signedIn) {
        if (signedIn == false && invite && !asked) {
            asked = true
            prompt = true
        }
    }
    CtScreen(
        overlay = {
            AccountPromptSheet(AccountReason.Invite.takeIf { prompt }, onDismiss = { prompt = false }, onEmail = { prompt = false; onCreateAccount() })
            ClimberSheet(
                climber = crew.firstOrNull { it.person.id == vm.climberId },
                error = vm.sheetError,
                busy = vm.changing,
                onMakeOrganiser = { vm.makeOrganiser(it) },
                onRemove = { vm.remove(it) },
                onDismiss = vm::closeSheet,
            )
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
                    // Organisers manage the crew (31); there's nothing to do on yourself, or without an account.
                    onClick = { vm.climberId = member.person.id }.takeIf { iOrganise && !me && signedIn == true },
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
    var copied by remember { mutableStateOf(false) }
    when (state) {
        LinkState.Loading -> CtText("Getting the invite link…", Modifier.padding(start = 8.dp), color = CragpackTheme.colors.ink2)
        is LinkState.Failed -> ErrorRow(state.message) { CtButton("Try again", onRetry, style = CtButtonStyle.Ghost, size = CtButtonSize.Sm) }
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

/** Climber (31): an organiser makes them organiser or removes them. Removing is undone by inviting them again. */
@Composable
private fun BoxScope.ClimberSheet(
    climber: CrewMember?,
    error: String?,
    busy: Boolean,
    onMakeOrganiser: (String) -> Unit,
    onRemove: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    CtBottomSheet(
        climber != null,
        climber?.person?.label.orEmpty(),
        onDismiss,
        titleLeading = climber?.let { c -> { Avatar(c.person.name, c.person.colorIndex, size = AvatarSize.Xl) } },
    ) {
        val c = climber ?: return@CtBottomSheet
        // ponytail: their offline status (Saved / Saving 62%) arrives with the offline pack (Phase 3).
        if (!c.organiser) ActionRow("Make organiser", LucideIcon.ShieldCheck, { onMakeOrganiser(c.person.id) }, enabled = !busy)
        ActionRow("Remove from trip", LucideIcon.UserMinus, { onRemove(c.person.id) }, danger = true, enabled = !busy)
        error?.let { ErrorRow(it) }
    }
}
