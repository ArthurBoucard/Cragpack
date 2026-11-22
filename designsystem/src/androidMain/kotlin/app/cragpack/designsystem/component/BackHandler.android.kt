package app.cragpack.designsystem.component

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import app.cragpack.designsystem.theme.LocalDarkTheme

@Composable
actual fun CtBackHandler(enabled: Boolean, onBack: () -> Unit) = BackHandler(enabled, onBack)

/**
 * Photo screens asking for light status bar icons right now. Two of them overlap while one replaces the other
 * (Join to Trip), so each can't just put back what it found: the bar follows the theme once none is left.
 */
private var lightIconRequests = 0

@Composable
actual fun SystemBarIcons(darkTheme: Boolean) {
    val view = LocalView.current
    DisposableEffect(view, darkTheme) {
        val window = view.context.activity()?.window ?: return@DisposableEffect onDispose {}
        WindowCompat.getInsetsController(window, view).apply {
            isAppearanceLightStatusBars = !darkTheme && lightIconRequests == 0
            isAppearanceLightNavigationBars = !darkTheme
        }
        onDispose {}
    }
}

@Composable
actual fun LightStatusBarIcons() {
    val view = LocalView.current
    val dark = LocalDarkTheme.current
    DisposableEffect(view, dark) {
        val window = view.context.activity()?.window ?: return@DisposableEffect onDispose {}
        val bars = WindowCompat.getInsetsController(window, view)
        lightIconRequests++
        bars.isAppearanceLightStatusBars = false
        onDispose {
            lightIconRequests--
            if (lightIconRequests == 0) bars.isAppearanceLightStatusBars = !dark
        }
    }
}

private tailrec fun Context.activity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.activity()
    else -> null
}
