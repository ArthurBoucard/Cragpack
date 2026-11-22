package app.cragpack.designsystem.theme

import app.cragpack.designsystem.component.SystemBarIcons
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import app.cragpack.designsystem.tokens.DarkColors
import app.cragpack.designsystem.tokens.LightColors

/** Auto follows the system; the user can force Dark or Sunlight in Settings (D10). */
enum class ThemeMode { Auto, Dark, Sunlight }

internal val LocalColors = staticCompositionLocalOf { DarkColors }

/** The app's theme (not a photo's dark content): what the status bar goes back to after a photo screen. */
internal val LocalDarkTheme = staticCompositionLocalOf { true }
private val LocalTypography = staticCompositionLocalOf<CragTypography> { error("Wrap content in CragpackTheme") }

/** Colour for icons and text when a component doesn't set one (like Material's LocalContentColor). */
val LocalContentColor = staticCompositionLocalOf { Color.Unspecified }

@Composable
fun CragpackTheme(mode: ThemeMode = ThemeMode.Auto, content: @Composable () -> Unit) {
    val dark = when (mode) {
        ThemeMode.Auto -> isSystemInDarkTheme()
        ThemeMode.Dark -> true
        ThemeMode.Sunlight -> false
    }
    val colors = if (dark) DarkColors else LightColors
    SystemBarIcons(dark)
    CompositionLocalProvider(
        LocalColors provides colors,
        LocalDarkTheme provides dark,
        LocalTypography provides cragTypography(),
        LocalContentColor provides colors.ink,
        content = content,
    )
}

/** Photo surfaces and the media viewer stay dark in both themes (D10). */
@Composable
internal fun DarkContent(content: @Composable () -> Unit) = CompositionLocalProvider(
    LocalColors provides DarkColors,
    LocalContentColor provides DarkColors.ink,
    content = content,
)

/** The recap sheet stays light in both themes (design rule 9). */
@Composable
internal fun LightContent(content: @Composable () -> Unit) = CompositionLocalProvider(
    LocalColors provides LightColors,
    LocalContentColor provides LightColors.ink,
    content = content,
)

object CragpackTheme {
    val colors: CragColors
        @Composable @ReadOnlyComposable get() = LocalColors.current
    val type: CragTypography
        @Composable @ReadOnlyComposable get() = LocalTypography.current
}
