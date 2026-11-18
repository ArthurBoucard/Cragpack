package app.cragpack.feature.trip

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.cragpack.data.AppGraph
import app.cragpack.data.PlannedDay
import app.cragpack.data.TripDetail
import app.cragpack.designsystem.component.AvatarSize
import app.cragpack.designsystem.component.AvatarStack
import app.cragpack.designsystem.component.BigNumber
import app.cragpack.designsystem.component.CtCard
import app.cragpack.designsystem.component.CtIconButton
import app.cragpack.designsystem.component.CtScreen
import app.cragpack.designsystem.component.CtText
import app.cragpack.designsystem.component.EmptyState
import app.cragpack.designsystem.component.LightStatusBarIcons
import app.cragpack.designsystem.component.NowBadge
import app.cragpack.designsystem.component.PhotoSurface
import app.cragpack.designsystem.component.SegmentedProgress
import app.cragpack.designsystem.component.StatTile
import app.cragpack.designsystem.component.SyncStatus
import app.cragpack.designsystem.component.SyncStatusPill
import app.cragpack.designsystem.component.TabDock
import app.cragpack.designsystem.component.TopBar
import app.cragpack.designsystem.icon.LucideIcon
import app.cragpack.designsystem.theme.CragpackTheme
import app.cragpack.designsystem.tokens.RadiusTokens
import app.cragpack.designsystem.tokens.SizeTokens
import app.cragpack.domain.format.formatDateRange
import app.cragpack.domain.trip.TripTiming
import app.cragpack.domain.trip.timing
import app.cragpack.feature.triplog.TripLogTab
import app.cragpack.feature.triplog.TripLogViewModel
import app.cragpack.feature.wishlist.WishlistFilterSheet
import app.cragpack.feature.wishlist.WishlistTab
import app.cragpack.feature.wishlist.WishlistViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class TripUi(val detail: TripDetail, val timing: TripTiming, val today: PlannedDay?)

class TripViewModel(graph: AppGraph, tripId: String) : ViewModel() {
    private val todayDate = graph.today()

    /** null while loading. */
    val state = combine(graph.trips.observeTrip(tripId), graph.trips.observeDay(tripId, todayDate)) { detail, day ->
        detail?.let { TripUi(it, it.trip.timing(todayDate), day) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}

private val tabs = listOf("Trip", "Wishlist", "Plan", "Gear", "Log")

/** Inside a trip: the 5-tab dock. Screen 02 is the Trip tab. */
@Composable
fun TripScreen(
    vm: TripViewModel,
    wishlist: WishlistViewModel,
    log: TripLogViewModel,
    onBack: () -> Unit,
    onAddObjective: () -> Unit,
    onOpenObjective: (String) -> Unit,
) {
    val ui by vm.state.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    CtScreen(
        statusBarStrip = tab != 0,
        dock = { TabDock(tabs, tab, { tab = it }) },
        overlay = { if (tab == 1) WishlistFilterSheet(wishlist) },
    ) { bottom ->
        val state = ui ?: return@CtScreen
        when (tab) {
            0 -> {
                LightStatusBarIcons()
                Overview(state, bottom.calculateBottomPadding(), onBack, onTab = { tab = it })
            }
            1 -> WishlistTab(wishlist, bottom, onBack, onAddObjective, onOpenObjective)
            4 -> TripLogTab(log, bottom, onBack, onOpenObjective)
            // ponytail: placeholder until each tab's slice lands.
            else -> Column(Modifier.padding(horizontal = SizeTokens.edge)) {
                TopBar(leading = { CtIconButton(LucideIcon.ChevronLeft, "Back", onBack) })
                EmptyState("${tabs[tab]} comes next", Modifier.fillMaxWidth().padding(top = 24.dp).height(240.dp))
            }
        }
    }
}

@Composable
private fun Overview(ui: TripUi, bottom: androidx.compose.ui.unit.Dp, onBack: () -> Unit, onTab: (Int) -> Unit) {
    val (trip, crew, stats) = ui.detail
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(SizeTokens.gap)) {
        PhotoSurface(Modifier.fillMaxWidth(), RoundedCornerShape(bottomStart = RadiusTokens.xl, bottomEnd = RadiusTokens.xl)) {
            Column(Modifier.padding(start = SizeTokens.edge, end = SizeTokens.edge, bottom = SizeTokens.edge)) {
                TopBar(
                    leading = { CtIconButton(LucideIcon.ChevronLeft, "Back", onBack) },
                    center = { SyncStatusPill(SyncStatus.OnThisPhone) },
                    // ponytail: ⋮ opens Trip settings once that screen exists.
                )
                Spacer(Modifier.height(120.dp))
                if (ui.timing is TripTiming.Now) NowBadge("Day ${ui.timing.day}/${ui.timing.days}", Modifier.padding(bottom = 12.dp))
                CtText(trip.name, style = CragpackTheme.type.titleXl)
                val place = listOf(trip.crag, formatDateRange(trip.start, trip.end)).filter { it.isNotBlank() }.joinToString(" · ")
                CtText(place, Modifier.padding(top = 8.dp, bottom = 16.dp))
                // ponytail: the invite + button arrives with accounts (Phase 2).
                AvatarStack(crew.map { it.name to it.colorIndex }, size = AvatarSize.Xl)
            }
        }
        Column(Modifier.padding(horizontal = SizeTokens.edge), verticalArrangement = Arrangement.spacedBy(SizeTokens.gap)) {
            CtCard(Modifier.fillMaxWidth(), onClick = { onTab(4) }) {
                Column(Modifier.padding(horizontal = 20.dp, vertical = 18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    BigNumber("${stats.sent}", "/${stats.objectives} sent", xl = true)
                    SegmentedProgress(if (stats.objectives == 0) 0f else stats.sent.toFloat() / stats.objectives)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(SizeTokens.gap)) {
                val (label, value) = when (val t = ui.timing) {
                    is TripTiming.Now -> "Today" to when {
                        ui.today?.isRest == true -> "Rest"
                        else -> ui.today?.sector ?: "Not planned"
                    }
                    is TripTiming.Upcoming -> "Starts in" to if (t.inDays == 1) "1 day" else "${t.inDays} days"
                    TripTiming.Past -> "Trip" to "${trip.days} days"
                }
                StatTile(label, value, Modifier.weight(1f), onClick = { onTab(2) })
                StatTile(
                    "Wishlist", "${stats.objectives}", Modifier.weight(1f),
                    unit = if (stats.objectives > 0) "· ${stats.unplanned} unplanned" else null,
                    onClick = { onTab(1) },
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(SizeTokens.gap)) {
                val missing = stats.gear - stats.gearCovered
                StatTile(
                    "Gear", if (stats.gear == 0) "0" else "${stats.gearCovered}/${stats.gear}", Modifier.weight(1f),
                    unit = if (missing > 0) "· $missing missing" else if (stats.gear == 0) "items" else null,
                    todo = missing > 0,
                    onClick = { onTab(3) },
                )
                // ponytail: "saved" count and the Download action arrive with the offline pack (Phase 3).
                StatTile("Beta", "${stats.beta}", Modifier.weight(1f), unit = if (stats.beta == 1) "item" else "items")
            }
            Spacer(Modifier.height(bottom))
        }
    }
}
