package app.cragpack.feature.tripsettings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.cragpack.data.AppGraph
import app.cragpack.data.MediaFiles
import app.cragpack.designsystem.component.CtButton
import app.cragpack.designsystem.component.CtButtonSize
import app.cragpack.designsystem.component.CtButtonStyle
import app.cragpack.designsystem.component.CtConfirmDialog
import app.cragpack.designsystem.component.CtFieldButton
import app.cragpack.designsystem.component.CtIconButton
import app.cragpack.designsystem.component.CtScreen
import app.cragpack.designsystem.component.CtText
import app.cragpack.designsystem.component.CtTextField
import app.cragpack.designsystem.component.DateRangeSheet
import app.cragpack.designsystem.component.ListRow
import app.cragpack.designsystem.component.PhotoSurface
import app.cragpack.designsystem.component.TopBar
import app.cragpack.designsystem.icon.LucideIcon
import app.cragpack.designsystem.theme.CragpackTheme
import app.cragpack.designsystem.tokens.SizeTokens
import app.cragpack.domain.format.formatDayShort
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.dialogs.FileKitType
import io.github.vinceglb.filekit.dialogs.compose.rememberFilePickerLauncher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.datetime.LocalDate

class TripSettingsViewModel(private val graph: AppGraph, private val tripId: String) : ViewModel() {
    val today = graph.today()
    /** Live trip for the cover, dates and crew size; null while loading or once deleted. */
    val detail = graph.trips.observeTrip(tripId).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    // The text fields own their text while typing; each change is saved (screen 29: "changes save automatically").
    var name by mutableStateOf("")
    var crag by mutableStateOf("")

    init {
        viewModelScope.launch {
            graph.trips.observeTrip(tripId).first()?.trip?.let { name = it.name; crag = it.crag }
        }
    }

    fun editName(value: String) {
        name = value
        // A blank name isn't saved: the last real name stays until a new one is typed.
        if (value.isNotBlank()) write { graph.trips.rename(tripId, value, crag) }
    }

    fun editCrag(value: String) {
        crag = value
        if (name.isNotBlank()) write { graph.trips.rename(tripId, name, value) }
    }

    fun changeDates(start: LocalDate, end: LocalDate) = write { graph.trips.changeDates(tripId, start, end) }

    fun setCover(file: PlatformFile) = viewModelScope.launch(Dispatchers.IO) {
        // ponytail: a failed copy is dropped silently (the original stays in the gallery), as for beta.
        runCatching { MediaFiles.import(file, isVideo = false) }.onSuccess {
            graph.trips.setCover(tripId, it.id, it.mime, it.bytes, it.localPath)
        }
    }

    suspend fun delete() = withContext(Dispatchers.IO) { graph.trips.delete(tripId) }

    // One write at a time, in order: typing "Font" must never save "Fon" last.
    private val writes = Dispatchers.IO.limitedParallelism(1)
    private fun write(block: () -> Unit) = viewModelScope.launch(writes) { block() }
}

/**
 * Trip settings (29) and Confirm delete (30).
 * ponytail: the invite link (Reset) and Leave trip arrive with accounts and sharing (Phase 2).
 */
@Composable
fun TripSettingsScreen(vm: TripSettingsViewModel, onBack: () -> Unit, onDeleted: () -> Unit) {
    val detail by vm.detail.collectAsStateWithLifecycle()
    var datesOpen by rememberSaveable { mutableStateOf(false) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val cover = rememberFilePickerLauncher(FileKitType.Image) { file -> if (file != null) vm.setCover(file) }
    val d = detail ?: run { CtScreen {}; return }
    val trip = d.trip

    CtScreen(
        overlay = {
            DateRangeSheet(
                visible = datesOpen, start = trip.start, end = trip.end, today = vm.today,
                onDone = { s, e -> vm.changeDates(s, e); datesOpen = false },
                onDismiss = { datesOpen = false },
            )
            CtConfirmDialog(
                visible = confirmDelete,
                title = "Delete ${trip.name}?",
                text = if (d.crew.size > 1) "The plan, beta and logs go for all ${d.crew.size} climbers. Sends stay in each personal logbook."
                else "The plan, beta and logs go. Your sends stay in your logbook.",
                confirmLabel = "Delete",
                onConfirm = { confirmDelete = false; scope.launch { vm.delete(); onDeleted() } },
                onDismiss = { confirmDelete = false },
            )
        },
    ) { bottom ->
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).imePadding().padding(horizontal = SizeTokens.edge),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            TopBar(
                leading = { CtIconButton(LucideIcon.ChevronLeft, "Back", onBack) },
                center = { CtText("Trip settings", style = CragpackTheme.type.title) },
            )
            ListRow(
                "Cover",
                leading = { PhotoSurface(Modifier.size(54.dp), CircleShape, imagePath = trip.coverPath?.let(MediaFiles::absolute)) },
                trailing = { CtButton("Change", { cover.launch() }, style = CtButtonStyle.Ghost, icon = LucideIcon.Image) },
            )
            val words = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next)
            CtTextField(vm.name, vm::editName, "Name", placeholder = "e.g. Font Easter", keyboardOptions = words)
            CtTextField(vm.crag, vm::editCrag, "Crag", placeholder = "e.g. Fontainebleau", keyboardOptions = words)
            Row(horizontalArrangement = Arrangement.spacedBy(SizeTokens.gap)) {
                CtFieldButton("Start", formatDayShort(trip.start), "", { datesOpen = true }, Modifier.weight(1f))
                CtFieldButton("End", formatDayShort(trip.end), "", { datesOpen = true }, Modifier.weight(1f))
            }
            CtButton(
                "Delete trip", { confirmDelete = true },
                style = CtButtonStyle.Danger, size = CtButtonSize.Wide, icon = LucideIcon.Trash2,
            )
            Spacer(Modifier.height(bottom.calculateBottomPadding()))
        }
    }
}
