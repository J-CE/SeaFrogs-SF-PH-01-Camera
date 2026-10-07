package de.jce.seafrogs

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.provider.MediaStore
import android.view.Surface
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.CameraState
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import java.util.UUID

data class PhotoCameraState(
    val ready: Boolean = false,
    val capturing: Boolean = false,
    val message: String = "Kamera startet",
    val resolution: String = ""
)

/**
 * UI and the future HID adapter share capturePhoto(). Neither needs camera APIs.
 * All public calls and callbacks use the main thread. CameraX handles image I/O.
 * Each start/stop advances a generation, so old async callbacks cannot rebind
 * the camera or update a newer session after Activity focus/lifecycle changes.
 */
class PhotoCameraController(
    context: Context,
    private val publish: (PhotoCameraState) -> Unit
) {
    private val appContext = context.applicationContext
    private val mainExecutor = ContextCompat.getMainExecutor(appContext)
    private var provider: ProcessCameraProvider? = null
    private var camera: Camera? = null
    private var preview: Preview? = null
    private var imageCapture: ImageCapture? = null
    private var owner: LifecycleOwner? = null
    private var previewView: PreviewView? = null
    private var generation = 0
    private var cameraOpen = false
    private var streaming = false
    private var busy = false
    private var rotation = Surface.ROTATION_0
    private var message = "Kamera startet"

    fun start(lifecycleOwner: LifecycleOwner, view: PreviewView) {
        stop()
        val token = generation
        owner = lifecycleOwner
        previewView = view
        message = "Kamera startet"
        emit()
        val future = ProcessCameraProvider.getInstance(appContext)
        future.addListener({
            if (generation != token) return@addListener
            try {
                val availableProvider = future.get()
                provider = availableProvider
                check(availableProvider.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA)) {
                    "Keine rückseitige Kamera verfügbar"
                }
                bind(availableProvider, lifecycleOwner, view)
                view.previewStreamState.observe(lifecycleOwner) { stream ->
                    if (generation == token) {
                        streaming = stream == PreviewView.StreamState.STREAMING
                        emit()
                    }
                }
                camera?.cameraInfo?.cameraState?.observe(lifecycleOwner) { state ->
                    if (generation == token) {
                        cameraOpen = state.type == CameraState.Type.OPEN && state.error == null
                        message = when {
                            state.error != null -> cameraErrorMessage(state.error!!.code)
                            cameraOpen -> "Bereit"
                            else -> "Warte auf Kamera"
                        }
                        emit()
                    }
                }
                // Reset the logical rear camera to its normal 1x field of view.
                // CameraX's normal photo session uses continuous picture AF.
                camera?.cameraControl?.setZoomRatio(1f)?.let { zoom ->
                    zoom.addListener({
                        if (generation == token) {
                            try { zoom.get() }
                            catch (error: Exception) {
                                cameraOpen = false
                                message = "1× konnte nicht eingestellt werden: ${error.cause?.message ?: error.message}"
                                emit()
                            }
                        }
                    }, mainExecutor)
                }
            } catch (error: Exception) {
                releaseUseCases()
                message = "Kamera nicht verfügbar: ${error.cause?.message ?: error.message}"
                emit()
            }
        }, mainExecutor)
    }

    private fun bind(
        availableProvider: ProcessCameraProvider,
        lifecycleOwner: LifecycleOwner,
        view: PreviewView
    ) {
        preview = Preview.Builder()
            .setResolutionSelector(ResolutionSelector.Builder()
                .setAspectRatioStrategy(AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY)
                .build())
            .build().also { it.setSurfaceProvider(view.surfaceProvider) }
        val resolutionSelector = ResolutionSelector.Builder()
            .setAllowedResolutionMode(ResolutionSelector.PREFER_HIGHER_RESOLUTION_OVER_CAPTURE_RATE)
            .setResolutionStrategy(ResolutionStrategy.HIGHEST_AVAILABLE_STRATEGY)
            .build()
        imageCapture = ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
            .setJpegQuality(100)
            .setTargetRotation(rotation)
            .setResolutionSelector(resolutionSelector)
            .build()
        // ResolutionSelector allows CameraX to negotiate the largest supported
        // 4:3 photo size alongside preview. Never claim an unexposed 50 MP mode.
        camera = availableProvider.bindToLifecycle(
            lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview!!, imageCapture!!
        )
    }

    fun setRotation(targetRotation: Int) {
        rotation = targetRotation
        imageCapture?.targetRotation = targetRotation
    }

    fun capturePhoto() {
        val capture = imageCapture ?: return
        if (!cameraOpen || !streaming || busy) return
        busy = true
        message = "Foto wird aufgenommen"
        emit()
        val token = generation
        val name = "SeaFrogs_${System.currentTimeMillis()}_${UUID.randomUUID()}.jpg"
        try {
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, name)
                put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                if (Build.VERSION.SDK_INT >= 29) {
                    put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/SeaFrogs")
                } else {
                    // Older devices require WRITE_EXTERNAL_STORAGE. Pixel 8 uses
                    // scoped MediaStore and never requests that legacy permission.
                    val directory = java.io.File(
                        android.os.Environment.getExternalStoragePublicDirectory(
                            android.os.Environment.DIRECTORY_PICTURES
                        ), "SeaFrogs"
                    )
                    if (!directory.exists() && !directory.mkdirs()) {
                        busy = false
                        message = "Speicherordner konnte nicht angelegt werden"
                        emit()
                        return
                    }
                    put(MediaStore.Images.Media.DATA, java.io.File(directory, name).absolutePath)
                }
            }
            val output = ImageCapture.OutputFileOptions.Builder(
                appContext.contentResolver, MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values
            ).build()
            capture.takePicture(output, mainExecutor, object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(result: ImageCapture.OutputFileResults) {
                    if (generation != token) return
                    busy = false
                    message = "JPEG gespeichert: Pictures/SeaFrogs"
                    emit()
                }
                override fun onError(error: ImageCaptureException) {
                    if (generation != token) return
                    busy = false
                    message = "Aufnahmefehler: ${error.message ?: error.imageCaptureError}"
                    emit()
                }
            })
        } catch (error: Exception) {
            busy = false
            message = "Aufnahmefehler: ${error.message}"
            emit()
        }
    }

    fun stop() {
        generation++
        owner?.let { lifecycleOwner ->
            camera?.cameraInfo?.cameraState?.removeObservers(lifecycleOwner)
            previewView?.previewStreamState?.removeObservers(lifecycleOwner)
        }
        releaseUseCases()
        owner = null
        previewView = null
    }

    private fun releaseUseCases() {
        val useCases = listOfNotNull(preview, imageCapture)
        if (useCases.isNotEmpty()) provider?.unbind(*useCases.toTypedArray())
        preview = null
        imageCapture = null
        camera = null
        cameraOpen = false
        streaming = false
        busy = false
    }

    private fun emit() {
        val size = imageCapture?.resolutionInfo?.resolution
        publish(PhotoCameraState(
            ready = cameraOpen && streaming && !busy,
            capturing = busy,
            message = message,
            resolution = size?.let { "${it.width} × ${it.height}" } ?: ""
        ))
    }

    private fun cameraErrorMessage(code: Int): String = when (code) {
        CameraState.ERROR_CAMERA_IN_USE -> "Kamera durch eine andere App belegt"
        CameraState.ERROR_MAX_CAMERAS_IN_USE -> "Keine freie Kamera verfügbar"
        CameraState.ERROR_CAMERA_DISABLED -> "Android hat die Kamera deaktiviert"
        CameraState.ERROR_STREAM_CONFIG -> "Fotoauflösung und Vorschau nicht gemeinsam verfügbar"
        CameraState.ERROR_CAMERA_FATAL_ERROR -> "Kameradienst ausgefallen. Erneut starten."
        else -> "Kamerafehler $code. Erneut starten."
    }
}
