package app.cragpack.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.cragpack.designsystem.icon.LucideIcon
import app.cragpack.designsystem.theme.CragpackTheme
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.minus
import kotlinx.datetime.plus

/**
 * Sheet to pick a start and end date on one month grid: tap the first day, then the last.
 * With [single], one tap picks one day (start = end).
 */
@Composable
fun BoxScope.DateRangeSheet(
    visible: Boolean,
    start: LocalDate?,
    end: LocalDate?,
    today: LocalDate,
    onDone: (LocalDate, LocalDate) -> Unit,
    onDismiss: () -> Unit,
    single: Boolean = false,
) {
    var from by remember(visible) { mutableStateOf(start) }
    var to by remember(visible) { mutableStateOf(end) }
    var month by remember(visible) { mutableStateOf((start ?: today).firstOfMonth()) }

    CtBottomSheet(visible, title = when { single -> "Date"; from == null || to != null -> "Trip dates"; else -> "Last day?" }, onDismiss = onDismiss) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CtIconButton(LucideIcon.ChevronLeft, "Previous month", { month = month.minus(DatePeriod(months = 1)) }, size = CtIconButtonSize.Sm)
            CtText(monthTitle(month), Modifier.weight(1f), style = CragpackTheme.type.title, textAlign = TextAlign.Center)
            CtIconButton(LucideIcon.ChevronRight, "Next month", { month = month.plus(DatePeriod(months = 1)) }, size = CtIconButtonSize.Sm)
        }
        MonthGrid(month, from, to, today) { d ->
            val s = from
            when {
                single -> { from = d; to = d }
                s == null || to != null -> { from = d; to = null }
                d < s -> from = d
                else -> to = d
            }
        }
        val s = from
        val e = to
        CtButton(
            "Done",
            onClick = { if (s != null && e != null) onDone(s, e) },
            style = CtButtonStyle.Accent,
            size = CtButtonSize.Wide,
            enabled = s != null && e != null,
        )
    }
}

@Composable
private fun MonthGrid(month: LocalDate, from: LocalDate?, to: LocalDate?, today: LocalDate, onPick: (LocalDate) -> Unit) {
    val c = CragpackTheme.colors
    val t = CragpackTheme.type
    val lead = month.dayOfWeek.isoDayNumber - 1 // Monday first
    val days = month.plus(DatePeriod(months = 1)).minus(DatePeriod(days = 1)).day
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row {
            listOf("M", "T", "W", "T", "F", "S", "S").forEach {
                CtText(it, Modifier.weight(1f), style = t.label, color = c.ink2, textAlign = TextAlign.Center)
            }
        }
        (0 until (lead + days + 6) / 7).forEach { week ->
            Row {
                (0 until 7).forEach { col ->
                    val n = week * 7 + col - lead + 1
                    Box(Modifier.weight(1f).heightIn(min = 44.dp), contentAlignment = Alignment.Center) {
                        if (n in 1..days) {
                            val d = LocalDate(month.year, month.month, n)
                            val edge = d == from || d == to
                            val inside = from != null && to != null && d > from && d < to
                            Box(
                                Modifier
                                    .size(44.dp)
                                    .aspectRatio(1f)
                                    .semantics { selected = edge || inside; contentDescription = d.toString() }
                                    .ctClickable(true, Role.Button, CircleShape) { onPick(d) }
                                    .clip(CircleShape)
                                    .background(if (edge) c.solid else if (inside) c.surface2 else androidx.compose.ui.graphics.Color.Transparent),
                                contentAlignment = Alignment.Center,
                            ) {
                                CtText(
                                    "$n",
                                    style = t.body.copy(fontWeight = if (d == today) FontWeight.Bold else FontWeight.Medium),
                                    color = if (edge) c.onSolid else if (d == today) c.accentInk else c.ink,
                                )
                            }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.padding(top = 4.dp))
    }
}

private fun LocalDate.firstOfMonth() = LocalDate(year, month, 1)

// ponytail: English month names; move to string resources with French.
private fun monthTitle(d: LocalDate) = d.month.name.lowercase().replaceFirstChar { it.uppercase() } + " " + d.year
