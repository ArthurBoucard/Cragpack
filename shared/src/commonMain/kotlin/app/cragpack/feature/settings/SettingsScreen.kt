package app.cragpack.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import app.cragpack.designsystem.component.CtConfirmDialog
import app.cragpack.feature.account.AccountPromptSheet
import app.cragpack.feature.account.AccountReason
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.cragpack.data.AppGraph
import app.cragpack.designsystem.component.Avatar
import app.cragpack.designsystem.component.AvatarSize
import app.cragpack.designsystem.component.CtIcon
import app.cragpack.designsystem.component.CtIconButton
import app.cragpack.designsystem.component.CtScreen
import app.cragpack.designsystem.component.CtSegmented
import app.cragpack.designsystem.component.CtSwitchRow
import app.cragpack.designsystem.component.CtText
import app.cragpack.designsystem.component.ListRow
import app.cragpack.designsystem.component.TopBar
import app.cragpack.designsystem.icon.LucideIcon
import app.cragpack.designsystem.theme.CragpackTheme
import app.cragpack.designsystem.tokens.SizeTokens
import app.cragpack.domain.grade.GradeDisplay
import app.cragpack.domain.settings.Theme
import app.cragpack.domain.settings.formatBytes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(private val graph: AppGraph) : ViewModel() {
    val settings = graph.settings.observe().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), graph.settings.current())
    val storageBytes = graph.settings.observeStorageBytes().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0L)
    val account = graph.account.observe().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val accountsAvailable = graph.account.available

    fun setGradeDisplay(value: GradeDisplay) = write { graph.settings.setGradeDisplay(value) }
    fun setTheme(value: Theme) = write { graph.settings.setTheme(value) }
    fun setWifiOnly(value: Boolean) = write { graph.settings.setWifiOnly(value) }
    fun signOut() = viewModelScope.launch { graph.account.signOut() }

    private fun write(block: () -> Unit) = viewModelScope.launch(Dispatchers.IO) { block() }
}

private val gradeLabels = listOf(GradeDisplay.AsEntered to "As entered", GradeDisplay.Font to "Font", GradeDisplay.V to "V")

/**
 * Settings (27). Without an account the account row reads "Create account" and opens the account prompt (design gap, decided).
 * ponytail: Delete account arrives with sync (Phase 2); Storage management (remove offline copies) with the offline pack (Phase 3).
 */
@Composable
fun SettingsScreen(vm: SettingsViewModel, onBack: () -> Unit, onCreateAccount: () -> Unit) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val storage by vm.storageBytes.collectAsStateWithLifecycle()
    val account by vm.account.collectAsStateWithLifecycle()
    var prompt by rememberSaveable { mutableStateOf(false) }
    var confirmSignOut by rememberSaveable { mutableStateOf(false) }
    CtScreen(
        overlay = {
            AccountPromptSheet(AccountReason.Sync.takeIf { prompt }, onDismiss = { prompt = false }, onEmail = { prompt = false; onCreateAccount() })
            CtConfirmDialog(
                visible = confirmSignOut,
                title = "Sign out?",
                text = "Your trips leave this phone. They come back when you sign in again.",
                confirmLabel = "Sign out",
                onConfirm = { confirmSignOut = false; vm.signOut() },
                onDismiss = { confirmSignOut = false },
            )
        },
    ) { bottom ->
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = SizeTokens.edge).padding(bottom),
            verticalArrangement = Arrangement.spacedBy(SizeTokens.gap),
        ) {
            TopBar(
                leading = { CtIconButton(LucideIcon.ChevronLeft, "Back", onBack) },
                center = { CtText("Settings", style = CragpackTheme.type.title) },
            )
            val signedIn = account
            if (signedIn != null) {
                ListRow(
                    signedIn.name.ifBlank { signedIn.email },
                    Modifier.padding(top = 8.dp),
                    meta = signedIn.email,
                    leading = { Avatar(signedIn.name, 1, size = AvatarSize.Xl) },
                )
            } else {
                ListRow(
                    "Create account",
                    Modifier.padding(top = 8.dp),
                    meta = "Your trips stay on this phone",
                    onClick = { prompt = true }.takeIf { vm.accountsAvailable },
                    leading = { Avatar("", 1, size = AvatarSize.Xl) },
                    trailing = { CtIcon(LucideIcon.ChevronRight, null, Modifier.padding(end = 10.dp), tint = CragpackTheme.colors.ink2) },
                )
            }
            Section("Boulder grades")
            CtSegmented(gradeLabels.map { it.second }, gradeLabels.indexOfFirst { it.first == settings.gradeDisplay }, { vm.setGradeDisplay(gradeLabels[it].first) })
            Section("Theme")
            CtSegmented(Theme.entries.map { it.name }, settings.theme.ordinal, { vm.setTheme(Theme.entries[it]) })
            Section("Offline")
            CtSwitchRow("Wi-Fi only", settings.wifiOnly, vm::setWifiOnly)
            ListRow(
                "Storage",
                leading = {
                    Box(Modifier.size(SizeTokens.tapSm), contentAlignment = Alignment.Center) { CtIcon(LucideIcon.HardDrive, null) }
                },
                trailing = { CtText(formatBytes(storage), Modifier.padding(end = 10.dp), color = CragpackTheme.colors.ink2) },
            )
            if (account != null) {
                Section("Account")
                ListRow(
                    "Sign out",
                    onClick = { confirmSignOut = true },
                    leading = {
                        Box(Modifier.size(SizeTokens.tapSm), contentAlignment = Alignment.Center) { CtIcon(LucideIcon.LogOut, null) }
                    },
                )
            }
        }
    }
}

@Composable
private fun Section(text: String) = CtText(
    text,
    Modifier.padding(start = 8.dp, top = 12.dp),
    style = CragpackTheme.type.label.copy(fontSize = CragpackTheme.type.label.fontSize * 14 / 13),
    color = CragpackTheme.colors.ink2,
)
