package app.cragpack.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
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
