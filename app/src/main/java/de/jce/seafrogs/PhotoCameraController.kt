package de.jce.seafrogs

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.CaptureResult
import android.hardware.camera2.TotalCaptureResult
import androidx.camera.extensions.ExtensionsManager
import androidx.camera.extensions.ExtensionMode
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
import org.json.JSONObject
import java.util.Locale
import java.util.UUID
import java.util.concurrent.TimeUnit

data class PhotoCameraState(
    val lens: PhotoLens = PhotoLens.MAIN,
    val zoomLabel: String = "1×",
    val exposureLabel: String = "0",
    val exposureSupported: Boolean = false,
    val ready: Boolean = false,
    val capturing: Boolean = false,
    val message: String = "Kamera startet",
    val resolution: String = "",
    val quality: String = "STANDARD",
    val diagnostics: String = ""
)

/**
 * UI and the HID adapter share camera commands; neither needs camera APIs.
 * All public calls and callbacks use the main thread. CameraX handles image I/O.
 * Each start/stop advances a generation, so old async callbacks cannot rebind
 * the camera or update a newer session after Activity focus/lifecycle changes.
 */
@androidx.annotation.OptIn(markerClass = [ExperimentalCamera2Interop::class])
class PhotoCameraController(
    context: Context,
    private val log: (JSONObject) -> Unit,
    private val publish: (PhotoCameraState) -> Unit
) {
    private val appContext = context.applicationContext
    private val exifWriter = PhotoExifWriter(appContext)
    private var testCase = "FREI"
    private var extensions: ExtensionsManager? = null
    private var extensionsAttempted = false
    private var qualityMode = ExtensionMode.NONE
    private var extensionSummary = "Erweiterungen werden geprüft"
    @Volatile private var latestResult: String = "{}"
    private var lastTelemetry = 0L
    private val autofocusStability = AutofocusStability()
    private val qualityModes = listOf(ExtensionMode.NONE, ExtensionMode.AUTO, ExtensionMode.HDR, ExtensionMode.NIGHT)
    private fun qualityName(mode: Int) = when (mode) {
        ExtensionMode.AUTO -> "AUTO"
        ExtensionMode.HDR -> "HDR"
        ExtensionMode.NIGHT -> "NIGHT"
        else -> "STANDARD"
    }
    private var activeRoute: CameraLensRoute? = null
    private var macroEntryFocusResult = "not_requested"
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
    private var zoomRatio = 1f
    private var exposureEv = 0f
    private var exposureReady = false
    private var controlPending = false
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

    /** Touch and HID share the same lens command and readiness guard. */
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
        zoomRatio = 1f
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
                if (!extensionsAttempted) {
                    extensionsAttempted = true
                    val extensionFuture = ExtensionsManager.getInstanceAsync(appContext, availableProvider)
                    extensionFuture.addListener({
                        if (generation != token) { extensionsAttempted = false; return@addListener }
                        extensions = runCatching { extensionFuture.get() }.getOrNull()
                        extensionSummary = if (extensions == null) "Extensions-Initialisierung fehlgeschlagen" else "Extensions bereit"
                        open(target, fallback, notice)
                    }, mainExecutor)
                    return@addListener
                }
                val lenses = catalog ?: CameraLensCatalog(appContext, availableProvider).also {
                    catalog = it
                }
                val route = checkNotNull(lenses.routeFor(target)) {
                    "Keine geeignete Kamera für ${target.label}"
                }
                val modes = qualityModes.drop(1).filter { mode ->
                    runCatching { extensions?.isExtensionAvailable(route.selector(), mode) == true }.getOrDefault(false)
                }
                extensionSummary = if (route.physicalId != null)
                    "Physischer Sensor: Extensions nicht sicher zuordenbar"
                else "Verfügbar: " + (modes.map(::qualityName).joinToString().ifBlank { "keine" })
                log(JSONObject().put("kind", "cameraCapabilities").put("lens", target.name)
                    .put("logicalId", route.logicalId).put("physicalId", route.physicalId ?: JSONObject.NULL)
                    .put("extensionsOnLogicalSelector", org.json.JSONArray(modes.map(::qualityName)))
                    .put("extensionsAllowedOnRoute", route.physicalId == null))
                if (route.physicalId != null || qualityMode !in modes) qualityMode = ExtensionMode.NONE
                bind(availableProvider, lifecycleOwner, view, route)
                activeRoute = route
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
                val zoom = boundCamera.cameraControl.setZoomRatio(zoomRatio)
                zoom.addListener({
                    if (generation == token) {
                        try {
                            zoom.get()
                            zoomReady = true
                            restoreExposure(boundCamera, token, target, fallback)
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
        log(JSONObject().put("kind", "cameraFailure").put("message", detail))
        if (qualityMode != ExtensionMode.NONE) {
            qualityMode = ExtensionMode.NONE
            open(target, fallback, "Extension fehlgeschlagen; STANDARD: $detail")
            return
        }
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
        val previewResolution = ResolutionSelector.Builder()
            .setAspectRatioStrategy(AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY)
        val captureResolution = ResolutionSelector.Builder()
            .setAllowedResolutionMode(ResolutionSelector.PREFER_HIGHER_RESOLUTION_OVER_CAPTURE_RATE)
            .setAspectRatioStrategy(AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY)
            .setResolutionStrategy(ResolutionStrategy.HIGHEST_AVAILABLE_STRATEGY)
        if (route.physicalId != null) {
            val routes = checkNotNull(catalog)
            val jpegSizes = routes.commonOutputSizes(route, android.graphics.ImageFormat.JPEG)
                .filter { kotlin.math.abs(it.width.toDouble() / it.height - 4.0 / 3.0) < 0.02 }.toSet()
            val previewSizes = routes.commonOutputSizes(route, android.graphics.ImageFormat.PRIVATE)
            check(jpegSizes.isNotEmpty()) { "Keine gemeinsame 4:3-JPEG-Größe für UW/Macro" }
            check(previewSizes.isNotEmpty()) { "Keine gemeinsame Vorschaugröße für UW/Macro" }
            captureResolution.setResolutionFilter { supported, _ ->
                supported.filter { it in jpegSizes }.sortedByDescending { it.width.toLong() * it.height }
            }
            previewResolution.setResolutionFilter { supported, _ -> supported.filter { it in previewSizes } }
            log(JSONObject().put("kind", "physicalStreamResolutionCandidates")
                .put("logicalId", route.logicalId).put("physicalId", route.physicalId)
                .put("jpegSizes", org.json.JSONArray(jpegSizes.sortedByDescending { it.width.toLong() * it.height }.map { it.toString() }))
                .put("previewSizes", org.json.JSONArray(previewSizes.map { it.toString() })))
        }
        val previewBuilder = Preview.Builder().setResolutionSelector(previewResolution.build())
        val captureBuilder = ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
            .setJpegQuality(100)
            .setTargetRotation(rotation)
            .setResolutionSelector(captureResolution.build())
        // Both output streams must use the same physical sensor. The logical
        // parent supplies CameraX controls; their effect needs device validation.
        if (Build.VERSION.SDK_INT >= 28) route.physicalId?.let { physicalId ->
            Camera2Interop.Extender(previewBuilder).setPhysicalCameraId(physicalId)
            Camera2Interop.Extender(captureBuilder).setPhysicalCameraId(physicalId)
        }
        val token = generation
        latestResult = "{}"
        lastTelemetry = 0L
        autofocusStability.reset()
        val callback = object : CameraCaptureSession.CaptureCallback() {
            override fun onCaptureCompleted(session: CameraCaptureSession, request: CaptureRequest, result: TotalCaptureResult) {
                val value = JSONObject().put("kind", "cameraResult")
                    .put("sensorTimestampNs", result.get(CaptureResult.SENSOR_TIMESTAMP) ?: JSONObject.NULL)
                    .put("iso", result.get(CaptureResult.SENSOR_SENSITIVITY) ?: JSONObject.NULL)
                    .put("exposureTimeNs", result.get(CaptureResult.SENSOR_EXPOSURE_TIME) ?: JSONObject.NULL)
                    .put("focalLengthMm", result.get(CaptureResult.LENS_FOCAL_LENGTH)?.toDouble() ?: JSONObject.NULL)
                    .put("focusDistanceDiopters", result.get(CaptureResult.LENS_FOCUS_DISTANCE)?.toDouble() ?: JSONObject.NULL)
                    .put("afState", result.get(CaptureResult.CONTROL_AF_STATE) ?: JSONObject.NULL)
                    .put("afMode", result.get(CaptureResult.CONTROL_AF_MODE) ?: JSONObject.NULL)
                    .put("cropRegion", result.get(CaptureResult.SCALER_CROP_REGION)?.toShortString() ?: JSONObject.NULL)
                    .put("activePhysicalId", if (Build.VERSION.SDK_INT >= 29)
                        result.get(CaptureResult.LOGICAL_MULTI_CAMERA_ACTIVE_PHYSICAL_ID) ?: JSONObject.NULL else JSONObject.NULL)
                    .put("physicalResults", org.json.JSONArray().apply {
                        if (Build.VERSION.SDK_INT >= 28) result.physicalCameraResults.forEach { (id, physical) ->
                            put(JSONObject().put("id", id)
                                .put("afState", physical.get(CaptureResult.CONTROL_AF_STATE) ?: JSONObject.NULL)
                                .put("afMode", physical.get(CaptureResult.CONTROL_AF_MODE) ?: JSONObject.NULL)
                                .put("focusDistanceDiopters", physical.get(CaptureResult.LENS_FOCUS_DISTANCE)?.toDouble() ?: JSONObject.NULL)
                                .put("focalLengthMm", physical.get(CaptureResult.LENS_FOCAL_LENGTH)?.toDouble() ?: JSONObject.NULL)
                                .put("iso", physical.get(CaptureResult.SENSOR_SENSITIVITY) ?: JSONObject.NULL)
                                .put("exposureTimeNs", physical.get(CaptureResult.SENSOR_EXPOSURE_TIME) ?: JSONObject.NULL))
                        }
                    })
                mainExecutor.execute {
                    if (generation != token) return@execute
                    latestResult = value.toString()
                    val now = android.os.SystemClock.uptimeMillis()
                    // A logical AF result cannot confirm focus on a pinned sensor.
                    val focusResult: CaptureResult? = if (Build.VERSION.SDK_INT >= 28 && route.physicalId != null)
                        result.physicalCameraResults[route.physicalId] else result
                    val af = focusResult?.get(CaptureResult.CONTROL_AF_STATE)
                    val focused = af?.let { it == CaptureResult.CONTROL_AF_STATE_PASSIVE_FOCUSED ||
                        it == CaptureResult.CONTROL_AF_STATE_FOCUSED_LOCKED }
                    autofocusStability.frame(focused, result.get(CaptureResult.SENSOR_TIMESTAMP), now)
                    if (now - lastTelemetry >= 1000) { lastTelemetry = now; log(value); emit() }
                }
            }
        }
        // OEM extensions own their sessions; do not inject interop callbacks there.
        if (qualityMode == ExtensionMode.NONE) Camera2Interop.Extender(previewBuilder)
            .setSessionCaptureCallback(callback)
        val selector = if (qualityMode == ExtensionMode.NONE) route.selector()
            else checkNotNull(extensions).getExtensionEnabledCameraSelector(route.selector(), qualityMode)
        preview = previewBuilder.build().also { it.setSurfaceProvider(view.surfaceProvider) }
        imageCapture = captureBuilder.build()
        camera = availableProvider.bindToLifecycle(
            lifecycleOwner, selector, preview!!, imageCapture!!
        )
    }

    private fun maybeRestartMacroFocus(token: Int) {
        if (activeLens != PhotoLens.MACRO || focusTriggered ||
            !cameraOpen || !streaming || !zoomReady || !exposureReady) return
        focusTriggered = true
        macroEntryFocusResult = "pending"
        val boundCamera = camera ?: return
        val view = previewView ?: return
        val point = view.meteringPointFactory.createPoint(view.width / 2f, view.height / 2f)
        val action = FocusMeteringAction.Builder(point, FocusMeteringAction.FLAG_AF)
            .setAutoCancelDuration(5, TimeUnit.SECONDS).build()
        if (!boundCamera.cameraInfo.isFocusMeteringSupported(action)) {
            macroEntryFocusResult = "unsupported"
            message = "Macro: kontinuierlicher AF; Fokus-Neustart nicht verfügbar"
            return
        }
        focusPending = true
        message = "Macro fokussiert …"
        try {
            val focus = boundCamera.cameraControl.startFocusAndMetering(action)
            focus.addListener({
                if (generation != token) return@addListener
                val outcome = runCatching { focus.get().isFocusSuccessful }
                val focused = outcome.getOrDefault(false)
                macroEntryFocusResult = when {
                    outcome.isFailure -> "failed:" + (outcome.exceptionOrNull()?.cause
                        ?: outcome.exceptionOrNull())?.javaClass?.simpleName
                    focused -> "success"
                    else -> "not_confirmed"
                }
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
            macroEntryFocusResult = "failed:${error.javaClass.simpleName}"
            message = "Macro: Fokus-Neustart fehlgeschlagen: ${error.message}"
        }
    }

    private fun isReady() = cameraOpen && streaming && zoomReady && exposureReady &&
        !busy && !focusPending && !controlPending

    private fun restoreExposure(
        boundCamera: Camera, token: Int, target: PhotoLens, fallback: PhotoLens?
    ) {
        val exposure = boundCamera.cameraInfo.exposureState
        if (!exposure.isExposureCompensationSupported) {
            exposureEv = 0f
            exposureReady = true
            maybeRestartMacroFocus(token)
            emit()
            return
        }
        try {
            val step = exposure.exposureCompensationStep.toFloat()
            val range = exposure.exposureCompensationRange
            val index = CameraControlCycles.exposureIndex(exposureEv, step, range.lower, range.upper)
            val operation = boundCamera.cameraControl.setExposureCompensationIndex(index)
            operation.addListener({
                if (generation == token) {
                    try {
                        exposureEv = operation.get() * step
                        exposureReady = true
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
    }

    fun cycleQuality() {
        if (!isReady()) return
        val route = activeRoute ?: return
        if (route.physicalId != null) {
            message = "Extensions nur ohne erzwungenen physischen Sensor testen"
            emit(); return
        }
        val supported = qualityModes.filter { it == ExtensionMode.NONE ||
            runCatching { extensions?.isExtensionAvailable(route.selector(), it) == true }.getOrDefault(false) }
        qualityMode = supported[(supported.indexOf(qualityMode) + 1) % supported.size]
        zoomRatio = 1f
        exposureEv = 0f
        open(activeLens)
    }

    fun setTestCase(value: String) {
        testCase = value
        log(JSONObject().put("kind", "testCase").put("label", value))
    }

    fun autoTestQualities(lens: PhotoLens): List<String> {
        val route = catalog?.routeFor(lens) ?: return emptyList()
        return qualityModes.filter { it == ExtensionMode.NONE || (route.physicalId == null &&
            runCatching { extensions?.isExtensionAvailable(route.selector(), it) == true }.getOrDefault(false)) }
            .map(::qualityName)
    }

    fun configureAutoTest(step: AutoTestStep): Boolean {
        if (busy || owner == null || catalog?.routeFor(step.lens) == null) return false
        val mode = qualityModes.firstOrNull { qualityName(it) == step.quality } ?: return false
        if (step.quality !in autoTestQualities(step.lens)) return false
        testCase = step.id
        zoomRatio = step.zoom
        exposureEv = step.ev
        qualityMode = mode
        cyclePosition = step.lens
        open(step.lens)
        return true
    }

    fun autoTestMatches(step: AutoTestStep) = isReady() && activeLens == step.lens &&
        kotlin.math.abs(zoomRatio - step.zoom) < 0.01f && qualityName(qualityMode) == step.quality

    fun readyForCommand() = isReady()

    fun autoTestFocusStatus(): String = when {
        qualityMode != ExtensionMode.NONE -> "UNVERIFIED_EXTENSION"
        activeRoute?.supportsPhotoAutofocus != true -> "NOT_REQUIRED_FIXED_FOCUS"
        autofocusStability.stable(android.os.SystemClock.uptimeMillis()) -> "STABLE_PREVIEW_AF"
        else -> "WAITING_FOR_PREVIEW_AF"
    }

    fun cycleZoom() {
        if (!isReady()) return
        val boundCamera = camera ?: return
        val zoomState = boundCamera.cameraInfo.zoomState.value ?: return
        val next = CameraControlCycles.nextZoom(zoomRatio, activeLens == PhotoLens.MACRO,
            zoomState.minZoomRatio, zoomState.maxZoomRatio, activeLens == PhotoLens.ULTRAWIDE)
        if (next == null || next == zoomRatio) {
            message = "Keine weitere Zoomstufe unterstützt"
            emit()
            return
        }
        val token = generation
        controlPending = true
        message = "Zoom wird eingestellt"
        emit()
        try {
            val operation = boundCamera.cameraControl.setZoomRatio(next)
            operation.addListener({
                if (generation == token) {
                    controlPending = false
                    message = try {
                        operation.get()
                        zoomRatio = next
                        "Zoom eingestellt"
                    } catch (error: Exception) {
                        "Zoom fehlgeschlagen: ${error.cause?.message ?: error.message}"
                    }
                    emit()
                }
            }, mainExecutor)
        } catch (error: Exception) {
            controlPending = false
            message = "Zoom fehlgeschlagen: ${error.message}"
            emit()
        }
    }

    fun cycleExposure() {
        if (!isReady()) return
        val boundCamera = camera ?: return
        val exposure = boundCamera.cameraInfo.exposureState
        if (!exposure.isExposureCompensationSupported) {
            message = "Belichtungskorrektur nicht unterstützt"
            emit()
            return
        }
        val token = generation
        try {
            val step = exposure.exposureCompensationStep.toFloat()
            val range = exposure.exposureCompensationRange
            val next = CameraControlCycles.nextExposure(exposure.exposureCompensationIndex,
                step, range.lower, range.upper)
            controlPending = true
            message = "Belichtung wird eingestellt"
            emit()
            val operation = boundCamera.cameraControl.setExposureCompensationIndex(next)
            operation.addListener({
                if (generation == token) {
                    controlPending = false
                    message = try {
                        exposureEv = operation.get() * step
                        "Belichtung eingestellt"
                    } catch (error: Exception) {
                        "Belichtung fehlgeschlagen: ${error.cause?.message ?: error.message}"
                    }
                    emit()
                }
            }, mainExecutor)
        } catch (error: Exception) {
            controlPending = false
            message = "Belichtung fehlgeschlagen: ${error.message}"
            emit()
        }
    }

    private fun zoomLabel(): String = if (activeLens == PhotoLens.MACRO) when (zoomRatio) {
        1f -> "0,5×"
        2f -> "1× Crop"
        4f -> "2× Crop"
        else -> "${number(zoomRatio)}× Cropfaktor"
    } else "${number(zoomRatio)}×"

    private fun number(value: Float): String = String.format(Locale.GERMANY, "%.2f", value)
        .trimEnd('0').trimEnd(',')

    fun setRotation(targetRotation: Int) {
        rotation = targetRotation
        imageCapture?.targetRotation = targetRotation
    }

    fun capturePhoto(completed: ((android.net.Uri?, String?) -> Unit)? = null) {
        val capture = imageCapture
        if (capture == null || !isReady()) {
            completed?.invoke(null, "Kamera nicht bereit")
            return
        }
        busy = true
        message = "Foto wird aufgenommen"
        emit()
        val token = generation
        val metadata = exifSnapshot()
        log(JSONObject(metadata).put("kind", "shutterRequest"))
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
                        completed?.invoke(null, "Speicherordner konnte nicht angelegt werden")
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
                    // Finish metadata even when this session has stopped. Only
                    // UI updates depend on its generation; the JPEG still exists.
                    log(JSONObject().put("kind", "photoSaved").put("fileName", name)
                        .put("uri", result.savedUri?.toString() ?: JSONObject.NULL))
                    exifWriter.write(result.savedUri, metadata) { failure ->
                        mainExecutor.execute {
                            completed?.invoke(result.savedUri, failure)
                            if (generation == token) {
                                busy = false
                                message = if (failure == null) "JPEG mit EXIF gespeichert: Pictures/SeaFrogs"
                                else "JPEG gespeichert; EXIF-Ergaenzung fehlgeschlagen: $failure"
                                emit()
                            }
                        }
                    }
                }
                override fun onError(error: ImageCaptureException) {
                    completed?.invoke(null, error.message ?: "Aufnahmefehler ${error.imageCaptureError}")
                    if (generation != token) return
                    busy = false
                    message = "Aufnahmefehler: ${error.message ?: error.imageCaptureError}"
                    emit()
                }
            })
        } catch (error: Exception) {
            completed?.invoke(null, error.message ?: "Aufnahmefehler")
            busy = false
            message = "Aufnahmefehler: ${error.message}"
            emit()
        }
    }

    private fun exifSnapshot(): String {
        val exposure = camera?.cameraInfo?.exposureState
        val zoom = camera?.cameraInfo?.zoomState?.value
        val size = imageCapture?.resolutionInfo?.resolution
        return JSONObject()
            .put("app", "SeaFrogs Camera")
            .put("version", "0.6.2-resolution-af")
            .put("mode", "PHOTO")
            .put("testCase", testCase)
            .put("qualityMode", qualityName(qualityMode))
            .put("latestPreviewResultNotPhotoResult", JSONObject(latestResult))
            .put("lensMode", activeLens.name)
            .put("logicalCameraId", activeRoute?.logicalId ?: JSONObject.NULL)
            .put("requestedPhysicalCameraId", activeRoute?.physicalId ?: JSONObject.NULL)
            .put("appliedCameraXZoomRatio", zoomRatio.toDouble())
            .put("nominalMacroMainEquivalent", if (activeLens == PhotoLens.MACRO)
                (zoomRatio / 2f).toDouble() else JSONObject.NULL)
            .put("appliedExposureEV", exposureEv.toDouble())
            .put("exposureIndex", exposure?.exposureCompensationIndex ?: JSONObject.NULL)
            .put("exposureStep", exposure?.exposureCompensationStep?.toString() ?: JSONObject.NULL)
            .put("exposureMinIndex", exposure?.exposureCompensationRange?.lower ?: JSONObject.NULL)
            .put("exposureMaxIndex", exposure?.exposureCompensationRange?.upper ?: JSONObject.NULL)
            .put("zoomMinRatio", zoom?.minZoomRatio?.toDouble() ?: JSONObject.NULL)
            .put("zoomMaxRatio", zoom?.maxZoomRatio?.toDouble() ?: JSONObject.NULL)
            .put("macroEntryFocusResult", macroEntryFocusResult)
            .put("configuredPhotoWidth", size?.width ?: JSONObject.NULL)
            .put("configuredPhotoHeight", size?.height ?: JSONObject.NULL)
            .put("jpegTargetRotation", rotation)
            .put("androidBuild", Build.FINGERPRINT)
            .toString()
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
        activeRoute = null
        macroEntryFocusResult = "not_requested"
        cameraOpen = false
        streaming = false
        busy = false
        zoomReady = false
        exposureReady = false
        controlPending = false
        focusPending = false
        focusTriggered = false
        autofocusStability.reset()
    }

    private fun emit() {
        val size = imageCapture?.resolutionInfo?.resolution
        publish(PhotoCameraState(
            lens = activeLens,
            zoomLabel = zoomLabel(),
            exposureLabel = (if (exposureEv > 0f) "+" else "") + number(exposureEv),
            exposureSupported = camera?.cameraInfo?.exposureState?.isExposureCompensationSupported == true,
            ready = isReady(),
            capturing = busy,
            message = message,
            resolution = size?.let { "${it.width} × ${it.height}" } ?: "",
            quality = qualityName(qualityMode),
            diagnostics = extensionSummary + "\n" + resultSummary()
        ))
    }

    private fun resultSummary(): String {
        val logical = JSONObject(latestResult)
        if (logical.length() == 0) return "CaptureResult nicht verfügbar"
        val requested = activeRoute?.physicalId
        val physical = logical.optJSONArray("physicalResults")
        val matching = physical?.let { values ->
            (0 until values.length()).map { values.getJSONObject(it) }
                .firstOrNull { it.optString("id") == requested }
        }
        val result = matching ?: logical
        val source = if (matching != null) "PHYS ${matching.optString("id")}" else
            "LOG ${logical.optString("activePhysicalId", "?")}"
        return "$source | AF ${result.optString("afState", "?")} | " +
            "Fokus ${result.optString("focusDistanceDiopters", "?")} dpt | ISO ${result.optString("iso", "?")}"
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
