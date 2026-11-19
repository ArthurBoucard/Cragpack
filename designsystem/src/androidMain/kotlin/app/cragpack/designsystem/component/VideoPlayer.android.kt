package app.cragpack.designsystem.component

import android.graphics.SurfaceTexture
import android.media.MediaPlayer
import android.view.Surface
import android.view.TextureView
import android.widget.FrameLayout
import android.widget.MediaController
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect

/**
 * MediaPlayer on a TextureView, with the framework's MediaController (tap to show it).
 * Not VideoView: its SurfaceView never got a surface inside the Compose viewer. No player library for one screen.
 */
@Composable
actual fun VideoPlayer(path: String, modifier: Modifier) = key(path) {
    val video = remember { PlayerHolder() }
    var aspect by remember { mutableFloatStateOf(9f / 16f) }
    // The controller polls the player while shown: hide it before the player goes.
    DisposableEffect(Unit) { onDispose { video.controls?.hide(); video.player.release(); video.surface?.release() } }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { runCatching { if (video.player.isPlaying) video.player.pause() } }
    Box(modifier, contentAlignment = Alignment.Center) {
        AndroidView(
            factory = { context ->
                val frame = FrameLayout(context)
                val bar = MediaController(context).apply { setMediaPlayer(PlayerControls(video.player)); setAnchorView(frame) }
                video.controls = bar
                frame.addView(TextureView(context).apply {
                    surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                        override fun onSurfaceTextureAvailable(texture: SurfaceTexture, width: Int, height: Int) {
                            video.surface?.release()
                            video.surface = Surface(texture).also { video.player.setSurface(it) }
                            if (video.opened) return // the texture comes back after the app was in the background
                            video.opened = true
                            video.player.setOnPreparedListener {
                                if (it.videoWidth > 0 && it.videoHeight > 0) aspect = it.videoWidth.toFloat() / it.videoHeight
                                it.start()
                                bar.show()
                            }
                            // An unreadable file just leaves the frame dark rather than crashing the viewer.
                            runCatching { video.player.setDataSource(path); video.player.prepareAsync() }
                        }
                        override fun onSurfaceTextureSizeChanged(texture: SurfaceTexture, width: Int, height: Int) = Unit
                        override fun onSurfaceTextureDestroyed(texture: SurfaceTexture) = true
                        override fun onSurfaceTextureUpdated(texture: SurfaceTexture) = Unit
                    }
                    setOnClickListener { if (bar.isShowing) bar.hide() else bar.show() }
                })
                frame
            },
            modifier = Modifier.aspectRatio(aspect),
        )
    }
}

/** One video's player and the views attached to it, released together. */
private class PlayerHolder {
    val player = MediaPlayer()
    var controls: MediaController? = null
    var surface: Surface? = null
    var opened = false
}

private class PlayerControls(private val p: MediaPlayer) : MediaController.MediaPlayerControl {
    override fun start() = p.start()
    override fun pause() = p.pause()
    override fun getDuration() = p.duration
    override fun getCurrentPosition() = p.currentPosition
    override fun seekTo(pos: Int) = p.seekTo(pos)
    override fun isPlaying() = p.isPlaying
    override fun getBufferPercentage() = 100
    override fun canPause() = true
    override fun canSeekBackward() = true
    override fun canSeekForward() = true
    override fun getAudioSessionId() = p.audioSessionId
}
