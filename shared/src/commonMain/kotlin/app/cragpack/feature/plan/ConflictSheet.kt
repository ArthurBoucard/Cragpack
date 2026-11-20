package app.cragpack.feature.plan

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.cragpack.data.AppGraph
import app.cragpack.designsystem.component.Avatar
import app.cragpack.designsystem.component.CtBottomSheet
import app.cragpack.designsystem.component.CtButton
import app.cragpack.designsystem.component.CtButtonSize
import app.cragpack.designsystem.component.CtButtonStyle
import app.cragpack.designsystem.component.CtRadioRow
import app.cragpack.designsystem.icon.LucideIcon
import app.cragpack.domain.format.formatTime
import app.cragpack.domain.format.formatWeekday
import app.cragpack.domain.plan.Move
import app.cragpack.domain.trip.Person
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ConflictViewModel(private val graph: AppGraph, tripId: String) : ViewModel() {
    /** The open conflicts, with me as a climber (name and colour) for "You". */
    val state = combine(graph.plan.observeConflicts(tripId), graph.trips.observeCrew(tripId)) { conflicts, crew ->
        conflicts to (crew.firstOrNull { it.person.id == graph.myId }?.person ?: Person(graph.myId, "", 1))
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun keepMine(objectiveId: String) = viewModelScope.launch(Dispatchers.IO) { graph.plan.keepMine(objectiveId) }
    fun acceptTheirs(objectiveId: String) = viewModelScope.launch(Dispatchers.IO) { graph.plan.acceptTheirs(objectiveId) }
}

/**
 * Sync conflict (19): two climbers moved the same objective to different days while offline, and the other move
 * reached the server first (tech spec §3). Pick one; ✕ asks again next time the trip opens. Shown over any tab,
 * because my move doesn't go up until it's settled.
 */
@Composable
fun BoxScope.ConflictSheet(vm: ConflictViewModel) {
    val state by vm.state.collectAsStateWithLifecycle()
    var later by rememberSaveable { mutableStateOf(emptyList<String>()) }
    val (conflicts, me) = state ?: return
    val conflict = conflicts.firstOrNull { it.objectiveId !in later }
    var keepMine by remember(conflict?.objectiveId) { mutableStateOf(true) }
    CtBottomSheet(conflict != null, conflict?.let { "${it.name} moved twice" }.orEmpty(), onDismiss = { conflict?.let { later = later + it.objectiveId } }) {
        val c = conflict ?: return@CtBottomSheet
        val mine = c.mine.copy(by = me)
        MoveRow(mine, "You", selected = keepMine) { keepMine = true }
        MoveRow(c.theirs, c.theirs.by?.label ?: "Someone", selected = !keepMine) { keepMine = false }
        val chosen = if (keepMine) mine else c.theirs
        CtButton(
            chosen.date?.let { "Keep ${formatWeekday(it)}" } ?: "Keep it unplanned",
            { if (keepMine) vm.keepMine(c.objectiveId) else vm.acceptTheirs(c.objectiveId) },
            style = CtButtonStyle.Accent, size = CtButtonSize.Wide, icon = LucideIcon.Check,
        )
    }
}

@Composable
private fun MoveRow(move: Move, who: String, selected: Boolean, onClick: () -> Unit) {
    val where = listOfNotNull(place(move), move.sector).joinToString(" · ")
    CtRadioRow(
        where, selected, onClick,
        meta = "$who · ${formatTime(move.atMillis)}",
        leading = { move.by?.let { Avatar(it.name, it.colorIndex) } },
    )
}

private fun place(move: Move) = move.date?.let(::formatWeekday) ?: "Not planned"
