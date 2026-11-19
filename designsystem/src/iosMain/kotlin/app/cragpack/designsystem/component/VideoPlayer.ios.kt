package app.cragpack.designsystem.component

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.UIKitViewController
import platform.AVFoundation.AVPlayer
import platform.AVFoundation.pause
import platform.AVFoundation.play
import platform.AVKit.AVPlayerViewController
import platform.Foundation.NSURL

/** AVKit's player with its own controls, letterboxed. */
@Composable
actual fun VideoPlayer(path: String, modifier: Modifier) = UIKitViewController(
    factory = {
        AVPlayerViewController().apply {
            player = AVPlayer(uRL = NSURL.fileURLWithPath(path))
            player?.play()
        }
    },
    modifier = modifier,
    onRelease = { it.player?.pause() },
)
