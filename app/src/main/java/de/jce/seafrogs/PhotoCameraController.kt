package de.jce.seafrogs

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.provider.MediaStore
import android.view.Surface
import androidx.camera.core.Camera
import androidx.camera.camera2.interop.Camera2Interop
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.core.FocusMeteringAction
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
import java.util.concurrent.TimeUnit

data class PhotoCameraState(
    val lens: PhotoLens = PhotoLens.MAIN,
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
@androidx.annotation.OptIn(markerClass = [ExperimentalCamera2Interop::class])
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
    private var catalog: CameraLensCatalog? = null
    private var activeLens = PhotoLens.MAIN
    private var cyclePosition = PhotoLens.MAIN
    private var zoomReady = false
    private var focusPending = false
    private var focusTriggered = false
    private var rotation = Surface.ROTATION_0
    private var message = "Kamera startet"

    fun start(lifecycleOwner: LifecycleOwner, view: PreviewView) {
        stop()
        owner = lifecycleOwner
        previewView = view
        open(activeLens)
    }

    /** The later HID adapter calls the same command as the large UI button. */
    fun cycleLens() {
        if (!isReady()) return
        cyclePosition = cyclePosition.next()
        val target = cyclePosition
        if (catalog?.routeFor(target) == null) {
            message = if (target == PhotoLens.MACRO)
                "Macro nicht verfügbar: keine Ultraweitwinkelkamera mit Foto-AF"
            else "Ultraweitwinkelkamera nicht verfügbar"
            emit()
            return
        }
        open(target, activeLens)
    }

    private fun open(target: PhotoLens, fallback: PhotoLens? = null, notice: String? = null) {
        val lifecycleOwner = owner ?: return
        val view = previewView ?: return
        clearSession()
        val token = generation
        message = notice ?: "Kamerawechsel: ${target.label}"
        emit()
        val future = ProcessCameraProvider.getInstance(appContext)
        future.addListener({
            if (generation != token) return@addListener
            try {
                val availableProvider = future.get()
                provider = availableProvider
                val lenses = catalog ?: CameraLensCatalog(appContext, availableProvider).also {
                    catalog = it
                }
                val route = checkNotNull(lenses.routeFor(target)) {
                    "Keine geeignete Kamera für ${target.label}"
                }
                bind(availableProvider, lifecycleOwner, view, route)
                activeLens = target
                val boundCamera = checkNotNull(camera)
                view.previewStreamState.observe(lifecycleOwner) { stream ->
                    if (generation == token) {
                        streaming = stream == PreviewView.StreamState.STREAMING
                        maybeRestartMacroFocus(token)
                        emit()
                    }
                }
                boundCamera.cameraInfo.cameraState.observe(lifecycleOwner) { state ->
                    if (generation == token) {
                        cameraOpen = state.type == CameraState.Type.OPEN && state.error == null
                        message = when {
                            state.error != null -> cameraErrorMessage(state.error!!.code)
                            cameraOpen -> notice ?: "Bereit"
                            else -> "Warte auf Kamera"
                        }
                        maybeRestartMacroFocus(token)
                        emit()
                    }
                }
                val zoom = boundCamera.cameraControl.setZoomRatio(1f)
                zoom.addListener({
                    if (generation == token) {
                        try {
                            zoom.get()
                            zoomReady = true
                            maybeRestartMacroFocus(token)
                            emit()
                        } catch (error: Exception) {
                            recover(target, fallback, error)
                        }
                    }
                }, mainExecutor)
            } catch (error: Exception) {
                recover(target, fallback, error)
            }
        }, mainExecutor)
    }

    private fun recover(target: PhotoLens, fallback: PhotoLens?, error: Exception) {
        val detail = error.cause?.message ?: error.message
        if (fallback != null) {
            open(fallback, notice = "${target.label} fehlgeschlagen. Zurück zu ${fallback.label}: $detail")
        } else {
            clearSession()
            message = "Kamera nicht verfügbar: $detail. Erneut starten."
            emit()
        }
    }

    private fun bind(
        availableProvider: ProcessCameraProvider,
        lifecycleOwner: LifecycleOwner,
        view: PreviewView,
        route: CameraLensRoute
    ) {
        val previewBuilder = Preview.Builder()
            .setResolutionSelector(ResolutionSelector.Builder()
                .setAspectRatioStrategy(AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY)
                .build())
        val resolutionSelector = ResolutionSelector.Builder()
            .setAllowedResolutionMode(ResolutionSelector.PREFER_HIGHER_RESOLUTION_OVER_CAPTURE_RATE)
            .setResolutionStrategy(ResolutionStrategy.HIGHEST_AVAILABLE_STRATEGY)
            .build()
        val captureBuilder = ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
            .setJpegQuality(100)
            .setTargetRotation(rotation)
            .setResolutionSelector(resolutionSelector)
        // Both output streams must use the same physical sensor. The logical
        // parent supplies CameraX controls; their effect needs device validation.
        if (Build.VERSION.SDK_INT >= 28) route.physicalId?.let { physicalId ->
            Camera2Interop.Extender(previewBuilder).setPhysicalCameraId(physicalId)
            Camera2Interop.Extender(captureBuilder).setPhysicalCameraId(physicalId)
        }
        preview = previewBuilder.build().also { it.setSurfaceProvider(view.surfaceProvider) }
        imageCapture = captureBuilder.build()
        camera = availableProvider.bindToLifecycle(
            lifecycleOwner, route.selector(), preview!!, imageCapture!!
        )
    }

    private fun maybeRestartMacroFocus(token: Int) {
        if (activeLens != PhotoLens.MACRO || focusTriggered ||
            !cameraOpen || !streaming || !zoomReady) return
        focusTriggered = true
        val boundCamera = camera ?: return
        val view = previewView ?: return
        val point = view.meteringPointFactory.createPoint(view.width / 2f, view.height / 2f)
        val action = FocusMeteringAction.Builder(point, FocusMeteringAction.FLAG_AF)
            .setAutoCancelDuration(2, TimeUnit.SECONDS).build()
        if (!boundCamera.cameraInfo.isFocusMeteringSupported(action)) {
            message = "Macro: kontinuierlicher AF; Fokus-Neustart nicht verfügbar"
            return
        }
        focusPending = true
        message = "Macro fokussiert …"
        try {
            val focus = boundCamera.cameraControl.startFocusAndMetering(action)
            focus.addListener({
                if (generation != token) return@addListener
                val focused = runCatching { focus.get().isFocusSuccessful }.getOrDefault(false)
                // A one-shot AF trigger temporarily locks focus. Cancel it so
                // subsequent near subjects continue to receive continuous AF.
                try {
                    val cancel = boundCamera.cameraControl.cancelFocusAndMetering()
                    cancel.addListener({
                        if (generation == token) {
                            focusPending = false
                            message = if (runCatching { cancel.get() }.isFailure)
                                "Macro: AF-Rücksetzung nicht bestätigt. Erneut starten."
                            else if (focused) "Macro: kontinuierlicher AF aktiv"
                            else "Macro: Fokus nicht bestätigt; kontinuierlicher AF aktiv"
                            emit()
                        }
                    }, mainExecutor)
                } catch (error: Exception) {
                    focusPending = false
                    message = "Macro: AF-Rücksetzung fehlgeschlagen: ${error.message}"
                    emit()
                }
            }, mainExecutor)
        } catch (error: Exception) {
            focusPending = false
            message = "Macro: Fokus-Neustart fehlgeschlagen: ${error.message}"
        }
    }

    private fun isReady() = cameraOpen && streaming && zoomReady && !busy && !focusPending

    fun setRotation(targetRotation: Int) {
        rotation = targetRotation
        imageCapture?.targetRotation = targetRotation
    }

    fun capturePhoto() {
        val capture = imageCapture ?: return
        if (!isReady()) return
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
        clearSession()
        owner = null
        previewView = null
    }

    private fun clearSession() {
        generation++
        owner?.let { lifecycleOwner ->
            camera?.cameraInfo?.cameraState?.removeObservers(lifecycleOwner)
            previewView?.previewStreamState?.removeObservers(lifecycleOwner)
        }
        releaseUseCases()
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
        zoomReady = false
        focusPending = false
        focusTriggered = false
    }

    private fun emit() {
        val size = imageCapture?.resolutionInfo?.resolution
        publish(PhotoCameraState(
            lens = activeLens,
            ready = isReady(),
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
