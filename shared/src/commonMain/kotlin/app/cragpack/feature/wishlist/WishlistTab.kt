package app.cragpack.feature.wishlist

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.cragpack.data.AppGraph
import app.cragpack.data.Objective
import app.cragpack.designsystem.component.AvatarSize
import app.cragpack.designsystem.component.AvatarStack
import app.cragpack.designsystem.component.CtBottomSheet
import app.cragpack.designsystem.component.CtButton
import app.cragpack.designsystem.component.CtChip
import app.cragpack.designsystem.component.CtIcon
import app.cragpack.designsystem.component.CtIconButton
import app.cragpack.designsystem.component.CtIconButtonStyle
import app.cragpack.designsystem.component.CtRadioRow
import app.cragpack.designsystem.component.CtText
import app.cragpack.designsystem.component.EmptyState
import app.cragpack.designsystem.component.GradeChip
import app.cragpack.designsystem.component.ListRow
import app.cragpack.designsystem.component.StarToggle
import app.cragpack.designsystem.component.SyncStatus
import app.cragpack.designsystem.component.SyncStatusPill
import app.cragpack.designsystem.component.TopBar
import app.cragpack.designsystem.icon.LucideIcon
import app.cragpack.designsystem.theme.CragpackTheme
import app.cragpack.designsystem.tokens.SizeTokens
import app.cragpack.domain.grade.GradeDisplay
import app.cragpack.domain.objective.BetaType
import app.cragpack.feature.LocalGradeDisplay
import app.cragpack.feature.text
import app.cragpack.domain.trip.Person
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** One way to narrow the wishlist. Only one filter at a time; tapping the chip again picks another or clears it. */
enum class WishlistFilter(val label: String) { Sector("Sector"), Grade("Grade"), Who("Who") }

data class FilterOption(val key: String, val label: String)

data class WishlistUi(
    val all: List<Objective>,
    val crew: List<Person>,
    val sectors: List<FilterOption>,
)

class WishlistViewModel(private val graph: AppGraph, tripId: String) : ViewModel() {
    /** null while loading. */
    val state = combine(
        graph.objectives.observeWishlist(tripId),
        graph.trips.observeTrip(tripId),
        graph.objectives.observeSectors(tripId),
    ) { items, trip, sectors ->
        WishlistUi(items, trip?.crew.orEmpty(), sectors.map { FilterOption(it.id, it.name) })
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    var filter by mutableStateOf<WishlistFilter?>(null)
    var key by mutableStateOf<String?>(null)
    /** The filter whose sheet is open. */
    var picking by mutableStateOf<WishlistFilter?>(null)

    fun pick(f: WishlistFilter?, k: String?) { filter = f; key = k; picking = null }

    fun setStarred(id: String, on: Boolean) = viewModelScope.launch(Dispatchers.IO) { graph.objectives.setStarred(id, on) }
}

internal fun options(ui: WishlistUi, filter: WishlistFilter, display: GradeDisplay): List<FilterOption> = when (filter) {
    WishlistFilter.Sector -> ui.sectors + if (ui.all.any { it.sector == null }) listOf(FilterOption(NO_SECTOR, NO_SECTOR)) else emptyList()
    WishlistFilter.Who -> ui.crew.map { FilterOption(it.id, it.label) }
    // One option per colour band, labelled with the grades actually on the list: "6A – 6C".
    WishlistFilter.Grade -> ui.all.groupBy { it.grade.band }.entries.sortedBy { it.key }.map { (band, list) ->
        val grades = list.map { it.grade }.distinct().sortedBy { it.sortIndex }.map { it.display(display) }
        FilterOption("$band", if (grades.size == 1) grades.single() else "${grades.first()} – ${grades.last()}")
    }
}

internal fun Objective.matches(filter: WishlistFilter, key: String): Boolean = when (filter) {
    WishlistFilter.Sector -> (sector?.id ?: NO_SECTOR) == key
    WishlistFilter.Grade -> "${grade.band}" == key
    WishlistFilter.Who -> wanters.any { it.id == key }
}

private const val NO_SECTOR = "No sector"

/** Screens 03 and 16 (empty), shown as "Projects". Draw [WishlistFilterSheet] in the screen overlay. */
@Composable
fun WishlistTab(vm: WishlistViewModel, bottom: PaddingValues, onBack: () -> Unit, onAdd: () -> Unit, onOpen: (String) -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    val filter = vm.filter
    val key = vm.key
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = SizeTokens.edge, end = SizeTokens.edge, bottom = bottom.calculateBottomPadding()),
        verticalArrangement = Arrangement.spacedBy(SizeTokens.gap),
    ) {
        item {
            TopBar(
                leading = { CtIconButton(LucideIcon.ChevronLeft, "Back", onBack) },
                center = { SyncStatusPill(SyncStatus.OnThisPhone) },
                trailing = { CtIconButton(LucideIcon.Plus, "Add project", onAdd, style = CtIconButtonStyle.Accent) },
            )
        }
        item { CtText("Projects", Modifier.padding(top = 24.dp, start = 4.dp, end = 4.dp), style = CragpackTheme.type.h1) }
        val state = state ?: return@LazyColumn
        if (state.all.isEmpty()) {
            item {
                EmptyState("No projects yet", Modifier.fillMaxWidth().padding(top = 8.dp).height(320.dp)) {
                    CtButton("Add project", onAdd, icon = LucideIcon.Plus)
                }
            }
            return@LazyColumn
        }
        item {
            Row(Modifier.padding(top = 6.dp, bottom = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                WishlistFilter.entries.forEach { f ->
                    val chosen = if (filter == f) options(state, f, LocalGradeDisplay.current).firstOrNull { it.key == key }?.label else null
                    CtChip(chosen ?: f.label, selected = chosen != null, onClick = { vm.picking = f })
                }
            }
        }
        val shown = if (filter == null || key == null) state.all else state.all.filter { it.matches(filter, key) }
        if (shown.isEmpty()) {
            item {
                EmptyState("Nothing matches", Modifier.fillMaxWidth().height(240.dp)) {
                    CtButton("Show all", { vm.pick(null, null) })
                }
            }
        }
        // Grouped by sector, in the order sectors were created; objectives without one come last.
        val order = state.sectors.map { it.key }
        shown.groupBy { it.sector }.entries.sortedBy { (s, _) -> s?.let { order.indexOf(it.id) } ?: Int.MAX_VALUE }.forEach { (sector, list) ->
            item(key = "h-${sector?.id}") {
                CtText(
                    sector?.name ?: NO_SECTOR,
                    Modifier.padding(start = 8.dp, top = 10.dp),
                    style = CragpackTheme.type.label.copy(fontSize = CragpackTheme.type.label.fontSize * 14 / 13),
                    color = CragpackTheme.colors.ink2,
                )
            }
            items(list, key = { it.id }) { o -> ObjectiveRow(o, { onOpen(o.id) }, { vm.setStarred(o.id, !o.starred) }) }
        }
    }
}

@Composable
private fun ObjectiveRow(o: Objective, onClick: () -> Unit, onStar: () -> Unit) {
    val text = o.grade.text()
    val meta: @Composable RowScope.() -> Unit = {
        if (o.wanters.isNotEmpty()) AvatarStack(o.wanters.map { it.name to it.colorIndex }, size = AvatarSize.Sm)
        if (o.sent) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                CtIcon(LucideIcon.Check, null, size = 18.dp, tint = CragpackTheme.colors.accentInk)
                CtText("Sent", style = CragpackTheme.type.label, color = CragpackTheme.colors.accentInk)
            }
        }
        // What beta is there, one icon per kind: videos (uploaded or YouTube), photos, topos, notes.
        betaIcons.filter { (types, _) -> types.any { it in o.beta } }.forEach { (_, icon) ->
            CtIcon(icon, null, size = 18.dp, tint = CragpackTheme.colors.ink2)
        }
    }
    ListRow(
        o.name,
        onClick = onClick,
        leading = { GradeChip(text, o.grade.band) },
        trailing = { StarToggle(o.starred, onStar, if (o.starred) "Don't want ${o.name}" else "Want ${o.name}") },
        meta = meta.takeIf { o.wanters.isNotEmpty() || o.sent || o.beta.isNotEmpty() },
    )
}

private val betaIcons = listOf(
    setOf(BetaType.Video, BetaType.Youtube) to LucideIcon.Video,
    setOf(BetaType.Photo) to LucideIcon.Image,
    setOf(BetaType.Topo) to LucideIcon.FileText,
    setOf(BetaType.Note) to LucideIcon.NotebookPen,
)


/** Pick one option of the filter being edited, or All. */
@Composable
fun BoxScope.WishlistFilterSheet(vm: WishlistViewModel) {
    val state by vm.state.collectAsStateWithLifecycle()
    val f = vm.picking
    val ui = state
    CtBottomSheet(visible = f != null && ui != null, title = f?.label ?: "", onDismiss = { vm.picking = null }) {
        if (f == null || ui == null) return@CtBottomSheet
        CtRadioRow("All", selected = vm.filter != f, onClick = { vm.pick(null, null) })
        options(ui, f, LocalGradeDisplay.current).forEach { o ->
            CtRadioRow(o.label, selected = vm.filter == f && vm.key == o.key, onClick = { vm.pick(f, o.key) })
        }
    }
}
