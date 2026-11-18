package app.cragpack.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
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
