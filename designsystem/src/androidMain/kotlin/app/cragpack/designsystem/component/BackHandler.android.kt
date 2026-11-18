package app.cragpack.designsystem.component

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

@Composable
actual fun CtBackHandler(enabled: Boolean, onBack: () -> Unit) = BackHandler(enabled, onBack)

@Composable
actual fun LightStatusBarIcons() {
    val view = LocalView.current
    DisposableEffect(view) {
        val window = view.context.activity()?.window ?: return@DisposableEffect onDispose {}
        val bars = WindowCompat.getInsetsController(window, view)
        val before = bars.isAppearanceLightStatusBars
        bars.isAppearanceLightStatusBars = false
        onDispose { bars.isAppearanceLightStatusBars = before }
    }
}

private tailrec fun Context.activity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.activity()
    else -> null
}
