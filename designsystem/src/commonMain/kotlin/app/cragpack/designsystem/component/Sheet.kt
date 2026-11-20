package app.cragpack.designsystem.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import app.cragpack.designsystem.icon.LucideIcon
import app.cragpack.designsystem.theme.CragpackTheme
import app.cragpack.designsystem.tokens.ColorTokens
import app.cragpack.designsystem.tokens.RadiusTokens
import app.cragpack.designsystem.tokens.SizeTokens

/**
 * `.bottom-sheet` over a `.scrim` — for choices and short forms (design rule 11).
 * Place it in [CtScreen]'s overlay slot. Closes with ✕, the scrim, or system back.
 */
@Composable
fun BoxScope.CtBottomSheet(
    visible: Boolean,
    title: String,
    onDismiss: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    CtBackHandler(enabled = visible, onBack = onDismiss)
    AnimatedVisibility(visible, Modifier.matchParentSize(), enter = fadeIn(), exit = fadeOut()) {
        Box(Modifier.fillMaxSize().background(ColorTokens.overlayScrim).clickable(null, indication = null, onClick = onDismiss))
    }
    AnimatedVisibility(
        visible,
        Modifier.align(Alignment.BottomCenter).statusBarsPadding(),
        enter = slideInVertically { it },
        exit = slideOutVertically { it },
    ) {
        val c = CragpackTheme.colors
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(topStart = RadiusTokens.xl, topEnd = RadiusTokens.xl))
                .background(c.raised)
                .clickable(null, indication = null) {} // taps on the sheet don't reach the scrim
                .navigationBarsPadding()
                .imePadding()
                .verticalScroll(rememberScrollState()) // long choice lists (one row per trip day)
                .padding(start = SizeTokens.edge, end = SizeTokens.edge, top = 12.dp, bottom = SizeTokens.edge),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(Modifier.align(Alignment.CenterHorizontally).size(44.dp, 5.dp).clip(RoundedCornerShape(3.dp)).background(c.line))
            Row(verticalAlignment = Alignment.CenterVertically) {
                CtText(title, Modifier.weight(1f), style = CragpackTheme.type.h3, maxLines = 2)
                CtIconButton(LucideIcon.X, "Close", onDismiss, size = CtIconButtonSize.Sm)
            }
            content()
        }
    }
}
