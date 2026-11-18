package app.cragpack.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.cragpack.designsystem.icon.LucideIcon
import app.cragpack.designsystem.theme.CragpackTheme
import app.cragpack.designsystem.theme.LocalContentColor
import app.cragpack.designsystem.tokens.SizeTokens

enum class CtButtonStyle { Solid, Accent, Ghost, Danger, DangerSolid }
enum class CtButtonSize { Md, Sm, Wide }

/** `.btn` — pill button. One primary (Accent) action per screen. */
@Composable
fun CtButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: CtButtonStyle = CtButtonStyle.Solid,
    size: CtButtonSize = CtButtonSize.Md,
    icon: LucideIcon? = null,
    enabled: Boolean = true,
) {
    val c = CragpackTheme.colors
    val (bg, fg, border) = when (style) {
        CtButtonStyle.Solid -> Triple(c.solid, c.onSolid, null)
        CtButtonStyle.Accent -> Triple(c.accent, c.onAccent, null)
        CtButtonStyle.Ghost -> Triple(c.surface, c.ink, 1.dp to c.line)
        CtButtonStyle.Danger -> Triple(Color.Transparent, c.dangerInk, 2.dp to c.danger)
        CtButtonStyle.DangerSolid -> Triple(c.danger, c.onDanger, null)
    }
    val sized = when (size) {
        CtButtonSize.Md -> modifier.height(48.dp)
        CtButtonSize.Sm -> modifier.height(40.dp)
        CtButtonSize.Wide -> modifier.fillMaxWidth().height(56.dp)
    }
    Row(
        sized
            .ctClickable(enabled, Role.Button, CircleShape, onClick)
            .clip(CircleShape)
            .background(bg)
            .then(if (border != null) Modifier.border(border.first, border.second, CircleShape) else Modifier)
            .padding(horizontal = if (size == CtButtonSize.Sm) 14.dp else 18.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CompositionLocalProvider(LocalContentColor provides fg) {
            if (icon != null) CtIcon(icon, contentDescription = null, size = 20.dp)
            CtText(
                text,
                style = CragpackTheme.type.body.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = if (size == CtButtonSize.Wide) 16.sp else 15.sp,
                ),
                maxLines = 1,
            )
        }
    }
}

enum class CtIconButtonStyle { Default, Accent, On }
enum class CtIconButtonSize { Md, Sm, Xl }

/** `.ib` — round icon button. [contentDescription] is required: icon-only buttons need a label. */
@Composable
fun CtIconButton(
    icon: LucideIcon,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: CtIconButtonStyle = CtIconButtonStyle.Default,
    size: CtIconButtonSize = CtIconButtonSize.Md,
    enabled: Boolean = true,
) {
    val c = CragpackTheme.colors
    val (bg, fg) = when (style) {
        CtIconButtonStyle.Default -> c.surface2 to c.ink
        CtIconButtonStyle.Accent -> c.accent to c.onAccent
        CtIconButtonStyle.On -> c.solid to c.onSolid
    }
    val box = when (size) {
        CtIconButtonSize.Md -> SizeTokens.tap
        CtIconButtonSize.Sm -> SizeTokens.tapSm
        CtIconButtonSize.Xl -> 76.dp
    }
    Box(
        modifier
            .size(box)
            .ctClickable(enabled, Role.Button, CircleShape, onClick)
            .semantics { this.contentDescription = contentDescription }
            .clip(CircleShape)
            .background(bg)
            .then(if (style == CtIconButtonStyle.Default) Modifier.border(1.dp, c.line, CircleShape) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        CtIcon(icon, contentDescription = null, size = if (size == CtIconButtonSize.Xl) 28.dp else 24.dp, tint = fg)
    }
}
