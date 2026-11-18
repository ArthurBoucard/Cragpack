package app.cragpack.designsystem.component

import androidx.compose.runtime.Composable

@Composable
actual fun CtBackHandler(enabled: Boolean, onBack: () -> Unit) = Unit

// ponytail: iOS keeps the default status-bar style; set it per screen from Swift if heroes need it.
@Composable
actual fun LightStatusBarIcons() = Unit
