package app.cragpack.designsystem.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.cragpack.designsystem.icon.LucideIcon
import app.cragpack.designsystem.theme.CragpackTheme
import app.cragpack.designsystem.theme.LocalContentColor
import app.cragpack.designsystem.tokens.RadiusTokens
import app.cragpack.designsystem.tokens.SizeTokens

@Composable
private fun Dock(modifier: Modifier = Modifier, content: @Composable RowScope.() -> Unit) {
    val shape = RoundedCornerShape(RadiusTokens.lg)
    val c = CragpackTheme.colors
    Row(
        modifier
            .fillMaxWidth()
            .height(SizeTokens.dock)
            .shadow(24.dp, shape, ambientColor = Color.Black, spotColor = Color.Black)
            .background(c.solid, shape)
            .padding(6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        CompositionLocalProvider(LocalContentColor provides c.onSolid) { content() }
    }
}

/** `.dock.cta` — the screen's one primary action: accent icon on the left, centred label. */
@Composable
fun CtaDock(label: String, icon: LucideIcon, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val c = CragpackTheme.colors
    Dock(modifier.ctClickable(enabled, Role.Button, RoundedCornerShape(RadiusTokens.lg), onClick)) {
        Box(Modifier.size(SizeTokens.tap).clip(CircleShape).background(c.accent), contentAlignment = Alignment.Center) {
            CtIcon(icon, null, tint = c.onAccent)
        }
        CtText(
            label,
            Modifier.weight(1f).padding(end = SizeTokens.tap),
            style = CragpackTheme.type.body.copy(fontSize = 16.sp, fontWeight = FontWeight.SemiBold),
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
    }
}

/** `.dock.log` — Attempt / Sent / Flash. The chosen one ([on]) is accent with a tick. */
@Composable
fun LogDock(labels: List<String>, on: Int?, onClick: (Int) -> Unit, modifier: Modifier = Modifier) {
    val c = CragpackTheme.colors
    val shape = RoundedCornerShape(26.dp)
    Dock(modifier) {
        labels.forEachIndexed { i, label ->
            val chosen = i == on
            Row(
                Modifier
                    .weight(1f)
                    .height(SizeTokens.tap)
                    .ctClickable(true, Role.Button, shape) { onClick(i) }
                    .semantics { selected = chosen }
                    .clip(shape)
                    .background(if (chosen) c.accent else c.bg),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
            ) {
                CompositionLocalProvider(LocalContentColor provides if (chosen) c.onAccent else c.ink) {
                    if (chosen) CtIcon(LucideIcon.Check, null, size = 20.dp)
                    CtText(label, style = CragpackTheme.type.body.copy(fontWeight = FontWeight.Bold), maxLines = 1)
                }
            }
        }
    }
}

/**
 * `.toast` — a quick result with one action (Undo), just above the dock. Shows while [message] is set,
 * and calls [onTimeout] after a few seconds. Place it in [CtScreen]'s overlay slot.
 */
@Composable
fun BoxScope.CtToast(message: String?, actionLabel: String, onAction: () -> Unit, onTimeout: () -> Unit) {
    val c = CragpackTheme.colors
    LaunchedEffect(message) { if (message != null) { delay(6_000); onTimeout() } }
    var last by remember { mutableStateOf("") }
    if (message != null) last = message
    AnimatedVisibility(
        message != null,
        Modifier.align(Alignment.BottomCenter).padding(start = SizeTokens.edge, end = SizeTokens.edge, bottom = dockBottom() + 76.dp),
        enter = fadeIn() + slideInVertically { it / 2 },
        exit = fadeOut(),
    ) {
        val shape = RoundedCornerShape(28.dp)
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .shadow(16.dp, shape, ambientColor = Color.Black, spotColor = Color.Black)
                .background(c.raised, shape)
                .border(1.dp, c.line, shape)
                .padding(start = 18.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            CtIcon(LucideIcon.CircleCheck, null, tint = c.accent)
            CtText(last, Modifier.weight(1f), style = CragpackTheme.type.body.copy(fontWeight = FontWeight.SemiBold), maxLines = 1)
            CtButton(actionLabel, onAction, style = CtButtonStyle.Ghost, size = CtButtonSize.Sm, icon = LucideIcon.Undo2)
        }
    }
}

/** `.dock` — the 5-tab bar inside a trip. The current tab is filled with bg plus an accent dot. */
@Composable
fun TabDock(tabs: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val c = CragpackTheme.colors
    val style = CragpackTheme.type.body.copy(fontWeight = FontWeight.SemiBold)
    val shape = RoundedCornerShape(26.dp)
    Dock(modifier) {
        tabs.forEachIndexed { i, tab ->
            val on = i == selected
            Row(
                (if (on) Modifier else Modifier.weight(1f))
                    .height(SizeTokens.tap)
                    .semantics { this.selected = on }
                    .ctClickable(true, Role.Tab, shape) { onSelect(i) }
                    .then(if (on) Modifier.clip(shape).background(c.bg).padding(horizontal = 16.dp) else Modifier),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            ) {
                if (on) Box(Modifier.size(8.dp).clip(CircleShape).background(c.accent))
                CtText(tab, style = style, color = if (on) c.ink else c.onSolid, maxLines = 1)
            }
        }
    }
}
