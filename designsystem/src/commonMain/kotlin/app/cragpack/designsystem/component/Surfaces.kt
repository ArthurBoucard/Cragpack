package app.cragpack.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.cragpack.designsystem.theme.CragpackTheme
import app.cragpack.designsystem.theme.DarkContent
import app.cragpack.designsystem.tokens.ColorTokens
import app.cragpack.designsystem.tokens.RadiusTokens
import app.cragpack.designsystem.tokens.SizeTokens
import kotlin.math.sqrt

/** Design rule 2: a dashed outline means missing or not done yet. */
fun Modifier.dashedBorder(color: Color, radius: Dp, width: Dp = 2.dp) = drawBehind {
    val w = width.toPx()
    val r = radius.toPx().coerceAtMost((size.minDimension - w) / 2)
    drawRoundRect(
        color = color,
        topLeft = Offset(w / 2, w / 2),
        size = Size(size.width - w, size.height - w),
        cornerRadius = CornerRadius(r),
        style = Stroke(w, pathEffect = PathEffect.dashPathEffect(floatArrayOf(3 * w, 2.5f * w))),
    )
}

/** `.card` — surface with an outline, or dashed and empty when [todo]. */
@Composable
fun CtCard(
    modifier: Modifier = Modifier,
    todo: Boolean = false,
    radius: Dp = RadiusTokens.lg,
    onClick: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    val c = CragpackTheme.colors
    val shape = RoundedCornerShape(radius)
    Box(
        modifier
            .then(if (onClick != null) Modifier.ctClickable(true, Role.Button, shape, onClick) else Modifier)
            .then(
                if (todo) Modifier.dashedBorder(c.ink, radius)
                else Modifier.clip(shape).background(c.surface).border(1.dp, c.line, shape),
            ),
        content = content,
    )
}

/**
 * `.photo` — a dark photo surface: the striped placeholder (a real cover image comes with media),
 * a scrim so text stays readable, and dark colours inside in both themes.
 */
@Composable
fun PhotoSurface(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(RadiusTokens.xl),
    onClick: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit = {},
) {
    val stripe = with(LocalDensity.current) { 24.dp.toPx() / sqrt(2f) }
    val stripes = remember(stripe) {
        Brush.linearGradient(
            0f to ColorTokens.photoStripeA, 0.5f to ColorTokens.photoStripeA,
            0.5f to ColorTokens.photoStripeB, 1f to ColorTokens.photoStripeB,
            start = Offset.Zero, end = Offset(stripe, stripe), tileMode = TileMode.Repeated,
        )
    }
    val scrim = ColorTokens.photoScrim
    Box(
        modifier
            .then(if (onClick != null) Modifier.ctClickable(true, Role.Button, shape, onClick) else Modifier)
            .clip(shape)
            .background(stripes)
            .background(Brush.verticalGradient(0f to scrim.copy(alpha = 0.3f), 0.4f to scrim.copy(alpha = 0f), 1f to scrim)),
    ) {
        DarkContent { content() }
    }
}

/** `.tile` — label on top, big number at the bottom, optional corner action. */
@Composable
fun StatTile(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    unit: String? = null,
    todo: Boolean = false,
    onClick: (() -> Unit)? = null,
    action: (@Composable () -> Unit)? = null,
) = CtCard(modifier, todo = todo, onClick = onClick) {
    Column(
        Modifier.fillMaxWidth().heightIn(min = 104.dp).padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        CtText(label, color = CragpackTheme.colors.ink2, maxLines = 1)
        BigNumber(value, unit)
    }
    if (action != null) Box(Modifier.align(Alignment.TopEnd).padding(10.dp)) { action() }
}

/** `.big` with its small trailing unit: "18 · 6 unplanned". */
@Composable
fun BigNumber(value: String, unit: String? = null, modifier: Modifier = Modifier, xl: Boolean = false) {
    val t = CragpackTheme.type
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        CtText(value, Modifier.alignByBaseline(), style = if (xl) t.bigXl else t.big, maxLines = 1)
        if (unit != null) {
            CtText(
                unit,
                Modifier.alignByBaseline(),
                style = t.body.copy(fontSize = if (xl) t.body.fontSize * 16 / 15 else t.body.fontSize * 14 / 15),
                color = CragpackTheme.colors.ink2,
                maxLines = 1,
            )
        }
    }
}

/** `.empty` — inside a dashed card: a title and one action. */
@Composable
fun EmptyState(title: String, modifier: Modifier = Modifier, action: (@Composable () -> Unit)? = null) {
    Box(modifier.dashedBorder(CragpackTheme.colors.ink, RadiusTokens.lg).padding(24.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
            CtText(title, style = CragpackTheme.type.h3, textAlign = TextAlign.Center)
            action?.invoke()
        }
    }
}

/** `.row` — leading (grade, avatar, thumb), title and meta, trailing action. Dashed when [todo]. */
@Composable
fun ListRow(
    title: String,
    modifier: Modifier = Modifier,
    meta: String? = null,
    todo: Boolean = false,
    onClick: (() -> Unit)? = null,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable RowScope.() -> Unit)? = null,
) = CtCard(modifier.fillMaxWidth().heightIn(min = SizeTokens.row), todo = todo, radius = 38.dp, onClick = onClick) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = SizeTokens.row).padding(start = 10.dp, end = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        leading?.invoke()
        Column(Modifier.weight(1f)) {
            CtText(title, style = CragpackTheme.type.rowName, maxLines = 1)
            if (meta != null) CtText(meta, Modifier.padding(top = 4.dp), style = CragpackTheme.type.label, color = CragpackTheme.colors.ink2, maxLines = 1)
        }
        trailing?.invoke(this)
    }
}
