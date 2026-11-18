package app.cragpack.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.max
import app.cragpack.designsystem.theme.CragpackTheme
import app.cragpack.designsystem.tokens.SizeTokens

/**
 * Screen frame: background, an optional floating [dock] above the home indicator, and an [overlay]
 * slot for sheets and toasts drawn above everything. [content] gets the bottom padding that keeps
 * the last item clear of the dock (design rule 10).
 */
@Composable
fun CtScreen(
    modifier: Modifier = Modifier,
    dock: (@Composable () -> Unit)? = null,
    overlay: @Composable BoxScope.() -> Unit = {},
    content: @Composable (bottomPadding: PaddingValues) -> Unit,
) {
    val dockBottom = max(SizeTokens.edge, WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding())
    Box(modifier.fillMaxSize().background(CragpackTheme.colors.bg)) {
        content(PaddingValues(bottom = if (dock != null) dockBottom + 88.dp else dockBottom))
        if (dock != null) {
            Box(Modifier.align(Alignment.BottomCenter).padding(start = SizeTokens.edge, end = SizeTokens.edge, bottom = dockBottom)) {
                dock()
            }
        }
        overlay()
    }
}

/** Top bar: back/close or avatar on the left, status or title centred, one action on the right. Sits below the status bar. */
@Composable
fun TopBar(
    modifier: Modifier = Modifier,
    leading: @Composable () -> Unit = {},
    trailing: @Composable () -> Unit = {},
    center: @Composable () -> Unit = {},
) {
    Box(modifier.fillMaxWidth().statusBarsPadding().padding(top = 8.dp).height(SizeTokens.tap)) {
        Box(Modifier.align(Alignment.CenterStart)) { leading() }
        Box(Modifier.align(Alignment.Center)) { center() }
        Box(Modifier.align(Alignment.CenterEnd)) { trailing() }
    }
}

/** While in composition, status-bar icons are light: use on screens whose top is a dark photo hero. */
@Composable
expect fun LightStatusBarIcons()

/** System back (Android). iOS has no back button; sheets close with ✕ or the scrim. */
@Composable
expect fun CtBackHandler(enabled: Boolean = true, onBack: () -> Unit)
