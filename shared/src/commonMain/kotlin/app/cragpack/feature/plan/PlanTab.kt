package app.cragpack.feature.plan

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.cragpack.data.AppGraph
import app.cragpack.data.Objective
import app.cragpack.data.PlanDay
import app.cragpack.designsystem.component.CtBottomSheet
import app.cragpack.designsystem.component.CtButton
import app.cragpack.designsystem.component.CtCard
import app.cragpack.designsystem.component.CtIcon
import app.cragpack.designsystem.component.CtIconButton
import app.cragpack.designsystem.component.CtRadioRow
import app.cragpack.designsystem.component.CtText
import app.cragpack.designsystem.component.DayTab
import app.cragpack.designsystem.component.DayTabs
import app.cragpack.designsystem.component.EmptyState
import app.cragpack.designsystem.component.GradeChip
import app.cragpack.designsystem.component.ListRow
import app.cragpack.designsystem.component.PhotoSurface
import app.cragpack.designsystem.component.SyncStatus
import app.cragpack.designsystem.component.SyncStatusPill
import app.cragpack.designsystem.component.TopBar
import app.cragpack.designsystem.component.TrayPick
import app.cragpack.designsystem.icon.LucideIcon
import app.cragpack.designsystem.theme.CragpackTheme
import app.cragpack.designsystem.tokens.SizeTokens
import app.cragpack.domain.format.formatWeekday
import app.cragpack.domain.format.weekdayName
import app.cragpack.feature.text
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import sh.calvin.reorderable.ReorderableColumn

data class PlanUi(val days: List<PlanDay>, val byDay: Map<String, List<Objective>>, val unplanned: List<Objective>)

class PlanViewModel(private val graph: AppGraph, tripId: String) : ViewModel() {
    val today = graph.today()

    /** null while loading. */
    val state = combine(
        graph.plan.observeDays(tripId),
        graph.plan.observePlan(tripId),
        graph.objectives.observeWishlist(tripId),
    ) { days, plan, wishlist ->
        val byId = wishlist.associateBy { it.id }
        val planned = plan.mapNotNull { p -> byId[p.objectiveId]?.let { p.dayId to it } }
        PlanUi(
            days,
            planned.groupBy({ it.first }, { it.second }),
            wishlist.filter { o -> plan.none { it.objectiveId == o.id } },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** The selected day tab; null = today's day, or the first. */
    var selectedDay by mutableStateOf<String?>(null)

    /** The objective whose "Plan for" sheet is open. */
    var moving by mutableStateOf<Objective?>(null)

    fun selected(days: List<PlanDay>): PlanDay? =
        days.firstOrNull { it.id == selectedDay } ?: days.firstOrNull { it.date == today } ?: days.firstOrNull()

    /** Adds to the end of [dayId], or moves there from another day. */
    fun add(objectiveId: String, dayId: String) = write { graph.plan.place(objectiveId, dayId, Int.MAX_VALUE) }

    fun reorder(objectiveId: String, dayId: String, to: Int) = write { graph.plan.place(objectiveId, dayId, to) }

    fun unplan(objectiveId: String) = write { graph.plan.unplan(objectiveId) }

    private fun write(block: () -> Unit) = viewModelScope.launch(Dispatchers.IO) { block() }
}

internal fun dayLabel(d: PlanDay) = formatWeekday(d.date) to when {
    d.isRest -> "Rest"
    d.sector != null -> d.sector.name
    else -> "–"
}

/** The Plan tab (04). Draw [PlanMoveSheet] in the screen overlay. */
@Composable
fun PlanTab(vm: PlanViewModel, bottom: PaddingValues, onBack: () -> Unit, onEditDay: (String) -> Unit, onOpen: (String) -> Unit) {
    val ui by vm.state.collectAsStateWithLifecycle()
    val state = ui ?: return
    val day = vm.selected(state.days) ?: return
    val items = state.byDay[day.id].orEmpty()
    val bottomPad = bottom.calculateBottomPadding()

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            Column(Modifier.padding(horizontal = SizeTokens.edge)) {
                TopBar(
                    leading = { CtIconButton(LucideIcon.ChevronLeft, "Back", onBack) },
                    center = { SyncStatusPill(SyncStatus.OnThisPhone) },
                    trailing = { CtIconButton(LucideIcon.NotebookPen, "Edit day", { onEditDay(day.id) }) },
                )
                CtText("Plan", Modifier.padding(top = 24.dp, start = 4.dp, end = 4.dp, bottom = 14.dp), style = CragpackTheme.type.h1)
            }
            DayTabs(
                state.days.map { d ->
                    DayTab(weekdayName(d.date), "${d.date.day}", if (d.isRest) "Rest" else d.sector?.name, today = d.date == vm.today)
                },
                state.days.indexOf(day),
                { vm.selectedDay = state.days[it].id },
            )
            Column(Modifier.padding(start = SizeTokens.edge, end = SizeTokens.edge, top = 14.dp), verticalArrangement = Arrangement.spacedBy(SizeTokens.gap)) {
                DayCard(day) { onEditDay(day.id) }
                if (day.notes.isNotBlank()) {
                    CtCard(Modifier.fillMaxWidth()) { CtText(day.notes, Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) }
                }
                if (items.isEmpty()) {
                    CtText(
                        if (state.unplanned.isEmpty()) "Nothing planned. Add projects first." else "Nothing planned. Tap a project below to add it.",
                        Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 16.dp),
                        color = CragpackTheme.colors.ink2,
                        textAlign = TextAlign.Center,
                    )
                }
                // The list updates at once while the database catches up, so a dropped row doesn't jump back.
                var local by remember(items) { mutableStateOf(items) }
                ReorderableColumn(
                    local,
                    onSettle = { from, to ->
                        val moved = local[from]
                        local = local.toMutableList().apply { add(to, removeAt(from)) }
                        vm.reorder(moved.id, day.id, to)
                    },
                    verticalArrangement = Arrangement.spacedBy(SizeTokens.gap),
                ) { _, o, dragging ->
                    key(o.id) {
                        ReorderableItem {
                            ListRow(
                                o.name,
                                onClick = { onOpen(o.id) },
                                onLongClick = { vm.moving = o },
                                lifted = dragging,
                                leading = { GradeChip(o.grade.text(), o.grade.band) },
                                trailing = {
                                    Box(Modifier.size(SizeTokens.tapSm).draggableHandle(), contentAlignment = Alignment.Center) {
                                        CtIcon(LucideIcon.GripVertical, "Reorder ${o.name}", tint = CragpackTheme.colors.ink2)
                                    }
                                },
                                meta = null,
                            )
                        }
                    }
                }
                Spacer(Modifier.height(bottomPad + if (state.unplanned.isEmpty()) 0.dp else 64.dp))
            }
        }
        if (state.unplanned.isNotEmpty()) {
            Tray(state.unplanned, Modifier.align(Alignment.BottomStart).padding(bottom = bottomPad - 12.dp)) { vm.add(it, day.id) }
        }
    }
}

@Composable
private fun DayCard(day: PlanDay, onEdit: () -> Unit) {
    val sector = day.sector
    when {
        day.isRest || sector != null -> PhotoSurface(Modifier.fillMaxWidth(), onClick = onEdit) {
            Column(Modifier.padding(horizontal = 20.dp, vertical = 20.dp).padding(top = 40.dp)) {
                CtText(if (day.isRest) "Rest day" else sector!!.name, style = CragpackTheme.type.h3, maxLines = 1)
                val line = if (day.isRest) sector?.name else sector?.approach
                if (!line.isNullOrBlank()) CtText(line, Modifier.padding(top = 6.dp), maxLines = 2)
            }
        }
        // Rule 2: dashed = not done yet.
        else -> EmptyState("No sector yet", Modifier.fillMaxWidth()) { CtButton("Edit day", onEdit, icon = LucideIcon.NotebookPen) }
    }
}

@Composable
private fun Tray(unplanned: List<Objective>, modifier: Modifier, onAdd: (String) -> Unit) {
    LazyRow(
        modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = SizeTokens.edge),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        item { TrayLabel("${unplanned.size} unplanned") }
        items(unplanned, key = { it.id }) { o ->
            TrayPick(o.name, o.grade.text(), o.grade.band, { onAdd(o.id) })
        }
    }
}

@Composable
private fun TrayLabel(text: String) =
    CtCard(Modifier.height(SizeTokens.tap)) {
        CtText(text, Modifier.align(Alignment.Center).padding(horizontal = 16.dp), style = CragpackTheme.type.label, maxLines = 1)
    }

/** Long-press on a planned objective: move it to another day, or take it off the plan. */
@Composable
fun BoxScope.PlanMoveSheet(vm: PlanViewModel) {
    val ui by vm.state.collectAsStateWithLifecycle()
    val o = vm.moving
    val days = ui?.days.orEmpty()
    val current = ui?.byDay?.entries?.firstOrNull { (_, list) -> list.any { it.id == o?.id } }?.key
    CtBottomSheet(visible = o != null, title = o?.name.orEmpty(), onDismiss = { vm.moving = null }) {
        if (o == null) return@CtBottomSheet
        days.forEach { d ->
            val (date, sector) = dayLabel(d)
            CtRadioRow("$date · $sector", selected = d.id == current, onClick = { vm.add(o.id, d.id); vm.moving = null })
        }
        CtRadioRow("Not planned", selected = current == null, onClick = { vm.unplan(o.id); vm.moving = null })
    }
}
