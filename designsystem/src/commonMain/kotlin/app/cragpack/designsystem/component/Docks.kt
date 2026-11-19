package app.cragpack.designsystem.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.unit.IntOffset
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
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

/**
 * `.dock.cta` — the screen's one primary action, as slide-to-confirm: drag the accent knob to the far
 * end of the bar. Let go early and it springs back; a tap nudges it to show it slides. Screen readers
 * get a plain button.
 */
@Composable
fun CtaDock(label: String, icon: LucideIcon, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val c = CragpackTheme.colors
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    val nudge = with(LocalDensity.current) { 28.dp.toPx() }
    var x by remember { mutableFloatStateOf(0f) }
    var travel by remember { mutableFloatStateOf(0f) }
    var busy by remember { mutableStateOf(false) }
    val progress = if (travel > 0f) x / travel else 0f
    fun slideTo(target: Float, after: suspend () -> Unit = {}) = scope.launch {
        busy = true
        animate(x, target, animationSpec = spring(Spring.DampingRatioLowBouncy, Spring.StiffnessMediumLow)) { v, _ -> x = v }
        after()
        busy = false
    }
    fun confirm() = slideTo(travel) {
        haptics.performHapticFeedback(HapticFeedbackType.Confirm)
        onClick()
        delay(700) // the screen usually leaves; if it stays, the knob comes home
        animate(x, 0f) { v, _ -> x = v }
    }
    Dock(
        modifier
            .alpha(if (enabled) 1f else 0.4f)
            .semantics(mergeDescendants = true) {
                role = Role.Button
                onClick(label) { if (enabled) onClick(); enabled }
                if (!enabled) disabled()
            }
            .draggable(
                rememberDraggableState { d -> if (!busy) x = (x + d).coerceIn(0f, travel) },
                Orientation.Horizontal,
                enabled = enabled,
                onDragStopped = { if (x >= travel * 0.85f) confirm() else slideTo(0f) },
            )
            .pointerInput(enabled) { if (enabled) detectTapGestures { if (!busy) slideTo(nudge) { animate(x, 0f) { v, _ -> x = v } } } },
    ) {
        Box(Modifier.weight(1f).fillMaxHeight().onSizeChanged { travel = (it.width - it.height).toFloat().coerceAtLeast(0f) }) {
            val knob = with(LocalDensity.current) { x.toDp() } + SizeTokens.tap
            // The trail the knob leaves: shows how far there is to go.
            Box(Modifier.align(Alignment.CenterStart).width(knob).height(SizeTokens.tap).clip(CircleShape).background(c.accent.copy(alpha = 0.3f)))
            CtText(
                label,
                Modifier.align(Alignment.Center).padding(horizontal = SizeTokens.tap).graphicsLayer { alpha = 1f - progress },
                style = CragpackTheme.type.body.copy(fontSize = 16.sp, fontWeight = FontWeight.SemiBold),
                textAlign = TextAlign.Center,
                maxLines = 1,
            )
            CtIcon(
                LucideIcon.ChevronRight, null,
                Modifier.align(Alignment.CenterEnd).padding(end = 14.dp).graphicsLayer { alpha = 0.5f * (1f - progress) },
                size = 20.dp,
            )
            Box(
                Modifier
                    .align(Alignment.CenterStart)
                    .offset { IntOffset(x.roundToInt(), 0) }
                    .size(SizeTokens.tap)
                    .clip(CircleShape)
                    .background(c.accent),
                contentAlignment = Alignment.Center,
            ) {
                CtIcon(icon, null, tint = c.onAccent)
            }
        }
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

/** `.dock` — the 5-tab bar inside a trip. The current tab is an accent pill, like the chosen log style. */
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
                    .then(if (on) Modifier.clip(shape).background(c.accent).padding(horizontal = 18.dp) else Modifier),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                CtText(tab, style = style, color = if (on) c.onAccent else c.onSolid, maxLines = 1)
            }
        }
    }
}
