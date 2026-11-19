package app.cragpack.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.cragpack.designsystem.icon.LucideIcon
import app.cragpack.designsystem.theme.CragpackTheme
import app.cragpack.designsystem.theme.DarkContent
import app.cragpack.designsystem.tokens.ColorTokens
import app.cragpack.designsystem.tokens.SizeTokens
import coil3.compose.AsyncImage

/**
 * One page of the media viewer. [image] is a file path or URL: the photo itself, a video's first frame,
 * or a YouTube preview. A [video] (file path) plays in place; a [link] (YouTube, online only) opens outside the app.
 */
@Immutable
data class MediaItem(
    val label: String,
    val image: String?,
    val video: String? = null,
    val link: String? = null,
    val author: String? = null,
    val authorColor: Int = 1,
)

/**
 * Media viewer (21): every photo, topo and video of a climb, swiped through full screen, always dark,
 * with the position ("2/5") top right. Shows while [start] (the page to open on) is set.
 * Closes with ✕ or system back. Place it in [CtScreen]'s overlay slot.
 * ponytail: videos use the platform player's own controls; the design's scrubber can replace them later.
 */
@Composable
fun BoxScope.MediaViewer(items: List<MediaItem>, start: Int?, onDismiss: () -> Unit) {
    CtBackHandler(enabled = start != null, onBack = onDismiss)
    if (start == null || items.isEmpty()) return
    val pager = rememberPagerState(start.coerceIn(items.indices)) { items.size }
    DarkContent {
        // Swallows taps so nothing behind the viewer reacts.
        Box(Modifier.matchParentSize().background(ColorTokens.photoScrim.copy(alpha = 1f)).pointerInput(Unit) { detectTapGestures {} }) {
            HorizontalPager(pager, Modifier.fillMaxSize()) { page -> MediaPage(items[page]) }
            val item = items[pager.currentPage.coerceIn(items.indices)]
            TopBar(
                Modifier.padding(horizontal = SizeTokens.edge),
                leading = { CtIconButton(LucideIcon.X, "Close", onDismiss) },
                center = { SourcePill(if (item.link != null) "${item.label} · online only" else item.label, onlineOnly = item.link != null) },
                trailing = {
                    CtText(
                        "${pager.currentPage + 1}/${items.size}",
                        Modifier.padding(end = 12.dp),
                        style = CragpackTheme.type.mono.copy(fontSize = 15.sp),
                    )
                },
            )
            if (item.author != null) {
                Row(
                    Modifier.align(Alignment.BottomStart).navigationBarsPadding().padding(SizeTokens.edge),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Avatar(item.author, item.authorColor)
                    CtText(item.author, style = CragpackTheme.type.rowName, maxLines = 1)
                }
            }
        }
    }
}

@Composable
private fun MediaPage(item: MediaItem) {
    // Leaving the page disposes it, which stops the video.
    var playing by remember { mutableStateOf(false) }
    val uri = LocalUriHandler.current
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        when {
            item.video != null && playing -> VideoPlayer(item.video, Modifier.fillMaxSize().padding(vertical = 80.dp))
            item.video == null && item.link == null ->
                if (item.image != null) AsyncImage(item.image, null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
            else -> {
                PhotoSurface(Modifier.fillMaxSize(), RectangleShape, imagePath = item.image)
                CtIconButton(
                    LucideIcon.Play, if (item.link != null) "Play on YouTube" else "Play video",
                    { if (item.link != null) uri.openUri(item.link) else playing = true },
                    style = CtIconButtonStyle.Accent, size = CtIconButtonSize.Xl,
                )
            }
        }
    }
}

/** What's on screen; YouTube gets the empty "online only" dot. */
@Composable
private fun SourcePill(text: String, onlineOnly: Boolean) {
    val c = CragpackTheme.colors
    Row(
        Modifier.height(40.dp).clip(CircleShape).background(c.surface).border(1.dp, c.line, CircleShape).padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (onlineOnly) Box(Modifier.size(10.dp).border(2.dp, c.ink, CircleShape))
        CtText(text, style = CragpackTheme.type.body.copy(fontSize = 14.sp, fontWeight = FontWeight.SemiBold), maxLines = 1)
    }
}

/** Plays a local video file with the platform's own player and controls, starting right away. */
@Composable
expect fun VideoPlayer(path: String, modifier: Modifier = Modifier)
