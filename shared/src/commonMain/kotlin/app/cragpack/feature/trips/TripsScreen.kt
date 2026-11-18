package app.cragpack.feature.trips

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.cragpack.data.AppGraph
import app.cragpack.designsystem.component.Avatar
import app.cragpack.designsystem.component.AvatarSize
import app.cragpack.designsystem.component.AvatarStack
import app.cragpack.designsystem.component.CtIconButton
import app.cragpack.designsystem.component.CtScreen
import app.cragpack.designsystem.component.CtText
import app.cragpack.designsystem.component.CtaDock
import app.cragpack.designsystem.component.EmptyState
import app.cragpack.designsystem.component.ListRow
import app.cragpack.designsystem.component.NowBadge
import app.cragpack.designsystem.component.PhotoSurface
import app.cragpack.designsystem.component.SyncStatus
import app.cragpack.designsystem.component.SyncStatusPill
import app.cragpack.designsystem.component.TopBar
import app.cragpack.designsystem.icon.LucideIcon
import app.cragpack.designsystem.theme.CragpackTheme
import app.cragpack.designsystem.tokens.SizeTokens
import app.cragpack.domain.format.formatDateRange
import app.cragpack.domain.format.formatMonth
import app.cragpack.domain.trip.Person
import app.cragpack.domain.trip.TripTiming
import app.cragpack.domain.trip.homeOrder
import app.cragpack.domain.trip.timing
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

data class TripCardUi(
    val id: String,
    val name: String,
    /** "Fontainebleau · 17–21 Apr" */
    val place: String,
    val timing: TripTiming,
    val crew: List<Person>,
    /** Past trips: "Oct · 31 sends" */
    val pastLine: String,
)

class TripsViewModel(graph: AppGraph) : ViewModel() {
    /** null while loading. */
    val trips = graph.trips.observeTrips().map { items ->
        val today = graph.today()
        items.sortedWith(compareBy(homeOrder(today)) { it.trip }).map { (trip, crew, sends) ->
            TripCardUi(
                id = trip.id,
                name = trip.name,
                place = listOf(trip.crag, formatDateRange(trip.start, trip.end)).filter { it.isNotBlank() }.joinToString(" · "),
                timing = trip.timing(today),
                crew = crew,
                pastLine = "${formatMonth(trip.start, today)} · $sends ${if (sends == 1) "send" else "sends"}",
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}

/** Screens 15 (first launch) and 01. */
@Composable
fun TripsScreen(vm: TripsViewModel, onNewTrip: () -> Unit, onOpenTrip: (String) -> Unit, onMore: () -> Unit) {
    val trips by vm.trips.collectAsStateWithLifecycle()
    CtScreen(dock = { CtaDock("New trip", LucideIcon.Plus, onNewTrip) }) { bottom ->
        LazyColumn(
            contentPadding = PaddingValues(start = SizeTokens.edge, end = SizeTokens.edge, bottom = bottom.calculateBottomPadding()),
            verticalArrangement = Arrangement.spacedBy(SizeTokens.gap * 2),
        ) {
            item {
                TopBar(
                    // ponytail: avatar opens My logbook once that screen exists.
                    leading = { Avatar("", 1, size = AvatarSize.Xl) },
                    center = { SyncStatusPill(SyncStatus.OnThisPhone) },
                    trailing = { CtIconButton(LucideIcon.EllipsisVertical, "More", onMore) },
                )
            }
            item { CtText("Trips", Modifier.padding(top = 24.dp, start = 4.dp, end = 4.dp), style = CragpackTheme.type.h1) }
            val list = trips
            when {
                list == null -> Unit
                // ponytail: "Paste invite link" arrives with accounts (Phase 2).
                list.isEmpty() -> item { EmptyState("No trips yet", Modifier.fillMaxWidth().height(320.dp)) }
                else -> items(list, key = { it.id }) { trip ->
                    when (trip.timing) {
                        is TripTiming.Now -> NowCard(trip, trip.timing) { onOpenTrip(trip.id) }
                        is TripTiming.Upcoming -> UpcomingCard(trip) { onOpenTrip(trip.id) }
                        TripTiming.Past -> PastRow(trip) { onOpenTrip(trip.id) }
                    }
                }
            }
        }
    }
}

@Composable
private fun NowCard(trip: TripCardUi, now: TripTiming.Now, onClick: () -> Unit) {
    PhotoCard(Modifier.height(290.dp), onClick) {
        NowBadge("Now · day ${now.day}/${now.days}", Modifier.align(Alignment.TopStart).padding(12.dp))
        Column(Modifier.align(Alignment.BottomStart).padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            CtText(trip.name, style = CragpackTheme.type.h2, maxLines = 2)
            CtText(trip.place, maxLines = 1)
            AvatarStack(trip.crew.map { it.name to it.colorIndex })
        }
    }
}

@Composable
private fun UpcomingCard(trip: TripCardUi, onClick: () -> Unit) {
    // ponytail: the Download button arrives with the offline pack (Phase 3).
    PhotoCard(Modifier.height(150.dp), onClick) {
        Column(Modifier.align(Alignment.BottomStart).padding(20.dp)) {
            CtText(trip.name, style = CragpackTheme.type.h3, maxLines = 1)
            CtText(trip.place, maxLines = 1)
        }
    }
}

@Composable
private fun PastRow(trip: TripCardUi, onClick: () -> Unit) {
    ListRow(
        title = trip.name,
        meta = trip.pastLine,
        onClick = onClick,
        leading = { PhotoSurface(Modifier.size(54.dp), CircleShape) },
    )
}

@Composable
private fun PhotoCard(modifier: Modifier, onClick: () -> Unit, content: @Composable BoxScope.() -> Unit) =
    PhotoSurface(modifier.fillMaxWidth(), onClick = onClick, content = content)
