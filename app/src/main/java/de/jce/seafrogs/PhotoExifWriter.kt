package de.jce.seafrogs

import android.content.Context
import android.net.Uri
import android.os.Build
import androidx.exifinterface.media.ExifInterface
import java.util.concurrent.Executors

/** Adds app diagnostics to the saved original JPEG without recompressing pixels. */
class PhotoExifWriter(context: Context) {
    private val appContext = context.applicationContext

    fun write(uri: Uri?, snapshot: String, complete: (String?) -> Unit) {
        executor.execute {
            val failure = runCatching {
                checkNotNull(uri) { "Keine JPEG-URI erhalten" }
                appContext.contentResolver.openFileDescriptor(uri, "rw").use { descriptor ->
                    checkNotNull(descriptor) { "JPEG konnte nicht geoeffnet werden" }
                    val exif = ExifInterface(descriptor.fileDescriptor)
                    // HAL values for shutter, ISO, aperture, focal length and
                    // orientation remain authoritative. Never invent missing data.
                    if (exif.getAttribute(ExifInterface.TAG_MAKE).isNullOrBlank())
                        exif.setAttribute(ExifInterface.TAG_MAKE, Build.MANUFACTURER)
                    if (exif.getAttribute(ExifInterface.TAG_MODEL).isNullOrBlank())
                        exif.setAttribute(ExifInterface.TAG_MODEL, Build.MODEL)
                    val originalSoftware = exif.getAttribute(ExifInterface.TAG_SOFTWARE)
                    val appSoftware = "SeaFrogs Camera 0.8.1-dive"
                    exif.setAttribute(ExifInterface.TAG_SOFTWARE,
                        if (originalSoftware.isNullOrBlank()) appSoftware
                        else "$originalSoftware; $appSoftware")
                    val originalComment = exif.getAttribute(ExifInterface.TAG_USER_COMMENT)
                    exif.setAttribute(ExifInterface.TAG_USER_COMMENT,
                        if (originalComment.isNullOrBlank()) snapshot
                        else "$originalComment\n$snapshot")
                    exif.saveAttributes()
                }
            }.exceptionOrNull()?.let { it.message ?: it.javaClass.simpleName }
            complete(failure)
        }
    }

    companion object {
        // One application-wide I/O worker, rather than one thread per Activity.
        private val executor = Executors.newSingleThreadExecutor()
    }
}
