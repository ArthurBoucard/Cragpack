package app.cragpack.feature.gear

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
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
import app.cragpack.data.GearItem
import app.cragpack.data.PlanDay
import app.cragpack.designsystem.component.AvatarStack
import app.cragpack.designsystem.component.ChipRow
import app.cragpack.designsystem.component.CtBottomSheet
import app.cragpack.designsystem.component.CtButton
import app.cragpack.designsystem.component.CtButtonSize
import app.cragpack.designsystem.component.CtButtonStyle
import app.cragpack.designsystem.component.CtChip
import app.cragpack.designsystem.component.CtIconButton
import app.cragpack.designsystem.component.CtIconButtonStyle
import app.cragpack.designsystem.component.CtStepperRow
import app.cragpack.designsystem.component.CtSwitchRow
import app.cragpack.designsystem.component.CtText
import app.cragpack.designsystem.component.CtTextField
import app.cragpack.designsystem.component.EmptyState
import app.cragpack.designsystem.component.ListRow
import app.cragpack.designsystem.component.QuantityBox
import app.cragpack.designsystem.component.SyncStatus
import app.cragpack.designsystem.component.SyncStatusPill
import app.cragpack.designsystem.component.TopBar
import app.cragpack.designsystem.icon.LucideIcon
import app.cragpack.designsystem.theme.CragpackTheme
import app.cragpack.designsystem.tokens.SizeTokens
import app.cragpack.domain.format.formatWeekday
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class GearUi(val items: List<GearItem>, val days: List<PlanDay>) {
    /** Filter chips: only days that have gear of their own, by date. */
    val filterDays: List<PlanDay> get() = days.filter { d -> items.any { it.dayId == d.id } }
}

/**
 * What a filter shows: everything for the whole trip (null), or one day's own items plus the
 * whole-trip ones, i.e. everything to pack that day.
 */
internal fun GearUi.shown(dayId: String?): List<GearItem> =
    if (dayId == null) items else items.filter { it.dayId == null || it.dayId == dayId }

sealed interface GearSheet {
    data object Add : GearSheet
    data class Item(val id: String) : GearSheet
}

class GearViewModel(private val graph: AppGraph, private val tripId: String) : ViewModel() {
    /** null while loading. */
    val state = combine(graph.gear.observeGear(tripId), graph.plan.observeDays(tripId), ::GearUi)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** The day filter; null = whole trip. */
    var dayFilter by mutableStateOf<String?>(null)
    var sheet by mutableStateOf<GearSheet?>(null)

    fun add(name: String, needed: Int, dayId: String?, iBringIt: Boolean) =
        write { graph.gear.add(tripId, name, needed, dayId, iBringIt) }
    fun changeMine(id: String, delta: Int) = write { graph.gear.changeMine(id, delta) }
    fun changeNeeded(id: String, delta: Int) = write { graph.gear.changeNeeded(id, delta) }
    fun delete(id: String) = write { graph.gear.delete(id) }

    private fun write(block: () -> Unit) = viewModelScope.launch(Dispatchers.IO) { block() }
}

/** The Gear tab (06). Draw [GearSheets] in the screen overlay. */
@Composable
fun GearTab(vm: GearViewModel, bottom: PaddingValues, onBack: () -> Unit) {
    val ui by vm.state.collectAsStateWithLifecycle()
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = SizeTokens.edge, end = SizeTokens.edge, bottom = bottom.calculateBottomPadding()),
        verticalArrangement = Arrangement.spacedBy(SizeTokens.gap),
    ) {
        item {
            TopBar(
                leading = { CtIconButton(LucideIcon.ChevronLeft, "Back", onBack) },
                center = { SyncStatusPill(SyncStatus.OnThisPhone) },
                trailing = { CtIconButton(LucideIcon.Plus, "Add gear", { vm.sheet = GearSheet.Add }, style = CtIconButtonStyle.Accent) },
            )
        }
        item { CtText("Gear", Modifier.padding(top = 24.dp, start = 4.dp, end = 4.dp), style = CragpackTheme.type.h1) }
        val state = ui ?: return@LazyColumn
        if (state.items.isEmpty()) {
            item {
                EmptyState("No gear yet", Modifier.fillMaxWidth().padding(top = 8.dp).height(320.dp)) {
                    CtButton("Add gear", { vm.sheet = GearSheet.Add }, icon = LucideIcon.Plus)
                }
            }
            return@LazyColumn
        }
        // A deleted day's items are gone: fall back to the whole trip.
        val filter = vm.dayFilter?.takeIf { id -> state.filterDays.any { it.id == id } }
        if (state.filterDays.isNotEmpty()) {
            item {
                DayChips(state.filterDays, filter, { vm.dayFilter = it }, Modifier.padding(top = 6.dp, bottom = 4.dp))
            }
        }
        items(state.shown(filter), key = { it.id }) { g -> GearRow(g, { vm.sheet = GearSheet.Item(g.id) }, { vm.changeMine(g.id, +1) }) }
    }
}

/** Rule 2: a dashed row while something is missing, with the one-tap "Bring 1". */
@Composable
private fun GearRow(g: GearItem, onClick: () -> Unit, onBringOne: () -> Unit) = ListRow(
    g.name,
    todo = g.missing > 0,
    onClick = onClick,
    leading = { QuantityBox("${g.claimed}/${g.needed}") },
    trailing = {
        if (g.bringers.isNotEmpty()) AvatarStack(g.bringers.map { it.person.name to it.person.colorIndex })
        if (g.missing > 0) CtButton("Bring 1", onBringOne, style = CtButtonStyle.Accent, size = CtButtonSize.Sm)
    },
)

/** "Whole trip" (null) or one day. Wraps onto more lines on long trips. */
@Composable
private fun DayChips(days: List<PlanDay>, selected: String?, onSelect: (String?) -> Unit, modifier: Modifier = Modifier) =
    ChipRow(modifier) {
        CtChip("Whole trip", selected = selected == null, onClick = { onSelect(null) })
        days.forEach { d -> CtChip(formatWeekday(d.date), selected = selected == d.id, onClick = { onSelect(d.id) }) }
    }

/** Add gear (23) and the row sheet: what you bring, how many are needed, delete. */
@Composable
fun BoxScope.GearSheets(vm: GearViewModel) {
    val ui by vm.state.collectAsStateWithLifecycle()
    val sheet = vm.sheet
    val close = { vm.sheet = null }

    // Add gear (23)
    var name by rememberSaveable(sheet == GearSheet.Add) { mutableStateOf("") }
    var needed by rememberSaveable(sheet == GearSheet.Add) { mutableIntStateOf(1) }
    var forDay by rememberSaveable(sheet == GearSheet.Add) { mutableStateOf(vm.dayFilter) }
    var iBringIt by rememberSaveable(sheet == GearSheet.Add) { mutableStateOf(true) }
    CtBottomSheet(sheet == GearSheet.Add, "Add gear", close) {
        CtTextField(
            name, { name = it }, "Item", placeholder = "e.g. Crash pads",
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
        )
        CtStepperRow("Needed", needed, { needed = (needed + it).coerceIn(1, 99) }, range = 1..99)
        DayChips(ui?.days.orEmpty(), forDay, { forDay = it })
        CtSwitchRow("I'll bring it", iBringIt, { iBringIt = it })
        CtButton(
            "Add", { vm.add(name, needed, forDay, iBringIt); close() },
            style = CtButtonStyle.Accent, size = CtButtonSize.Wide, icon = LucideIcon.Plus, enabled = name.isNotBlank(),
        )
    }

    // Row sheet: reads the live item, so steppers show each change at once.
    val item = (sheet as? GearSheet.Item)?.let { s -> ui?.items?.firstOrNull { it.id == s.id } }
    CtBottomSheet(item != null, item?.name.orEmpty(), close) {
        if (item == null) return@CtBottomSheet
        CtText(item.date?.let { "For ${formatWeekday(it)}" } ?: "For the whole trip", Modifier.padding(start = 4.dp), color = CragpackTheme.colors.ink2)
        CtStepperRow("I bring", item.mine, { vm.changeMine(item.id, it) })
        CtStepperRow("Needed", item.needed, { vm.changeNeeded(item.id, it) }, range = 1..99)
        CtButton("Delete item", { vm.delete(item.id); close() }, style = CtButtonStyle.Danger, size = CtButtonSize.Wide)
    }
}
