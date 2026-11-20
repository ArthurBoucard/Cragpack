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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import app.cragpack.data.youtubeThumbnail
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
import app.cragpack.designsystem.component.CtConfirmDialog
import app.cragpack.designsystem.component.CtIconButton
import app.cragpack.designsystem.component.CtOption
import app.cragpack.designsystem.component.CtScreen
import app.cragpack.designsystem.component.CtText
import app.cragpack.designsystem.component.CtTextField
import app.cragpack.designsystem.component.GradeChip
import app.cragpack.designsystem.component.GradeChipSize
import app.cragpack.designsystem.component.MediaItem
import app.cragpack.designsystem.component.MediaViewer
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
import app.cragpack.feature.text
import app.cragpack.domain.objective.BetaType
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.dialogs.FileKitType
import io.github.vinceglb.filekit.dialogs.compose.rememberFilePickerLauncher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ObjectiveViewModel(private val graph: AppGraph, private val id: String) : ViewModel() {
    /** null while loading, and once deleted. */
    val state = graph.objectives.observeObjective(id).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val logs = graph.logs.observeForObjective(id).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val myId get() = graph.myId

    /** Each video's first frame (absolute path) by beta id, made the first time the video is shown. */
    val frames = state.map { detail ->
        detail?.beta.orEmpty().filter { it.type == BetaType.Video }
            .mapNotNull { b -> b.localPath?.let { MediaFiles.videoFrame(it) }?.let { b.id to it } }.toMap()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

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
    fun editNote(betaId: String, text: String) = write { editNote(betaId, text) }
    fun deleteBeta(betaId: String) = write { deleteBeta(betaId) }
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

private enum class Sheet { AddBeta, Note, DeleteNote, More }

/** Boulder / route detail (05) and the Add beta sheet (20). */
@Composable
fun ObjectiveScreen(vm: ObjectiveViewModel, onBack: () -> Unit, onEdit: () -> Unit) {
    val ui by vm.state.collectAsStateWithLifecycle()
    val logs by vm.logs.collectAsStateWithLifecycle()
    val frames by vm.frames.collectAsStateWithLifecycle()
    var sheet by rememberSaveable { mutableStateOf<Sheet?>(null) }
    /** The media viewer's page while it's open. */
    var viewing by rememberSaveable { mutableStateOf<Int?>(null) }
    /** The photo or video the Delete dialog asks about. */
    var deleting by remember { mutableStateOf<MediaItem?>(null) }
    var link by rememberSaveable { mutableStateOf("") }
    var note by rememberSaveable { mutableStateOf("") }
    /** The note being edited in the Note sheet; null when adding one. */
    var editingNote by rememberSaveable { mutableStateOf<String?>(null) }
    val media = ui?.beta.orEmpty().filter { it.type != BetaType.Note }
    val mediaItems = media.map { it.toMediaItem(frames[it.id], mine = it.author.id == vm.myId) }
    var pickingType by rememberSaveable { mutableStateOf(BetaType.Photo) }
    val images = rememberFilePickerLauncher(FileKitType.Image) { file -> if (file != null) vm.addFile(file, pickingType) }
    val videos = rememberFilePickerLauncher(FileKitType.Video) { file -> if (file != null) vm.addFile(file, BetaType.Video) }

    val mine = logs.filter { it.person.id == vm.myId }.map { it.style }
    val attempts = mine.count { it == LogStyle.Attempt }
    CtScreen(
        statusBarStrip = false,
        dock = {
            // Hidden under the media viewer, so nothing of it shows or reaches a screen reader there.
            if (ui != null && viewing == null) LogDock(
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
                    CtOption("Note", LucideIcon.NotebookPen, { editingNote = null; note = ""; sheet = Sheet.Note }, Modifier.weight(1f))
                }
            }
            val editing = editingNote
            CtBottomSheet(sheet == Sheet.Note, if (editing == null) "Add note" else "Edit note", { sheet = null }) {
                CtTextField(note, { note = it }, "Note", placeholder = "e.g. Left heel hook, then cross to the sloper.", area = true)
                if (editing == null) {
                    CtButton("Add note", { vm.addNote(note); note = ""; sheet = null }, size = CtButtonSize.Wide, enabled = note.isNotBlank())
                } else {
                    CtButton("Save note", { vm.editNote(editing, note); note = ""; sheet = null }, size = CtButtonSize.Wide, enabled = note.isNotBlank())
                    CtButton("Delete note", { sheet = Sheet.DeleteNote }, style = CtButtonStyle.Danger, size = CtButtonSize.Wide)
                }
            }
            // A note has no Undo, so deleting it asks first (design rule 11).
            CtConfirmDialog(
                sheet == Sheet.DeleteNote && editing != null, "Delete this note?", "It's gone for everyone on the trip.", "Delete",
                onConfirm = { editing?.let(vm::deleteBeta); note = ""; sheet = null },
                onDismiss = { sheet = Sheet.Note },
            )
            CtBottomSheet(sheet == Sheet.More, ui?.objective?.name.orEmpty(), { sheet = null }) {
                CtButton("Edit", { sheet = null; onEdit() }, style = CtButtonStyle.Ghost, size = CtButtonSize.Wide)
                CtButton("Delete project", { sheet = null; vm.delete(); onBack() }, style = CtButtonStyle.Danger, size = CtButtonSize.Wide)
            }
            MediaViewer(mediaItems, viewing, onDismiss = { viewing = null }, onDelete = { deleting = it })
            // After the viewer so it shows on top of it. No Undo here either, so it asks first.
            val d = deleting
            CtConfirmDialog(
                d != null, "Delete this ${if (d?.link != null) "YouTube link" else d?.label?.lowercase()}?", "It's gone for everyone on the trip.", "Delete",
                onConfirm = {
                    if (d != null) vm.deleteBeta(d.id)
                    if (media.size <= 1) viewing = null
                    deleting = null
                },
                onDismiss = { deleting = null },
            )
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
                        GradeChip(o.grade.text(), o.grade.band, size = GradeChipSize.Xl)
                        CtText(o.name, style = CragpackTheme.type.titleXl, maxLines = 2)
                    }
                    // ponytail: becomes a Navigate chip once objectives can hold a location pin.
                    if (o.sector != null) CtChip(o.sector.name, selected = false, onClick = null, Modifier.padding(top = 16.dp), icon = LucideIcon.MapPin)
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            // The count says how much of the strip is off screen.
            Row(Modifier.padding(horizontal = SizeTokens.edge + 4.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                CtText("Beta", style = CragpackTheme.type.h3)
                if (media.isNotEmpty()) CtText("${media.size}", style = CragpackTheme.type.h3, color = CragpackTheme.colors.ink2)
            }
            LazyRow(contentPadding = PaddingValues(horizontal = SizeTokens.edge), horizontalArrangement = Arrangement.spacedBy(SizeTokens.gap)) {
                item { AddBetaThumb({ sheet = Sheet.AddBeta }) }
                itemsIndexed(media, key = { _, b -> b.id }) { i, b ->
                    val item = mediaItems[i]
                    BetaThumb(
                        item.label,
                        onClick = { viewing = i },
                        imagePath = item.image,
                        icon = if (b.type == BetaType.Video || b.type == BetaType.Youtube) LucideIcon.Play else null,
                        onlineOnly = b.type == BetaType.Youtube,
                    )
                }
            }
            }
            Column(Modifier.padding(horizontal = SizeTokens.edge), verticalArrangement = Arrangement.spacedBy(SizeTokens.gap)) {
                detail.beta.filter { it.type == BetaType.Note }.forEach { n ->
                    NoteCard(n, onEdit = if (n.author.id == vm.myId) ({ editingNote = n.id; note = n.text.orEmpty(); sheet = Sheet.Note }) else null)
                }
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

/** A beta item as the media viewer and the strip show it. [frame] is a video's first frame; [mine] makes it deletable. */
private fun Beta.toMediaItem(frame: String?, mine: Boolean): MediaItem {
    val path = localPath?.let(MediaFiles::absolute)
    val by = author.name.ifBlank { null }
    return when (type) {
        BetaType.Video -> MediaItem(id, "Video", frame, video = path, author = by, authorColor = author.colorIndex, deletable = mine)
        BetaType.Youtube -> {
            val u = url.orEmpty()
            MediaItem(id, "YouTube", youtubeThumbnail(u), link = if (u.startsWith("http")) u else "https://$u", author = by, authorColor = author.colorIndex, deletable = mine)
        }
        else -> MediaItem(id, if (type == BetaType.Topo) "Topo" else "Photo", path, author = by, authorColor = author.colorIndex, deletable = mine)
    }
}

/** A climber's note. Your own notes open the Note sheet to edit or delete them. */
@Composable
private fun NoteCard(b: Beta, onEdit: (() -> Unit)?) = CtCard(Modifier.fillMaxWidth(), radius = RadiusTokens.lg, onClick = onEdit) {
    Row(Modifier.padding(start = 16.dp, end = 12.dp, top = 14.dp, bottom = 14.dp), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
        Avatar(b.author.name, b.author.colorIndex, size = AvatarSize.Lg)
        CtText(b.text.orEmpty(), Modifier.weight(1f))
        if (onEdit != null) CtIcon(LucideIcon.EllipsisVertical, "Edit note", size = 20.dp, tint = CragpackTheme.colors.ink2)
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
