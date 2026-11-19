package app.cragpack.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import app.cragpack.designsystem.tokens.SizeTokens
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.cragpack.designsystem.theme.CragpackTheme
import app.cragpack.designsystem.tokens.ColorTokens

enum class GradeChipSize(internal val box: Dp, internal val text: TextUnit) {
    Xl(68.dp, 24.sp), Md(54.dp, 18.sp), Sm(44.dp, 15.sp), Xs(40.dp, 13.sp)
}

/**
 * `.grade` — round grade chip. [band] is 1..9 (the domain Grade gives it).
 * The grade text is always shown, so colour is never the only signal.
 */
@Composable
fun GradeChip(text: String, band: Int, modifier: Modifier = Modifier, size: GradeChipSize = GradeChipSize.Md) =
    GradeShape(text, band, CircleShape, size.text, modifier.size(size.box))

/** `.gtag` — small 24-high grade pill used inline in rows and meta lines. */
@Composable
fun GradeTag(text: String, band: Int, modifier: Modifier = Modifier) =
    GradeShape(text, band, RoundedCornerShape(12.dp), 13.sp, modifier.height(24.dp), padH = 9.dp)

/**
 * `.gpick` — every grade, two taps away: a row of numbers (1…9, in band colours), then that number's
 * grades as 4 columns of pills. The selected grade gets an outline ring. [grades] are (text, band) in sort order.
 * V grades (VB, V0- … V17) don't group by first digit (V1 and V10 would share a row), so they show as one grid.
 */
@Composable
fun GradePicker(grades: List<Pair<String, Int>>, selected: String?, onSelect: (String) -> Unit, modifier: Modifier = Modifier) {
    val ink = CragpackTheme.colors.ink
    val flat = remember(grades) { grades.any { (text, _) -> !text.first().isDigit() } }
    val groups = remember(grades) { if (flat) mapOf("" to grades) else grades.groupBy { (text, _) -> numberOf(text) } }
    var open by rememberSaveable(grades) { mutableStateOf(if (flat) "" else selected?.let(::numberOf) ?: groups.keys.firstOrNull { it == "6" } ?: groups.keys.first()) }
    LaunchedEffect(selected) { if (selected != null && !flat) open = numberOf(selected) }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(SizeTokens.gap)) {
        if (!flat) Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            groups.forEach { (number, list) ->
                val on = number == open
                GradeShape(
                    number, list.first().second, CircleShape, 15.sp,
                    Modifier
                        .weight(1f)
                        .height(SizeTokens.tapSm)
                        .alpha(if (on) 1f else 0.35f)
                        .ctClickable(true, Role.Tab, CircleShape) { open = number }
                        .semantics { this.selected = on; contentDescription = "Grades $number" },
                )
            }
        }
        groups[open].orEmpty().chunked(4).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(SizeTokens.gap)) {
                row.forEach { (text, band) ->
                    val on = text == selected
                    GradeShape(
                        text, band, CircleShape, 17.sp,
                        Modifier
                            .weight(1f)
                            .height(SizeTokens.tap)
                            .then(if (on) Modifier.ring(ink) else Modifier)
                            .ctClickable(true, Role.RadioButton, CircleShape) { onSelect(text) }
                            .semantics { this.selected = on },
                    )
                }
                repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

/** "6A+" → "6"; grades without a digit (VB) keep their first letter. */
private fun numberOf(grade: String): String = (grade.firstOrNull { it.isDigit() } ?: grade.first()).toString()

/** `.pyramid` — one bar per band row: label, bar in the band colour (length = value / max), count. */
@Composable
fun GradePyramid(rows: List<Triple<String, Int, Int>>, modifier: Modifier = Modifier) {
    val max = rows.maxOfOrNull { it.third }?.coerceAtLeast(1) ?: 1
    val style = CragpackTheme.type.h3.copy(fontSize = 13.sp, lineHeight = 13.sp, letterSpacing = 0.sp)
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        rows.forEach { (label, band, count) ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                CtText(label, Modifier.width(64.dp), style = style, maxLines = 1)
                Box(Modifier.weight(1f)) {
                    GradeShape("", band, CircleShape, 13.sp, Modifier.fillMaxWidth(count.toFloat() / max).height(16.dp))
                }
                CtText("$count", Modifier.width(28.dp), style = style, textAlign = androidx.compose.ui.text.style.TextAlign.End, maxLines = 1)
            }
        }
    }
}

/** 3-wide outline drawn 3 outside the pill, like CSS `outline-offset: 3px`. */
private fun Modifier.ring(color: Color) = drawBehind {
    val w = 3.dp.toPx()
    val inset = 3.dp.toPx() + w / 2
    drawRoundRect(
        color,
        topLeft = Offset(-inset, -inset),
        size = Size(size.width + 2 * inset, size.height + 2 * inset),
        cornerRadius = CornerRadius(size.height / 2 + inset),
        style = Stroke(w),
    )
}

@Composable
private fun GradeShape(text: String, band: Int, shape: Shape, fontSize: TextUnit, modifier: Modifier, padH: Dp = 0.dp) {
    require(band in 1..9) { "Grade band must be 1..9, was $band" }
    val dark = Color(0xFF1C1814)
    Box(
        modifier
            .then(
                if (band == 9) {
                    // Gold, 135°: top-left to bottom-right.
                    Modifier.background(
                        Brush.linearGradient(*ColorTokens.gradeGold.toTypedArray(), start = Offset.Zero, end = Offset.Infinite),
                        shape,
                    )
                } else {
                    Modifier.background(ColorTokens.gradeBands[band - 1], shape)
                },
            )
            .then(if (band == 8) Modifier.border(2.dp, CragpackTheme.colors.ink, shape) else Modifier)
            .padding(horizontal = padH),
        contentAlignment = Alignment.Center,
    ) {
        CtText(
            text,
            style = CragpackTheme.type.h3.copy(fontSize = fontSize, lineHeight = fontSize, letterSpacing = 0.sp),
            color = if (band == 8) Color(0xFFF3EBE0) else dark,
            maxLines = 1,
        )
    }
}
