package app.cragpack.data

import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import java.io.File
import java.io.FileOutputStream

internal actual fun writeVideoFrame(video: String, out: String): Boolean {
    val retriever = MediaMetadataRetriever()
    try {
        retriever.setDataSource(video)
        val frame = retriever.getFrameAtTime(0) ?: return false
        val ok = runCatching { FileOutputStream(out).use { frame.compress(Bitmap.CompressFormat.JPEG, 80, it) } }.getOrDefault(false)
        if (!ok) File(out).delete() // a half-written frame would be trusted forever
        return ok
    } finally {
        retriever.release()
    }
}
