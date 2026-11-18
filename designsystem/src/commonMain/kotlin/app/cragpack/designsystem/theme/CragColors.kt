package app.cragpack.designsystem.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/** Colour roles from docs/04-design-system.md. Values are generated from the tokens (DarkColors, LightColors). */
@Immutable
data class CragColors(
    val bg: Color,
    val ink: Color,
    val ink2: Color,
    val surface: Color,
    val surface2: Color,
    val line: Color,
    val raised: Color,
    val solid: Color,
    val onSolid: Color,
    /** Actions, ticks, "now", progress. Never a warning. */
    val accent: Color,
    val onAccent: Color,
    val accentInk: Color,
    /** Destructive actions only. */
    val danger: Color,
    val dangerInk: Color,
    val onDanger: Color,
    val ring: Color,
)
