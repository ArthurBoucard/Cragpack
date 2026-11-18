package app.cragpack.feature.triplog

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.cragpack.data.AppGraph
import app.cragpack.data.TripLogEntry
import app.cragpack.designsystem.component.AvatarSize
import app.cragpack.designsystem.component.AvatarStack
import app.cragpack.designsystem.component.CtChip
import app.cragpack.designsystem.component.CtIcon
import app.cragpack.designsystem.component.CtIconButton
import app.cragpack.designsystem.component.CtText
import app.cragpack.designsystem.component.EmptyState
import app.cragpack.designsystem.component.GradeChip
import app.cragpack.designsystem.component.ListRow
import app.cragpack.designsystem.component.SyncStatus
import app.cragpack.designsystem.component.SyncStatusPill
import app.cragpack.designsystem.component.TopBar
import app.cragpack.designsystem.icon.LucideIcon
import app.cragpack.designsystem.theme.CragpackTheme
import app.cragpack.designsystem.tokens.SizeTokens
import app.cragpack.domain.format.formatWeekday
import app.cragpack.domain.grade.GradeDisplay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn

class TripLogViewModel(graph: AppGraph, tripId: String) : ViewModel() {
    val myId = graph.myId
    /** null while loading. */
    val entries = graph.logs.observeTripLog(tripId).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}

private val filters = listOf("Everyone", "Me", "Sends")

/** One row: everyone who logged one objective on one day. */
internal data class LogRow(val objectiveId: String?, val entries: List<TripLogEntry>) {
    val first get() = entries.first()

    /** "Flash · Sent" with a tick, or "3 attempts". */
    val summary: Pair<String, Boolean>
        get() {
            val sends = entries.map { it.style }.filter { it.isSend }.distinct().sortedByDescending { it.ordinal }
            if (sends.isNotEmpty()) return sends.joinToString(" · ") { it.label } to true
            val n = entries.size
            return (if (n == 1) "1 attempt" else "$n attempts") to false
        }
}

/** Day + sector groups, newest day first, then rows per objective in log order. */
internal fun group(entries: List<TripLogEntry>): List<Pair<String, List<LogRow>>> =
    entries.groupBy { it.date to it.sector }.map { (key, list) ->
        val (date, sector) = key
        listOfNotNull(formatWeekday(date), sector).joinToString(" · ") to
            list.groupBy { it.objectiveId ?: it.name }.values.map { LogRow(it.first().objectiveId, it) }
    }

/** The Log tab (25). */
@Composable
fun TripLogTab(vm: TripLogViewModel, bottom: PaddingValues, onBack: () -> Unit, onOpen: (String) -> Unit) {
    val all by vm.entries.collectAsStateWithLifecycle()
    var filter by rememberSaveable { mutableIntStateOf(0) }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = SizeTokens.edge, end = SizeTokens.edge, bottom = bottom.calculateBottomPadding()),
        verticalArrangement = Arrangement.spacedBy(SizeTokens.gap),
    ) {
        item {
            TopBar(
                leading = { CtIconButton(LucideIcon.ChevronLeft, "Back", onBack) },
                center = { SyncStatusPill(SyncStatus.OnThisPhone) },
            )
        }
        item { CtText("Log", Modifier.padding(top = 24.dp, start = 4.dp, end = 4.dp), style = CragpackTheme.type.h1) }
        val list = all ?: return@LazyColumn
        if (list.isEmpty()) {
            item { EmptyState("Nothing logged yet", Modifier.fillMaxWidth().padding(top = 8.dp).height(240.dp)) }
            return@LazyColumn
        }
        item {
            Row(Modifier.padding(top = 6.dp, bottom = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                filters.forEachIndexed { i, f -> CtChip(f, selected = i == filter, onClick = { filter = i }) }
            }
        }
        val shown = when (filter) {
            1 -> list.filter { it.person.id == vm.myId }
            2 -> list.filter { it.style.isSend }
            else -> list
        }
        group(shown).forEach { (header, rows) ->
            item(key = "h-$header") {
                CtText(
                    header,
                    Modifier.padding(start = 8.dp, top = 10.dp),
                    style = CragpackTheme.type.label.copy(fontSize = CragpackTheme.type.label.fontSize * 14 / 13),
                    color = CragpackTheme.colors.ink2,
                )
            }
            items(rows, key = { "$header-${it.objectiveId ?: it.first.name}" }) { row -> LogListRow(row, onOpen) }
        }
    }
}

@Composable
private fun LogListRow(row: LogRow, onOpen: (String) -> Unit) {
    val (text, sent) = row.summary
    val color = if (sent) CragpackTheme.colors.accentInk else CragpackTheme.colors.ink2
    val meta: @Composable RowScope.() -> Unit = {
        AvatarStack(row.entries.map { it.person }.distinctBy { it.id }.map { it.name to it.colorIndex }, size = AvatarSize.Sm)
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
            if (sent) CtIcon(LucideIcon.Check, null, size = 18.dp, tint = color)
            CtText(text, style = CragpackTheme.type.label, color = color, maxLines = 1)
        }
    }
    ListRow(
        row.first.name,
        onClick = row.objectiveId?.let { { onOpen(it) } },
        leading = { GradeChip(row.first.grade.display(GradeDisplay.AsEntered), row.first.grade.band) },
        meta = meta,
    )
}
