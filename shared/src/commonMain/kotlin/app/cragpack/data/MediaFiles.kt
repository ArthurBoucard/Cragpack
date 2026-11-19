package app.cragpack.data

import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.absolutePath
import io.github.vinceglb.filekit.copyTo
import io.github.vinceglb.filekit.createDirectories
import io.github.vinceglb.filekit.exists
import io.github.vinceglb.filekit.extension
import io.github.vinceglb.filekit.filesDir
import io.github.vinceglb.filekit.mimeType
import io.github.vinceglb.filekit.resolve
import io.github.vinceglb.filekit.size
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext

/**
 * Beta media lives in the app's own files folder. Paths are stored relative to it,
 * because iOS moves the app's folder when the app updates.
 */
object MediaFiles {
    class Imported(val id: String, val mime: String, val bytes: Long, val localPath: String)

    /** Copies a file picked from the phone's gallery into app storage. */
    suspend fun import(file: PlatformFile, isVideo: Boolean): Imported {
        val id = newId()
        val mime = file.mimeType()?.toString() ?: if (isVideo) "video/mp4" else "image/jpeg"
        val ext = file.extension.lowercase().ifBlank { mime.substringAfter('/') }
        val path = "media/$id.$ext"
        FileKit.filesDir.resolve("media").createDirectories()
        file.copyTo(FileKit.filesDir.resolve(path))
        return Imported(id, mime, file.size(), path)
    }

    fun absolute(localPath: String): String = FileKit.filesDir.resolve(localPath).absolutePath()

    /**
     * A video's first frame as a JPEG next to it ("media/x.mp4.jpg"), made the first time it's asked for.
     * Returns its absolute path, or null if the phone can't read the video.
     */
    suspend fun videoFrame(localPath: String): String? = withContext(Dispatchers.IO) {
        val frame = FileKit.filesDir.resolve("$localPath.jpg")
        val out = frame.absolutePath()
        out.takeIf { frame.exists() || runCatching { writeVideoFrame(absolute(localPath), out) }.getOrDefault(false) }
    }
}

/** Writes the first frame of the video at [video] as a JPEG to [out]; false if the video can't be read. Both are absolute paths. */
internal expect fun writeVideoFrame(video: String, out: String): Boolean
