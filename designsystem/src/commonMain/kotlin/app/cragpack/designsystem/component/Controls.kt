package app.cragpack.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.max
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

/**
 * The ★ "I want to try it". On: a filled accent star on solid in Sunlight; on Dark, where solid is
 * cream and an orange star on it reads muddy, an accent disc with a dark star.
 */
@Composable
fun StarToggle(on: Boolean, onToggle: () -> Unit, contentDescription: String, modifier: Modifier = Modifier) {
    val c = CragpackTheme.colors
    Box(
        modifier
            .size(SizeTokens.tap)
            .ctClickable(true, Role.Checkbox, CircleShape, onToggle)
            .semantics { this.contentDescription = contentDescription; selected = on }
            .clip(CircleShape)
            .background(if (on) c.accent else c.surface2)
            .then(if (on) Modifier else Modifier.border(1.dp, c.line, CircleShape)),
        contentAlignment = Alignment.Center,
    ) {
        // On: an orange disc with a filled star, the same in both themes.
        if (on) CtIcon(LucideIcon.StarFilled, null, tint = c.onAccent) else CtIcon(LucideIcon.Star, null, tint = c.ink)
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

/** `.switch` in a `.row` — a label with an on/off switch; on is accent. The whole row toggles. */
@Composable
fun CtSwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    val c = CragpackTheme.colors
    val shape = RoundedCornerShape(RadiusTokens.lg)
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .ctClickable(true, Role.Switch, shape) { onChange(!checked) }
            .semantics { selected = checked }
            .clip(shape)
            .background(c.surface)
            .border(1.dp, c.line, shape)
            .padding(start = 20.dp, end = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CtText(label, Modifier.weight(1f), style = CragpackTheme.type.rowName, maxLines = 1)
        Box(Modifier.size(56.dp, 32.dp).clip(CircleShape).background(if (checked) c.accent else c.surface2).padding(4.dp)) {
            Box(
                Modifier
                    .align(if (checked) Alignment.CenterEnd else Alignment.CenterStart)
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(if (checked) c.onAccent else c.ink),
            )
        }
    }
}

/**
 * `.stepper` in a `.row.slim` — a label with − value +. [onStep] gets −1 or +1, never an absolute value,
 * so quick taps add up even before the new value comes back. The buttons stop at the ends of [range].
 */
@Composable
fun CtStepperRow(label: String, value: Int, onStep: (Int) -> Unit, modifier: Modifier = Modifier, range: IntRange = 0..99) {
    val c = CragpackTheme.colors
    val shape = RoundedCornerShape(RadiusTokens.lg)
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .clip(shape)
            .background(c.surface)
            .border(1.dp, c.line, shape)
            .padding(start = 20.dp, end = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CtText(label, Modifier.weight(1f), style = CragpackTheme.type.rowName, maxLines = 1)
        Row(
            Modifier.height(SizeTokens.tap).clip(CircleShape).background(c.surface).border(1.dp, c.line, CircleShape).padding(4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            // Small icon buttons are 44, the smallest tap target (rule 8); the pill comes to 52.
            CtIconButton(LucideIcon.Minus, "Less $label", { onStep(-1) }, size = CtIconButtonSize.Sm, enabled = value > range.first)
            CtText("$value", Modifier.widthIn(min = 40.dp), style = CragpackTheme.type.title, textAlign = TextAlign.Center)
            CtIconButton(LucideIcon.Plus, "More $label", { onStep(+1) }, size = CtIconButtonSize.Sm, enabled = value < range.last)
        }
    }
}

/** `.qty` — "2/3": how many are covered out of how many are needed. */
@Composable
fun QuantityBox(text: String, modifier: Modifier = Modifier) = Box(
    modifier.size(48.dp, 40.dp).clip(RoundedCornerShape(RadiusTokens.sm)).background(CragpackTheme.colors.surface2),
    contentAlignment = Alignment.Center,
) {
    CtText(text, style = CragpackTheme.type.h3.copy(fontSize = 15.sp, lineHeight = 15.sp, letterSpacing = 0.sp), maxLines = 1)
}

/** One day in [DayTabs]: "Fri", "20", and where once it's planned (a sector, or Rest). */
@Immutable
data class DayTab(val weekday: String, val day: String, val place: String?, val today: Boolean = false)

/**
 * `.days` / `.day` — the trip as calendar days: weekday, a big day number, and the sector underneath.
 * Days share the row's width when they fit and scroll sideways when they don't. Today reads "Today".
 */
@Composable
fun DayTabs(days: List<DayTab>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val c = CragpackTheme.colors
    val shape = RoundedCornerShape(RadiusTokens.md)
    val gap = 6.dp
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val tile = max(72.dp, (maxWidth - SizeTokens.edge * 2 - gap * (days.size - 1)) / days.size.coerceAtLeast(1))
        // Opens with the selected day second from the left, so the day before it still shows.
        val start = with(LocalDensity.current) { ((tile + gap) * (selected - 1).coerceAtLeast(0)).roundToPx() }
        val anyPlace = days.any { it.place != null }
        Row(
            Modifier.horizontalScroll(rememberScrollState(start)).padding(horizontal = SizeTokens.edge),
            horizontalArrangement = Arrangement.spacedBy(gap),
        ) {
            days.forEachIndexed { i, d ->
                val on = i == selected
                val soft = if (on) c.onSolid else c.ink2
                Column(
                    Modifier
                        .size(tile, 84.dp)
                        .ctClickable(true, Role.Tab, shape) { onSelect(i) }
                        .semantics { this.selected = on }
                        .clip(shape)
                        .background(if (on) c.solid else c.surface)
                        .then(if (on) Modifier else Modifier.border(1.dp, c.line, shape))
                        .padding(horizontal = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    CtText(
                        if (d.today) "Today" else d.weekday,
                        style = CragpackTheme.type.label,
                        color = if (d.today && !on) c.accentInk else soft,
                        maxLines = 1,
                    )
                    CtText(d.day, style = CragpackTheme.type.h3.copy(fontSize = 22.sp, lineHeight = 24.sp), color = if (on) c.onSolid else c.ink, maxLines = 1)
                    // Once any day has a place, every tile keeps the line so the numbers line up.
                    if (anyPlace) CtText(d.place.orEmpty(),style = CragpackTheme.type.label.copy(fontWeight = FontWeight.Medium), color = soft, maxLines = 1)
                }
            }
        }
    }
}

/** `.pick` — an unplanned objective in the floating tray; tapping it adds it to the day. */
@Composable
fun TrayPick(name: String, grade: String, band: Int, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = CragpackTheme.colors
    Row(
        modifier
            .height(SizeTokens.tap)
            .ctClickable(true, Role.Button, CircleShape, onClick)
            .semantics { contentDescription = "Add $name" }
            .clip(CircleShape)
            .background(c.raised)
            .border(1.dp, c.line, CircleShape)
            .padding(start = 6.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        GradeChip(grade, band, size = GradeChipSize.Xs)
        CtText(name, style = semibold, maxLines = 1)
        Box(Modifier.size(32.dp).clip(CircleShape).background(c.surface2), contentAlignment = Alignment.Center) {
            CtIcon(LucideIcon.Plus, null, size = 18.dp)
        }
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
