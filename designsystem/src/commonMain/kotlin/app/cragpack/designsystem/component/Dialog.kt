package app.cragpack.designsystem.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.unit.dp
import app.cragpack.designsystem.theme.CragpackTheme
import app.cragpack.designsystem.tokens.ColorTokens
import app.cragpack.designsystem.tokens.RadiusTokens

/**
 * `.dialog` — only to confirm something that can't be undone (design rule 11): a title, the short
 * consequence, Cancel and a solid danger button. Place it in [CtScreen]'s overlay slot.
 */
@Composable
fun BoxScope.CtConfirmDialog(
    visible: Boolean,
    title: String,
    text: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    CtBackHandler(enabled = visible, onBack = onDismiss)
    AnimatedVisibility(visible, Modifier.matchParentSize(), enter = fadeIn(), exit = fadeOut()) {
        Box(
            Modifier.fillMaxSize().background(ColorTokens.overlayScrim).clickable(null, indication = null, onClick = onDismiss),
            contentAlignment = Alignment.Center,
        ) {
            val shape = RoundedCornerShape(RadiusTokens.xl)
            Column(
                Modifier
                    .padding(horizontal = 24.dp)
                    .fillMaxWidth()
                    .shadow(24.dp, shape)
                    .clip(shape)
                    .background(CragpackTheme.colors.raised)
                    .clickable(null, indication = null) {} // taps on the card don't dismiss
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                CtText(title, style = CragpackTheme.type.h3)
                CtText(text, color = CragpackTheme.colors.ink2)
                Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CtButton("Cancel", onDismiss, Modifier.weight(1f), style = CtButtonStyle.Ghost)
                    CtButton(confirmLabel, onConfirm, Modifier.weight(1f), style = CtButtonStyle.DangerSolid)
                }
            }
        }
    }
}
