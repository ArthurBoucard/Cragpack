package app.cragpack.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.cragpack.designsystem.theme.CragpackTheme
import app.cragpack.designsystem.theme.LightContent
import app.cragpack.designsystem.tokens.RadiusTokens

/** `.sheet` — the recap card: light in both themes so it reads like the shared image. */
@Composable
fun RecapSheet(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) = LightContent {
    val c = CragpackTheme.colors
    val shape = RoundedCornerShape(RadiusTokens.xl)
    Column(
        modifier.fillMaxWidth().clip(shape).background(c.bg).border(1.dp, c.line, shape).padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        content = content,
    )
}

/** One column of the sends-per-day chart: a day label and its sends, or a rest day. */
data class DayBar(val label: String, val sends: Int, val rest: Boolean)

/** `.chart.plot` — sends per day as accent bars (height = sends / most sends); rest days get a thin "rest" bar. */
@Composable
fun SendsPerDay(days: List<DayBar>, modifier: Modifier = Modifier) {
    val c = CragpackTheme.colors
    val max = days.maxOfOrNull { it.sends }?.coerceAtLeast(1) ?: 1
    val label = CragpackTheme.type.label
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        days.forEach { d ->
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Box(Modifier.height(80.dp).fillMaxWidth(), contentAlignment = Alignment.BottomCenter) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        CtText(if (d.rest) "rest" else "${d.sends}", style = label, color = if (d.rest) c.ink2 else c.ink, maxLines = 1)
                        val height = if (d.rest || d.sends == 0) 6.dp else 54.dp * d.sends / max
                        Box(
                            Modifier.fillMaxWidth().height(height).clip(RoundedCornerShape(if (height < 20.dp) 3.dp else 10.dp))
                                .background(if (d.rest || d.sends == 0) c.surface2 else c.accent),
                        )
                    }
                }
                CtText(d.label, style = label, color = c.ink2, maxLines = 1, textAlign = TextAlign.Center)
            }
        }
    }
}

/** `.stat` — one climber in the recap: avatar, sends and hardest grade. [me] gets an ink outline. */
@Composable
fun ClimberStat(
    name: String,
    colorIndex: Int,
    sends: Int,
    hardest: Pair<String, Int>?,
    me: Boolean,
    modifier: Modifier = Modifier,
) = CtCard(modifier.height(112.dp).then(if (me) Modifier.border(2.dp, CragpackTheme.colors.ink, RoundedCornerShape(RadiusTokens.lg)) else Modifier)) {
    Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Avatar(name, colorIndex)
        CtText("$sends", style = CragpackTheme.type.big.copy(fontWeight = FontWeight.Bold), maxLines = 1)
        if (hardest != null) GradeTag(hardest.first, hardest.second)
    }
}
