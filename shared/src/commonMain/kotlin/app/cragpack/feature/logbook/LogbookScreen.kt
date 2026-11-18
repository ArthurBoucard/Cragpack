package app.cragpack.feature.logbook

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.cragpack.data.AppGraph
import app.cragpack.data.LogbookEntry
import app.cragpack.designsystem.component.BigNumber
import app.cragpack.designsystem.component.CtCard
import app.cragpack.designsystem.component.CtChip
import app.cragpack.designsystem.component.CtIconButton
import app.cragpack.designsystem.component.CtScreen
import app.cragpack.designsystem.component.CtText
import app.cragpack.designsystem.component.CtaDock
import app.cragpack.designsystem.component.EmptyState
import app.cragpack.designsystem.component.GradeChip
import app.cragpack.designsystem.component.GradeChipSize
import app.cragpack.designsystem.component.GradePyramid
import app.cragpack.designsystem.component.GradeTag
import app.cragpack.designsystem.component.ListRow
import app.cragpack.designsystem.component.SyncStatus
import app.cragpack.designsystem.component.SyncStatusPill
import app.cragpack.designsystem.component.TopBar
import app.cragpack.designsystem.icon.LucideIcon
import app.cragpack.designsystem.theme.CragpackTheme
import app.cragpack.designsystem.tokens.SizeTokens
import app.cragpack.domain.format.formatDay
import app.cragpack.domain.grade.GradeDisplay
import app.cragpack.domain.grade.GradeScale
import app.cragpack.domain.log.hardest
import app.cragpack.domain.log.pyramid
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn

class LogbookViewModel(graph: AppGraph) : ViewModel() {
    val today = graph.today()
    /** null while loading. */
    val entries = graph.logs.observeLogbook().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}

private val kinds = listOf("All", "Boulder", "Route")

/** My logbook (26): every send across trips and outside them. */
@Composable
fun LogbookScreen(vm: LogbookViewModel, onBack: () -> Unit, onLogSend: () -> Unit, onOpen: (String) -> Unit) {
    val all by vm.entries.collectAsStateWithLifecycle()
    var kind by rememberSaveable { mutableIntStateOf(0) }
    var thisYear by rememberSaveable { mutableStateOf(false) }
    CtScreen(dock = { CtaDock("Log a send", LucideIcon.Plus, onLogSend) }) { bottom ->
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = SizeTokens.edge, end = SizeTokens.edge, bottom = bottom.calculateBottomPadding()),
            verticalArrangement = Arrangement.spacedBy(SizeTokens.gap),
        ) {
            item {
                TopBar(
                    leading = { CtIconButton(LucideIcon.ChevronLeft, "Back", onBack) },
                    center = { SyncStatusPill(SyncStatus.OnThisPhone) },
                    // ponytail: the ⚙ opens Settings (27) once that screen exists.
                )
            }
            item { CtText("Logbook", Modifier.padding(top = 24.dp, start = 4.dp, end = 4.dp), style = CragpackTheme.type.h1) }
            val list = all ?: return@LazyColumn
            item {
                Row(Modifier.padding(top = 6.dp, bottom = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    kinds.forEachIndexed { i, k -> CtChip(k, selected = i == kind, onClick = { kind = i }) }
                    CtChip("${vm.today.year}", selected = thisYear, onClick = { thisYear = !thisYear })
                }
            }
            // ponytail: the logbook lists sends only; attempts show in each trip's log.
            val sends = list.filter { e ->
                e.style.isSend &&
                    (kind == 0 || (kind == 1) == e.grade.isBoulder) &&
                    (!thisYear || e.date.year == vm.today.year)
            }
            if (sends.isEmpty()) {
                item { EmptyState("No sends yet", Modifier.fillMaxWidth().height(240.dp)) }
                return@LazyColumn
            }
            item { Stats(sends, if (kind == 2) GradeScale.French else GradeScale.Font) }
            items(sends, key = { it.id }) { e -> Entry(e, vm.today, onOpen) }
        }
    }
}

@Composable
private fun Stats(sends: List<LogbookEntry>, scale: GradeScale) = CtCard(Modifier.fillMaxWidth()) {
    Column(Modifier.padding(horizontal = 20.dp, vertical = 18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            BigNumber("${sends.size}", if (sends.size == 1) "send" else "sends", Modifier.weight(1f), xl = true)
            hardest(sends.map { it.grade })?.let { g ->
                CtText("Hardest", Modifier.padding(end = 8.dp), color = CragpackTheme.colors.ink2)
                GradeTag(g.display(GradeDisplay.AsEntered), g.band)
            }
        }
        GradePyramid(pyramid(sends.map { it.grade }, scale).map { Triple(it.label, it.band, it.count) })
    }
}

@Composable
private fun Entry(e: LogbookEntry, today: kotlinx.datetime.LocalDate, onOpen: (String) -> Unit) {
    val where = e.trip ?: e.crag.ifBlank { "Outside a trip" }
    ListRow(
        e.name,
        meta = "$where · ${formatDay(e.date, today)}",
        onClick = e.objectiveId?.let { { onOpen(it) } },
        leading = { GradeChip(e.grade.display(GradeDisplay.AsEntered), e.grade.band, size = GradeChipSize.Sm) },
        trailing = {
            CtText(
                e.style.label,
                Modifier.padding(end = 10.dp),
                style = CragpackTheme.type.body.copy(fontWeight = FontWeight.SemiBold),
                color = CragpackTheme.colors.accentInk,
            )
        },
    )
}
