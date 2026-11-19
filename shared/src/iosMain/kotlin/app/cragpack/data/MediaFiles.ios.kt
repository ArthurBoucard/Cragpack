package app.cragpack.data

import kotlinx.cinterop.ExperimentalForeignApi
import platform.AVFoundation.AVAssetImageGenerator
import platform.AVFoundation.AVURLAsset
import platform.CoreGraphics.CGImageRelease
import platform.CoreMedia.CMTimeMake
import platform.Foundation.NSURL
import platform.Foundation.writeToFile
import platform.UIKit.UIImage
import platform.UIKit.UIImageJPEGRepresentation

@OptIn(ExperimentalForeignApi::class)
internal actual fun writeVideoFrame(video: String, out: String): Boolean {
    val generator = AVAssetImageGenerator(AVURLAsset(NSURL.fileURLWithPath(video), null))
    generator.appliesPreferredTrackTransform = true // portrait phone videos stay upright
    val frame = generator.copyCGImageAtTime(CMTimeMake(0, 1), null, null) ?: return false
    val jpeg = UIImageJPEGRepresentation(UIImage.imageWithCGImage(frame), 0.8)
    CGImageRelease(frame)
    return jpeg?.writeToFile(out, true) ?: false
}
