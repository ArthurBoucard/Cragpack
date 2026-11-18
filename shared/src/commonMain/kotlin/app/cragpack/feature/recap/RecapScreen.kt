package app.cragpack.feature.recap

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.cragpack.data.AppGraph
import app.cragpack.data.MediaFiles
import app.cragpack.domain.trip.Trip
import app.cragpack.designsystem.component.BigNumber
import app.cragpack.designsystem.component.ClimberStat
import app.cragpack.designsystem.component.CtCard
import app.cragpack.designsystem.component.CtIconButton
import app.cragpack.designsystem.component.CtScreen
import app.cragpack.designsystem.component.CtText
import app.cragpack.designsystem.component.DayBar
import app.cragpack.designsystem.component.GradePyramid
import app.cragpack.designsystem.component.LightStatusBarIcons
import app.cragpack.designsystem.component.PhotoSurface
import app.cragpack.designsystem.component.RecapSheet
import app.cragpack.designsystem.component.SendsPerDay
import app.cragpack.designsystem.component.TopBar
import app.cragpack.designsystem.icon.LucideIcon
import app.cragpack.designsystem.theme.CragpackTheme
import app.cragpack.designsystem.tokens.RadiusTokens
import app.cragpack.designsystem.tokens.SizeTokens
import app.cragpack.domain.grade.GradeScale
import app.cragpack.domain.log.pyramid
import app.cragpack.domain.objective.ClimbStyle
import app.cragpack.feature.entryScale
import app.cragpack.feature.text
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.datetime.DayOfWeek

data class RecapUi(val trip: Trip, val crewSize: Int, val recap: Recap)

class RecapViewModel(graph: AppGraph, tripId: String) : ViewModel() {
    val myId = graph.myId

    /** null while loading, or if the trip is gone. */
    val state = combine(
        graph.trips.observeTrip(tripId),
        graph.logs.observeTripLog(tripId),
        graph.plan.observeDays(tripId),
    ) { detail, log, days ->
        detail?.let { RecapUi(it.trip, it.crew.size, recap(log, days, it.crew)) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}

/** Trip recap (08). ponytail: "Share recap" (the share card, 28) arrives in Phase 4. */
@Composable
fun RecapScreen(vm: RecapViewModel, onBack: () -> Unit) {
    val ui by vm.state.collectAsStateWithLifecycle()
    CtScreen(statusBarStrip = false) { bottom ->
        val (trip, crewSize, r) = ui ?: return@CtScreen
        LightStatusBarIcons()
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(SizeTokens.gap * 2)) {
            PhotoSurface(
                Modifier.fillMaxWidth(),
                RoundedCornerShape(bottomStart = RadiusTokens.xl, bottomEnd = RadiusTokens.xl),
                imagePath = trip.coverPath?.let(MediaFiles::absolute),
            ) {
                Column(Modifier.padding(start = SizeTokens.edge, end = SizeTokens.edge, bottom = SizeTokens.edge)) {
                    TopBar(leading = { CtIconButton(LucideIcon.ChevronLeft, "Back", onBack) })
                    Spacer(Modifier.height(120.dp))
                    CtText(trip.name, style = CragpackTheme.type.titleXl, maxLines = 2)
                    CtText(
                        "${plural(trip.days, "day")} · ${plural(crewSize, "climber")}",
                        Modifier.padding(top = 8.dp),
                    )
                }
            }
            Column(Modifier.padding(horizontal = SizeTokens.edge), verticalArrangement = Arrangement.spacedBy(SizeTokens.gap)) {
                RecapSheet {
                    Row(verticalAlignment = Alignment.Bottom) {
                        BigNumber("${r.sends}", if (r.sends == 1) "send" else "sends", Modifier.weight(1f), xl = true)
                        CtText(plural(r.flashes, "flash", "flashes"), Modifier.padding(bottom = 6.dp))
                    }
                    // Labels in the trip's own grades: boulder grades if it has any, else route grades.
                    val scale = if (r.sentGrades.any { it.isBoulder }) ClimbStyle.Boulder.entryScale() else GradeScale.French
                    GradePyramid(pyramid(r.sentGrades, scale).map { Triple(it.label, it.band, it.count) })
                }
                CtCard(Modifier.fillMaxWidth()) {
                    SendsPerDay(
                        r.days.map { DayBar(shortDay(it.date.dayOfWeek), it.sends, it.isRest) },
                        Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
                    )
                }
                r.climbers.chunked(4).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(SizeTokens.gap)) {
                        row.forEach { c ->
                            ClimberStat(
                                c.person.name, c.person.colorIndex, c.sends,
                                c.hardest?.let { it.text() to it.band },
                                me = c.person.id == vm.myId,
                                modifier = Modifier.weight(1f),
                            )
                        }
                        repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
            Spacer(Modifier.height(bottom.calculateBottomPadding()))
        }
    }
}

private fun shortDay(d: DayOfWeek) = d.name.take(3).lowercase().replaceFirstChar { it.uppercase() }

private fun plural(n: Int, one: String, many: String = one + "s") = "$n ${if (n == 1) one else many}"
