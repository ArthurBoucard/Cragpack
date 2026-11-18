package app.cragpack.designsystem.component

import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.cragpack.designsystem.icon.LucideIcon
import app.cragpack.designsystem.theme.CragpackTheme
import app.cragpack.designsystem.theme.LocalContentColor
import app.cragpack.designsystem.tokens.IconTokens
import org.jetbrains.compose.resources.vectorResource

@Composable
fun CtText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = CragpackTheme.type.body,
    color: Color = LocalContentColor.current,
    maxLines: Int = Int.MAX_VALUE,
    textAlign: TextAlign = TextAlign.Unspecified,
) = BasicText(text, modifier, style.copy(color = color, textAlign = textAlign), overflow = TextOverflow.Ellipsis, maxLines = maxLines)

/** Lucide icon, 24 by default (18 small). Pass a [contentDescription] unless a label sits next to it. */
@Composable
fun CtIcon(
    icon: LucideIcon,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    size: Dp = IconTokens.size,
    tint: Color = LocalContentColor.current,
) = Image(vectorResource(icon.res), contentDescription, modifier.size(size), colorFilter = ColorFilter.tint(tint))

/**
 * Shared touch behaviour from the design system "States": pressed = scale 0.97, disabled = 40% opacity,
 * focus = 3-wide ink outline. No ripple.
 */
@Composable
internal fun Modifier.ctClickable(enabled: Boolean, role: Role, shape: Shape, onClick: () -> Unit): Modifier {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val focused by interaction.collectIsFocusedAsState()
    val ink = CragpackTheme.colors.ink
    return this
        .graphicsLayer { if (pressed) { scaleX = 0.97f; scaleY = 0.97f } }
        .alpha(if (enabled) 1f else 0.4f)
        .then(if (focused) Modifier.border(3.dp, ink, shape) else Modifier)
        .clickable(interaction, indication = null, enabled = enabled, role = role, onClick = onClick)
}
