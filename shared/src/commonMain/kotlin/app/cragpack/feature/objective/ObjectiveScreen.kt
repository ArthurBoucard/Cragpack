package app.cragpack.feature.objective

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.cragpack.data.AppGraph
import app.cragpack.data.Beta
import app.cragpack.data.LogUndo
import app.cragpack.designsystem.component.CtIcon
import app.cragpack.designsystem.component.CtToast
import app.cragpack.designsystem.component.LogDock
import app.cragpack.domain.log.LogStyle
import androidx.compose.ui.text.font.FontWeight
import kotlinx.coroutines.withContext
import app.cragpack.data.MediaFiles
import app.cragpack.data.isYoutubeLink
import app.cragpack.designsystem.component.AddBetaThumb
import app.cragpack.designsystem.component.Avatar
import app.cragpack.designsystem.component.AvatarSize
import app.cragpack.designsystem.component.AvatarStack
import app.cragpack.designsystem.component.BetaThumb
import app.cragpack.designsystem.component.CtBottomSheet
import app.cragpack.designsystem.component.CtButton
import app.cragpack.designsystem.component.CtButtonSize
import app.cragpack.designsystem.component.CtButtonStyle
import app.cragpack.designsystem.component.CtCard
import app.cragpack.designsystem.component.CtChip
import app.cragpack.designsystem.component.CtIconButton
import app.cragpack.designsystem.component.CtOption
import app.cragpack.designsystem.component.CtScreen
import app.cragpack.designsystem.component.CtText
import app.cragpack.designsystem.component.CtTextField
import app.cragpack.designsystem.component.GradeChip
import app.cragpack.designsystem.component.GradeChipSize
import app.cragpack.designsystem.component.ImageViewer
import app.cragpack.designsystem.component.LightStatusBarIcons
import app.cragpack.designsystem.component.PhotoSurface
import app.cragpack.designsystem.component.StarToggle
import app.cragpack.designsystem.component.SyncStatus
import app.cragpack.designsystem.component.SyncStatusPill
import app.cragpack.designsystem.component.TopBar
import app.cragpack.designsystem.icon.LucideIcon
import app.cragpack.designsystem.theme.CragpackTheme
import app.cragpack.designsystem.tokens.RadiusTokens
import app.cragpack.designsystem.tokens.SizeTokens
import app.cragpack.domain.grade.GradeDisplay
import app.cragpack.domain.objective.BetaType
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.dialogs.FileKitType
import io.github.vinceglb.filekit.dialogs.compose.rememberFilePickerLauncher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ObjectiveViewModel(private val graph: AppGraph, private val id: String) : ViewModel() {
    /** null while loading, and once deleted. */
    val state = graph.objectives.observeObjective(id).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val logs = graph.logs.observeForObjective(id).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val myId = graph.myId

    /** The Undo toast's message after a log action. */
    var toast by mutableStateOf<String?>(null)
    private var lastLog: LogUndo? = null

    fun log(style: LogStyle) = viewModelScope.launch {
        val name = state.value?.objective?.name.orEmpty()
        val undo = withContext(Dispatchers.IO) { graph.logs.log(id, style) }
        lastLog = undo
        toast = if (undo.added == null) "Removed ${style.label.lowercase()} · $name" else "${style.label} · $name"
    }

    fun undoLog() {
        val undo = lastLog ?: return
        lastLog = null
        toast = null
        viewModelScope.launch(Dispatchers.IO) { graph.logs.undo(undo) }
    }

    fun setStarred(on: Boolean) = write { setStarred(id, on) }
    fun delete() = write { delete(id) }
    fun addNote(text: String) = write { addNote(id, text) }
    fun addYoutube(url: String) = write { addYoutube(id, url) }

    fun addFile(file: PlatformFile, type: BetaType) = viewModelScope.launch(Dispatchers.IO) {
        // ponytail: a failed copy is dropped silently (the original stays in the gallery); show it once the upload queue (Phase 3) has an error row.
        runCatching { MediaFiles.import(file, isVideo = type == BetaType.Video) }.onSuccess {
            graph.objectives.addMedia(id, type, it.id, it.mime, it.bytes, it.localPath)
        }
    }

    private fun write(block: app.cragpack.data.ObjectiveRepository.() -> Unit) =
        viewModelScope.launch(Dispatchers.IO) { graph.objectives.block() }
}

private enum class Sheet { AddBeta, Note, More }

/** Boulder / route detail (05) and the Add beta sheet (20). */
@Composable
fun ObjectiveScreen(vm: ObjectiveViewModel, onBack: () -> Unit, onEdit: () -> Unit) {
    val ui by vm.state.collectAsStateWithLifecycle()
    val logs by vm.logs.collectAsStateWithLifecycle()
    var sheet by rememberSaveable { mutableStateOf<Sheet?>(null) }
    var viewing by rememberSaveable { mutableStateOf<String?>(null) }
    var link by rememberSaveable { mutableStateOf("") }
    var note by rememberSaveable { mutableStateOf("") }
    var pickingType by rememberSaveable { mutableStateOf(BetaType.Photo) }
    val images = rememberFilePickerLauncher(FileKitType.Image) { file -> if (file != null) vm.addFile(file, pickingType) }
    val videos = rememberFilePickerLauncher(FileKitType.Video) { file -> if (file != null) vm.addFile(file, BetaType.Video) }
    val uri = LocalUriHandler.current

    val mine = logs.filter { it.person.id == vm.myId }.map { it.style }
    val attempts = mine.count { it == LogStyle.Attempt }
    CtScreen(
        statusBarStrip = false,
        dock = {
            if (ui != null) LogDock(
                listOf(if (attempts > 0) "Attempt · $attempts" else "Attempt", "Sent", "Flash"),
                on = when { LogStyle.Flash in mine -> 2; LogStyle.Sent in mine -> 1; else -> null },
                onClick = { vm.log(LogStyle.entries[it]) },
            )
        },
        overlay = {
            CtToast(vm.toast, "Undo", vm::undoLog) { vm.toast = null }
            CtBottomSheet(sheet == Sheet.AddBeta, "Add beta", { sheet = null }) {
                CtTextField(
                    link, { link = it }, "YouTube link · plays online only", placeholder = "youtu.be/…", mono = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    trailing = {
                        CtButton("Add", { vm.addYoutube(link); link = ""; sheet = null }, style = CtButtonStyle.Accent, size = CtButtonSize.Sm, enabled = isYoutubeLink(link))
                    },
                )
                Row(horizontalArrangement = Arrangement.spacedBy(SizeTokens.gap)) {
                    CtOption("Video", LucideIcon.Video, { sheet = null; videos.launch() }, Modifier.weight(1f))
                    CtOption("Photo", LucideIcon.Image, { sheet = null; pickingType = BetaType.Photo; images.launch() }, Modifier.weight(1f))
                    CtOption("Topo", LucideIcon.FileText, { sheet = null; pickingType = BetaType.Topo; images.launch() }, Modifier.weight(1f))
                    CtOption("Note", LucideIcon.NotebookPen, { sheet = Sheet.Note }, Modifier.weight(1f))
                }
            }
            CtBottomSheet(sheet == Sheet.Note, "Add note", { sheet = null }) {
                CtTextField(note, { note = it }, "Note", placeholder = "e.g. Left heel hook, then cross to the sloper.", area = true)
                CtButton("Add note", { vm.addNote(note); note = ""; sheet = null }, size = CtButtonSize.Wide, enabled = note.isNotBlank())
            }
            CtBottomSheet(sheet == Sheet.More, ui?.objective?.name.orEmpty(), { sheet = null }) {
                CtButton("Edit", { sheet = null; onEdit() }, style = CtButtonStyle.Ghost, size = CtButtonSize.Wide)
                CtButton("Delete objective", { sheet = null; vm.delete(); onBack() }, style = CtButtonStyle.Danger, size = CtButtonSize.Wide)
            }
            ImageViewer(viewing?.let(MediaFiles::absolute)) { viewing = null }
        },
    ) { bottom ->
        val detail = ui ?: return@CtScreen
        val o = detail.objective
        LightStatusBarIcons()
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(SizeTokens.gap * 2)) {
            val cover = detail.beta.firstOrNull { it.type == BetaType.Photo }?.localPath
            PhotoSurface(
                Modifier.fillMaxWidth(),
                RoundedCornerShape(bottomStart = RadiusTokens.xl, bottomEnd = RadiusTokens.xl),
                imagePath = cover?.let(MediaFiles::absolute),
            ) {
                Column(Modifier.padding(start = SizeTokens.edge, end = SizeTokens.edge, bottom = SizeTokens.edge)) {
                    TopBar(
                        leading = { CtIconButton(LucideIcon.ChevronLeft, "Back", onBack) },
                        center = { SyncStatusPill(SyncStatus.OnThisPhone) },
                        trailing = { CtIconButton(LucideIcon.EllipsisVertical, "More", { sheet = Sheet.More }) },
                    )
                    Spacer(Modifier.height(140.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        GradeChip(o.grade.display(GradeDisplay.AsEntered), o.grade.band, size = GradeChipSize.Xl)
                        CtText(o.name, style = CragpackTheme.type.titleXl, maxLines = 2)
                    }
                    // ponytail: becomes a Navigate chip once objectives can hold a location pin.
                    if (o.sector != null) CtChip(o.sector.name, selected = false, onClick = null, Modifier.padding(top = 16.dp), icon = LucideIcon.MapPin)
                }
            }
            LazyRow(contentPadding = PaddingValues(horizontal = SizeTokens.edge), horizontalArrangement = Arrangement.spacedBy(SizeTokens.gap)) {
                item { AddBetaThumb({ sheet = Sheet.AddBeta }) }
                items(detail.beta.filter { it.type != BetaType.Note }, key = { it.id }) { b -> Thumb(b, onView = { viewing = it }, onLink = { uri.openUri(it) }) }
            }
            Column(Modifier.padding(horizontal = SizeTokens.edge), verticalArrangement = Arrangement.spacedBy(SizeTokens.gap)) {
                detail.beta.filter { it.type == BetaType.Note }.forEach { NoteCard(it) }
                Row(horizontalArrangement = Arrangement.spacedBy(SizeTokens.gap)) {
                    PeopleTile("Want", o.wanters.map { it.name to it.colorIndex }, Modifier.weight(1f)) {
                        StarToggle(o.starred, { vm.setStarred(!o.starred) }, if (o.starred) "Don't want it" else "Want it")
                    }
                    // Each climber's best: a flash beats a sent.
                    val senders = logs.filter { it.style.isSend }.groupBy { it.person.id }.values.map { l -> l.maxBy { it.style.ordinal } }
                    PeopleTile(
                        "Sent", senders.map { it.person.name to it.person.colorIndex }, Modifier.weight(1f),
                        tick = senders.singleOrNull()?.style?.label,
                    )
                }
            }
            Spacer(Modifier.height(bottom.calculateBottomPadding()))
        }
    }
}

@Composable
private fun Thumb(b: Beta, onView: (String) -> Unit, onLink: (String) -> Unit) = when (b.type) {
    BetaType.Photo, BetaType.Topo -> {
        val path = b.localPath
        BetaThumb(
            if (b.type == BetaType.Topo) "Topo" else "Photo",
            onClick = path?.let { { onView(it) } },
            imagePath = path?.let(MediaFiles::absolute),
        )
    }
    // ponytail: videos play in the media viewer (21, Phase 3); until then the thumb only shows they're there.
    BetaType.Video -> BetaThumb("Video", onClick = null, icon = LucideIcon.Play)
    BetaType.Youtube -> {
        val url = b.url.orEmpty()
        BetaThumb("YouTube", onClick = { onLink(if (url.startsWith("http")) url else "https://$url") }, icon = LucideIcon.Play, onlineOnly = true)
    }
    BetaType.Note -> Unit
}

@Composable
private fun NoteCard(b: Beta) = CtCard(Modifier.fillMaxWidth(), radius = RadiusTokens.lg) {
    Row(Modifier.padding(horizontal = 16.dp, vertical = 14.dp), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
        Avatar(b.author.name, b.author.colorIndex, size = AvatarSize.Lg)
        CtText(b.text.orEmpty(), Modifier.weight(1f))
    }
}

@Composable
private fun PeopleTile(
    label: String,
    people: List<Pair<String, Int>>,
    modifier: Modifier,
    tick: String? = null,
    action: (@Composable () -> Unit)? = null,
) =
    CtCard(modifier) {
        Column(
            Modifier.fillMaxWidth().heightIn(min = 104.dp).padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            CtText(label, color = CragpackTheme.colors.ink2, maxLines = 1)
            if (people.isEmpty()) CtText("Nobody yet", color = CragpackTheme.colors.ink2, maxLines = 1)
            else Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                AvatarStack(people, size = AvatarSize.Lg)
                if (tick != null) {
                    CtIcon(LucideIcon.Check, null, size = 18.dp, tint = CragpackTheme.colors.accentInk)
                    CtText(tick, style = CragpackTheme.type.body.copy(fontWeight = FontWeight.Bold), color = CragpackTheme.colors.accentInk)
                }
            }
        }
        if (action != null) Box(Modifier.align(Alignment.TopEnd).padding(10.dp)) { action() }
    }
