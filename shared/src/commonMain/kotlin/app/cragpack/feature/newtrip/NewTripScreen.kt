package app.cragpack.feature.newtrip

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
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
import app.cragpack.data.AppGraph
import app.cragpack.data.MediaFiles
import app.cragpack.designsystem.component.CtButton
import app.cragpack.designsystem.component.CtCard
import app.cragpack.designsystem.component.PhotoSurface
import app.cragpack.designsystem.tokens.RadiusTokens
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.Alignment
import androidx.lifecycle.viewModelScope
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.dialogs.FileKitType
import io.github.vinceglb.filekit.dialogs.compose.rememberFilePickerLauncher
import app.cragpack.designsystem.component.CtFieldButton
import app.cragpack.designsystem.component.CtIconButton
import app.cragpack.designsystem.component.CtScreen
import app.cragpack.designsystem.component.CtText
import app.cragpack.designsystem.component.CtTextField
import app.cragpack.designsystem.component.CtaDock
import app.cragpack.designsystem.component.DateRangeSheet
import app.cragpack.designsystem.component.TopBar
import app.cragpack.designsystem.icon.LucideIcon
import app.cragpack.designsystem.theme.CragpackTheme
import app.cragpack.designsystem.tokens.SizeTokens
import app.cragpack.domain.format.formatDayShort
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.datetime.LocalDate

data class NewTripForm(
    val name: String = "",
    val crag: String = "",
    val start: LocalDate? = null,
    val end: LocalDate? = null,
    /** Already copied into app storage so it shows at once; recorded with the trip on create. */
    val cover: MediaFiles.Imported? = null,
) {
    val canCreate get() = name.isNotBlank() && start != null && end != null
}

class NewTripViewModel(private val graph: AppGraph) : ViewModel() {
    var form by mutableStateOf(NewTripForm())
    val today: LocalDate = graph.today()

    // ponytail: a cover picked and then abandoned (form closed) leaves its copy in app storage; clean up with Storage management.
    fun pickCover(file: PlatformFile) = viewModelScope.launch {
        runCatching { withContext(Dispatchers.IO) { MediaFiles.import(file, isVideo = false) } }.onSuccess { form = form.copy(cover = it) }
    }

    /** Returns the new trip's id. */
    suspend fun create(): String = withContext(Dispatchers.IO) {
        val f = form
        graph.trips.create(f.name, f.crag, f.start!!, f.end!!).also { id ->
            f.cover?.let { graph.trips.setCover(id, it.id, it.mime, it.bytes, it.localPath) }
        }
    }
}

/** New trip (09). */
@Composable
fun NewTripScreen(vm: NewTripViewModel, onClose: () -> Unit, onCreated: (String) -> Unit) {
    var datesOpen by rememberSaveable { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val form = vm.form
    val coverPicker = rememberFilePickerLauncher(FileKitType.Image) { file -> if (file != null) vm.pickCover(file) }
    CtScreen(
        dock = {
            CtaDock("Create trip", LucideIcon.Check, enabled = form.canCreate, onClick = {
                scope.launch { onCreated(vm.create()) }
            })
        },
        overlay = {
            DateRangeSheet(
                visible = datesOpen,
                start = form.start,
                end = form.end,
                today = vm.today,
                onDone = { s, e -> vm.form = form.copy(start = s, end = e); datesOpen = false },
                onDismiss = { datesOpen = false },
            )
        },
    ) { bottom ->
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).imePadding().padding(horizontal = SizeTokens.edge),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            TopBar(
                leading = { CtIconButton(LucideIcon.X, "Close", onClose) },
                center = { CtText("New trip", style = CragpackTheme.type.title) },
            )
            CoverCard(form.cover?.localPath?.let(MediaFiles::absolute)) { coverPicker.launch() }
            val words = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next)
            CtTextField(form.name, { vm.form = form.copy(name = it) }, "Name", placeholder = "e.g. Font Easter", keyboardOptions = words)
            CtTextField(form.crag, { vm.form = form.copy(crag = it) }, "Crag", placeholder = "e.g. Fontainebleau", keyboardOptions = words)
            Row(horizontalArrangement = Arrangement.spacedBy(SizeTokens.gap)) {
                CtFieldButton("Start", form.start?.let(::formatDayShort), "Pick dates", { datesOpen = true }, Modifier.weight(1f))
                CtFieldButton("End", form.end?.let(::formatDayShort), "–", { datesOpen = true }, Modifier.weight(1f))
            }
            Spacer(Modifier.height(bottom.calculateBottomPadding()))
        }
    }
}

/** `.cover` — 200 high: an empty card with "Cover", or the picked photo with "Change". */
@Composable
private fun CoverCard(imagePath: String?, onPick: () -> Unit) {
    val modifier = Modifier.fillMaxWidth().height(200.dp)
    val button: @Composable BoxScope.() -> Unit = {
        CtButton(if (imagePath == null) "Cover" else "Change", onPick, Modifier.align(Alignment.Center), icon = LucideIcon.Image)
    }
    if (imagePath == null) CtCard(modifier, radius = RadiusTokens.xl, content = button)
    else PhotoSurface(modifier, imagePath = imagePath, content = button)
}
