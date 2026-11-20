package app.cragpack.feature

import androidx.compose.runtime.Composable

/** Opens the phone's share menu with [text] (an invite link). */
@Composable
expect fun rememberShareText(): (text: String) -> Unit
