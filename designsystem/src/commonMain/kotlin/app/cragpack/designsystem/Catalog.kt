package app.cragpack.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import app.cragpack.designsystem.component.CtButton
import app.cragpack.designsystem.component.CtButtonSize
import app.cragpack.designsystem.component.CtButtonStyle
import app.cragpack.designsystem.component.CtIcon
import app.cragpack.designsystem.component.CtIconButton
import app.cragpack.designsystem.component.CtIconButtonSize
import app.cragpack.designsystem.component.CtIconButtonStyle
import app.cragpack.designsystem.component.CtText
import app.cragpack.designsystem.component.GradeChip
import app.cragpack.designsystem.component.GradeChipSize
import app.cragpack.designsystem.component.GradeTag
import app.cragpack.designsystem.icon.LucideIcon
import app.cragpack.designsystem.theme.CragpackTheme
import app.cragpack.designsystem.theme.ThemeMode
import app.cragpack.designsystem.tokens.RadiusTokens
import app.cragpack.designsystem.tokens.SizeTokens

/** Every design-system piece on one screen, to compare with design/design-system.png in both themes. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun Catalog() {
    var mode by remember { mutableStateOf(ThemeMode.Auto) }
    CragpackTheme(mode) {
        val c = CragpackTheme.colors
        val t = CragpackTheme.type
        Column(
            Modifier
                .fillMaxSize()
                .background(c.bg)
                .verticalScroll(rememberScrollState())
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(SizeTokens.edge),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            CtText("Cragpack system", style = t.h1)
            Row {
                ThemeMode.entries.forEach {
                    CtButton(it.name, { mode = it }, style = if (it == mode) CtButtonStyle.Solid else CtButtonStyle.Ghost, size = CtButtonSize.Sm)
                }
            }

            Section("Colour roles")
            Row {
                listOf(
                    "bg" to c.bg, "ink" to c.ink, "ink2" to c.ink2, "surface" to c.surface, "surface2" to c.surface2,
                    "raised" to c.raised, "solid" to c.solid, "accent" to c.accent, "accentInk" to c.accentInk, "danger" to c.danger,
                ).forEach { (name, color) -> Swatch(name, color) }
            }

            Section("Type")
            CtText("Font Easter", style = t.titleXl)
            CtText("Wishlist", style = t.h1)
            CtText("Bas Cuvier", style = t.h3)
            CtText("14/18 saved", style = t.big)
            CtText("Add gear", style = t.title)
            CtText("La Joker", style = t.rowName)
            CtText("Fontainebleau · 17–21 Apr", style = t.body)
            CtText("Crag", style = t.label, color = c.ink2)
            CtText("cragpack.app/j/k3x9", style = t.mono)

            Section("Grades")
            Row {
                listOf("2" to 2, "4" to 4, "5+" to 5, "6B" to 6, "7A" to 7, "8A" to 8, "9A" to 9).forEach { (g, band) ->
                    GradeChip(g, band, size = GradeChipSize.Sm)
                }
            }
            Row {
                GradeChip("7A", 7, size = GradeChipSize.Xl)
                GradeChip("7A", 7)
                GradeChip("7A", 7, size = GradeChipSize.Sm)
                GradeChip("7A", 7, size = GradeChipSize.Xs)
                GradeTag("7A", 7)
                GradeTag("V6", 7)
                GradeTag("6c+", 6)
            }

            Section("Controls")
            Row {
                CtIconButton(LucideIcon.ChevronLeft, "Back", {})
                CtIconButton(LucideIcon.Plus, "Add", {}, style = CtIconButtonStyle.Accent)
                CtIconButton(LucideIcon.StarFilled, "Unstar", {}, style = CtIconButtonStyle.On)
                CtIconButton(LucideIcon.X, "Close", {}, size = CtIconButtonSize.Sm)
            }
            Row {
                CtButton("Recap", {})
                CtButton("Bring 1", {}, style = CtButtonStyle.Accent)
                CtButton("Pin location", {}, style = CtButtonStyle.Ghost, icon = LucideIcon.MapPin)
            }
            Row {
                CtButton("Delete trip", {}, style = CtButtonStyle.Danger, icon = LucideIcon.Trash2)
                CtButton("Delete", {}, style = CtButtonStyle.DangerSolid)
                CtButton("Create trip", {}, style = CtButtonStyle.Accent, enabled = false)
            }
            CtButton("Add", {}, style = CtButtonStyle.Accent, size = CtButtonSize.Wide, icon = LucideIcon.Plus)

            Section("Icons")
            Row {
                LucideIcon.entries.forEach { CtIcon(it, it.name) }
            }

            Box(Modifier.padding(bottom = SizeTokens.dock))
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Row(content: @Composable () -> Unit) =
    FlowRow(horizontalArrangement = Arrangement.spacedBy(SizeTokens.gap), verticalArrangement = Arrangement.spacedBy(SizeTokens.gap)) {
        content()
    }

@Composable
private fun Section(title: String) =
    CtText(title.uppercase(), Modifier.padding(top = 16.dp), style = CragpackTheme.type.label, color = CragpackTheme.colors.ink2)

@Composable
private fun Swatch(name: String, color: Color) = Column(Modifier.width(76.dp)) {
    Box(
        Modifier
            .size(76.dp, 44.dp)
            .background(color, RoundedCornerShape(RadiusTokens.sm))
            .border(1.dp, CragpackTheme.colors.line, RoundedCornerShape(RadiusTokens.sm)),
    )
    CtText(name, style = CragpackTheme.type.label, color = CragpackTheme.colors.ink2, maxLines = 1)
}
