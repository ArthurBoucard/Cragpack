package app.cragpack.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.cragpack.designsystem.icon.LucideIcon
import app.cragpack.designsystem.theme.CragpackTheme
import app.cragpack.designsystem.theme.LocalContentColor
import app.cragpack.designsystem.tokens.RadiusTokens
import app.cragpack.designsystem.tokens.SizeTokens

private val semibold @Composable get() = CragpackTheme.type.body.copy(fontWeight = FontWeight.SemiBold)

/**
 * `.chip` — toggle or single choice; the selected one is solid. Without [text] it's a round icon chip
 * (then [contentDescription] names it). Inert when [onClick] is null.
 */
@Composable
fun CtChip(
    text: String?,
    selected: Boolean,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    icon: LucideIcon? = null,
    contentDescription: String? = null,
) {
    val c = CragpackTheme.colors
    Row(
        modifier
            .height(46.dp)
            .then(if (text == null) Modifier.size(46.dp) else Modifier)
            .then(if (onClick != null) Modifier.ctClickable(true, Role.Button, CircleShape, onClick) else Modifier)
            .semantics { this.selected = selected; if (contentDescription != null) this.contentDescription = contentDescription }
            .clip(CircleShape)
            .background(if (selected) c.solid else c.surface)
            .then(if (selected) Modifier else Modifier.border(1.dp, c.line, CircleShape))
            .padding(horizontal = if (text == null) 0.dp else 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CompositionLocalProvider(LocalContentColor provides if (selected) c.onSolid else c.ink) {
            if (icon != null) CtIcon(icon, null, size = 20.dp)
            if (text != null) CtText(text, style = semibold, maxLines = 1)
        }
    }
}

/** `.seg` — 2 to 4 options, exactly one selected. */
@Composable
fun CtSegmented(options: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val c = CragpackTheme.colors
    Row(
        modifier.fillMaxWidth().clip(CircleShape).background(c.surface).border(1.dp, c.line, CircleShape).padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        options.forEachIndexed { i, label ->
            val on = i == selected
            Box(
                Modifier
                    .weight(1f)
                    .height(46.dp)
                    .ctClickable(true, Role.RadioButton, CircleShape) { onSelect(i) }
                    .semantics { this.selected = on }
                    .clip(CircleShape)
                    .background(if (on) c.solid else Color.Transparent),
                contentAlignment = Alignment.Center,
            ) {
                CtText(label, style = semibold, color = if (on) c.onSolid else c.ink, maxLines = 1)
            }
        }
    }
}

/** The ★ "I want to try it". Filled accent star on solid when on. */
@Composable
fun StarToggle(on: Boolean, onToggle: () -> Unit, contentDescription: String, modifier: Modifier = Modifier) {
    val c = CragpackTheme.colors
    Box(
        modifier
            .size(SizeTokens.tap)
            .ctClickable(true, Role.Checkbox, CircleShape, onToggle)
            .semantics { this.contentDescription = contentDescription; selected = on }
            .clip(CircleShape)
            .background(if (on) c.solid else c.surface2)
            .then(if (on) Modifier else Modifier.border(1.dp, c.line, CircleShape)),
        contentAlignment = Alignment.Center,
    ) {
        if (on) CtIcon(LucideIcon.StarFilled, null, tint = c.accent)
        CtIcon(LucideIcon.Star, null, tint = if (on) c.onSolid else c.ink)
    }
}

/** `.row` + `.radio` — one choice in a sheet; the selected row gets an ink outline. */
@Composable
fun CtRadioRow(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = CragpackTheme.colors
    val shape = RoundedCornerShape(RadiusTokens.lg)
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .ctClickable(true, Role.RadioButton, shape, onClick)
            .semantics { this.selected = selected }
            .clip(shape)
            .background(c.surface)
            .border(if (selected) 2.dp else 1.dp, if (selected) c.ink else c.line, shape)
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CtText(text, Modifier.weight(1f), style = CragpackTheme.type.rowName.copy(fontSize = 16.sp), maxLines = 1)
        Box(Modifier.size(26.dp).border(2.dp, c.ink, CircleShape), contentAlignment = Alignment.Center) {
            if (selected) Box(Modifier.size(11.dp).clip(CircleShape).background(c.ink))
        }
    }
}

/** `.opt` — a big choice button with an icon over its label (Add beta: Video, Photo, Topo, Note). */
@Composable
fun CtOption(label: String, icon: LucideIcon, onClick: () -> Unit, modifier: Modifier = Modifier) =
    CtCard(modifier.height(84.dp), radius = RadiusTokens.lg, onClick = onClick) {
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
            CtIcon(icon, null)
            CtText(label, Modifier.padding(top = 6.dp), style = semibold.copy(fontSize = 14.sp), maxLines = 1)
        }
    }

/** A row of options that wraps; used for chips that can outgrow one line (sectors). */
@Composable
fun ChipRow(modifier: Modifier = Modifier, content: @Composable () -> Unit) =
    androidx.compose.foundation.layout.FlowRow(
        modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) { content() }
