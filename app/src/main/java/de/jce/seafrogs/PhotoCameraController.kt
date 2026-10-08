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
import androidx.camera.video.VideoCapture
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.MediaStoreOutputOptions
import androidx.camera.video.VideoRecordEvent
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
    val exposureAdjustable: Boolean = false,
    val ready: Boolean = false,
    val capturing: Boolean = false,
    val message: String = "Kamera startet",
    val resolution: String = "",
    val quality: String = "STANDARD",
    val diagnostics: String = "",
    val photoFormat: String = "JPEG",
    val videoMode: Boolean = false,
    val recording: Boolean = false,
    val videoFps: Int = 30,
    val exposureLimits: ExposureLimits = ExposureLimits()
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
    private var videoCapture: VideoCapture<Recorder>? = null
    private var recording: Recording? = null
    private var videoOperation = 0
    private var videoMode = false
    private val wbPreferences = appContext.getSharedPreferences("whiteBalance", Context.MODE_PRIVATE)
    private var whiteBalanceMode = wbPreferences.getInt("mode", CaptureRequest.CONTROL_AWB_MODE_AUTO)
    private var manualWhiteBalance: ManualWhiteBalance? = null
    private var manualWbStatus=""
    private var wbStrength=wbPreferences.getInt("strength",50).coerceIn(0,100)
    fun whiteBalanceStrength()=wbStrength
    fun setWhiteBalanceStrength(value: Int) {
        if(!isReady() || recording!=null) return
        wbStrength=value.coerceIn(0,100); wbPreferences.edit().putInt("strength",wbStrength).apply()
        open(activeLens)
    }
    private var appliedWhiteBalanceMode = CaptureRequest.CONTROL_AWB_MODE_AUTO
    private val wbNames = listOf("Auto" to CaptureRequest.CONTROL_AWB_MODE_AUTO,
        "Tageslicht" to CaptureRequest.CONTROL_AWB_MODE_DAYLIGHT,
        "Bewölkt" to CaptureRequest.CONTROL_AWB_MODE_CLOUDY_DAYLIGHT,
        "Schatten" to CaptureRequest.CONTROL_AWB_MODE_SHADE)
    fun whiteBalanceLabel(): String = WhiteBalanceLabels.label(whiteBalanceMode,appliedWhiteBalanceMode,
        manualWhiteBalance!=null,wbStrength,manualWbStatus,wbNames,ManualWhiteBalance.names)
    fun whiteBalanceChoices(): List<Pair<String,Int>> {
        val route = activeRoute ?: return wbNames.take(1)
        val manager = appContext.getSystemService(android.hardware.camera2.CameraManager::class.java)
        val available = manager.getCameraCharacteristics(route.physicalId ?: route.logicalId)[android.hardware.camera2.CameraCharacteristics.CONTROL_AWB_AVAILABLE_MODES] ?: intArrayOf(CaptureRequest.CONTROL_AWB_MODE_AUTO)
        val presets=wbNames.filter { it.second in available }
        return presets + ManualWhiteBalance.names.mapIndexedNotNull { index,name ->
            val c=manager.getCameraCharacteristics(route.physicalId ?: route.logicalId)
            if(runCatching { ManualWhiteBalance.build(appContext,c,100+index,wbStrength) }.isSuccess) name to 100+index else null
        }
    }
    fun setWhiteBalance(mode: Int) {
        if (!isReady() || recording != null || whiteBalanceChoices().none { it.second == mode }) return
        whiteBalanceMode=mode; wbPreferences.edit().putInt("mode",mode).apply()
        if(mode != CaptureRequest.CONTROL_AWB_MODE_AUTO) qualityMode=ExtensionMode.NONE
        open(activeLens)
    }
    private var videoSeconds = 0L
    private var videoFps = appContext.getSharedPreferences("video", Context.MODE_PRIVATE).getInt("fps", 30)
        .takeIf { it == 30 || it == 60 } ?: 30
    private var owner: LifecycleOwner? = null
    private var previewView: PreviewView? = null
    private var generation = 0
    private var persistentPhoto: RawJpegCapture? = null
    private var persistentTexture: android.view.TextureView? = null
    private var persistentSurface: android.view.Surface? = null
    private var persistentSize: android.util.Size? = null
    private var persistentEvSupported = false
    private var persistentPreviewSize: android.util.Size? = null
    private var persistentZoomMaximum = 1f
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
    private var exposureOperation = 0
    private var persistentEvPending = false
    private var zoomReady = false
    private var focusPending = false
    private var focusTriggered = false
    private var rotation = Surface.ROTATION_0
    private var message = "Kamera startet"
    private val formatPreferences = appContext.getSharedPreferences("photoFormat", Context.MODE_PRIVATE)
    private var rawEnabled = formatPreferences.getBoolean("rawJpeg", false)
    private var nativeEnabled = false // Experimental fusion is frozen.
    private var testNativeFusionOverride: Boolean? = null
    private fun usesNativeFusion() = testNativeFusionOverride ?: nativeEnabled
    private fun selectedQualityName() = if (usesNativeFusion()) "MEHRBILD" else qualityName(qualityMode)
    private var testRawOverride: Boolean? = null
    private var rawCapture: RawJpegCapture? = null
    private var lastRawOutcome: RawPairOutcome? = null
    private fun usesRaw() = testRawOverride ?: rawEnabled
    private val exposurePreferences = appContext.getSharedPreferences("exposureLimits", Context.MODE_PRIVATE)
    private var exposureLimits = ExposureLimits(
        exposurePreferences.getInt("isoCap", 0).takeIf { it in ExposureLimits.isoChoices } ?: 0,
        exposurePreferences.getLong("longestTimeNs", 33_333_333L).takeIf { it in ExposureLimits.timeChoices } ?: 33_333_333L,
        exposurePreferences.getInt("boostCap", 0).takeIf { it in ExposureLimits.boostChoices } ?: 0)
    private var testLimitsOverride: ExposureLimits? = null
    private var testNativeCapture = false
    private var testProcessing = ProcessingVariant.NONE
    private var testFrameCount = 1
    private val comparisonReferences = mutableMapOf<PhotoLens, FrozenCaptureSettings>()
    private fun effectiveLimits() = testLimitsOverride ?: exposureLimits
    fun savedExposureLimits() = exposureLimits

    fun setExposureLimits(value: ExposureLimits) {
        if (videoMode || !isReady() || value.isoCap !in ExposureLimits.isoChoices || value.longestTimeNs !in ExposureLimits.timeChoices || value.boostCap !in ExposureLimits.boostChoices) return
        exposureLimits = value
        exposurePreferences.edit().putInt("isoCap", value.isoCap).putLong("longestTimeNs", value.longestTimeNs).putInt("boostCap", value.boostCap).apply()
        if (value.enabled) qualityMode = ExtensionMode.NONE
        open(activeLens)
    }

    fun setRawEnabled(value: Boolean) {
        if (videoMode || !isReady()) return
        rawEnabled = value
        formatPreferences.edit().putBoolean("rawJpeg", value).apply()
        if (value) qualityMode = ExtensionMode.NONE
        open(activeLens)
    }

    fun endAutoTest() {
        testNativeFusionOverride = null
        testRawOverride = null
        testLimitsOverride = null
        testNativeCapture = false
        testProcessing = ProcessingVariant.NONE
        testFrameCount = 1
        comparisonReferences.clear()
        if (usesNativeFusion() || usesRaw() || effectiveLimits().enabled) qualityMode = ExtensionMode.NONE
        // Rebind after a cancelled extension run too, so saved format and
        // limits cannot coexist with a stale extension session.
        if (!busy && owner != null) open(activeLens) else emit()
    }
    fun rawOutcome() = lastRawOutcome

    fun start(lifecycleOwner: LifecycleOwner, view: PreviewView) {
        stop()
        owner = lifecycleOwner
        previewView = view
        open(activeLens)
    }

    /** Touch and HID share the same lens command and readiness guard. */
    fun cycleLens() {
        if (recording != null) return
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
                if (whiteBalanceMode != CaptureRequest.CONTROL_AWB_MODE_AUTO || videoMode || usesRaw() || effectiveLimits().enabled || route.physicalId != null || qualityMode !in modes) qualityMode = ExtensionMode.NONE
                if (!videoMode && !testNativeCapture && !usesNativeFusion() && testCase == "FREI" &&
                    (usesRaw() || effectiveLimits().enabled)) {
                    activeRoute = route; activeLens = target
                    bindPersistentPhoto(view,route,token,notice)
                    return@addListener
                }
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
        if (videoMode) {
            videoMode = false
            open(target, fallback, "Video nicht verfügbar: $detail. Zurück zu FOTO.")
            return
        }
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

    private fun bindPersistentPhoto(view: PreviewView, route: CameraLensRoute, token: Int, notice: String?) {
        val lenses = checkNotNull(catalog)
        val jpeg = lenses.commonOutputSizes(route, android.graphics.ImageFormat.JPEG)
            .filter { kotlin.math.abs(it.width.toDouble()/it.height - 4.0/3.0)<0.02 }
            .maxByOrNull { it.width.toLong()*it.height } ?: error("Keine 4:3-JPEG-Größe verfügbar")
        val raw = if(usesRaw()) lenses.rawSize(route) ?: error("RAW nicht unterstützt") else null
        val sizes = lenses.commonOutputSizes(route,android.graphics.ImageFormat.PRIVATE)
        val previewSize = sizes.filter { it.width<=1920 && it.height<=1440 && kotlin.math.abs(it.width.toDouble()/it.height-4.0/3.0)<0.02 }
            .maxByOrNull { it.width.toLong()*it.height } ?: sizes.minByOrNull { kotlin.math.abs(it.width.toDouble()/it.height-4.0/3.0) }
            ?: error("Vorschaugröße fehlt")
        val manager=appContext.getSystemService(android.hardware.camera2.CameraManager::class.java)
        val c=manager.getCameraCharacteristics(route.physicalId ?: route.logicalId)
        val logical=manager.getCameraCharacteristics(route.logicalId)
        persistentZoomMaximum=(c[android.hardware.camera2.CameraCharacteristics.SCALER_AVAILABLE_MAX_DIGITAL_ZOOM] ?: 1f).coerceAtLeast(1f)
        zoomRatio=zoomRatio.coerceIn(1f,persistentZoomMaximum)
        val evStep=logical[android.hardware.camera2.CameraCharacteristics.CONTROL_AE_COMPENSATION_STEP]?.toFloat() ?: 0f
        val evRange=logical[android.hardware.camera2.CameraCharacteristics.CONTROL_AE_COMPENSATION_RANGE]
        persistentEvSupported=evStep>0 && evRange!=null && evRange.lower<evRange.upper
        exposureEv=if(persistentEvSupported) CameraControlCycles.exposureIndex(exposureEv,evStep,evRange!!.lower,evRange.upper)*evStep else 0f
        manualWhiteBalance=if(whiteBalanceMode in 100..103) runCatching { ManualWhiteBalance.build(appContext,c,whiteBalanceMode,wbStrength) }.getOrNull() else null
        val modes=c[android.hardware.camera2.CameraCharacteristics.CONTROL_AWB_AVAILABLE_MODES] ?: intArrayOf(CaptureRequest.CONTROL_AWB_MODE_AUTO)
        appliedWhiteBalanceMode=if(manualWhiteBalance!=null) CaptureRequest.CONTROL_AWB_MODE_OFF else if(whiteBalanceMode in modes) whiteBalanceMode else CaptureRequest.CONTROL_AWB_MODE_AUTO
        manualWbStatus=if(manualWhiteBalance!=null) "WB NICHT BESTÄTIGT" else if(whiteBalanceMode in 100..103) " · Profil nicht verfügbar: Auto" else ""
        persistentSize=jpeg; persistentPreviewSize=previewSize
        extensionSummary="Dauerhafte Camera2-Fotositzung · kein Neustart beim Auslösen"
        val operation=RawJpegCapture(appContext); persistentPhoto=operation
        val texture=android.view.TextureView(view.context); persistentTexture=texture
        texture.surfaceTextureListener=object : android.view.TextureView.SurfaceTextureListener {
            override fun onSurfaceTextureAvailable(surface: android.graphics.SurfaceTexture, width: Int, height: Int) {
                if(generation!=token) return
                try {
                surface.setDefaultBufferSize(previewSize.width,previewSize.height)
                val output=android.view.Surface(surface);persistentSurface=output
                transformPersistentPreview()
                operation.openPersistent(route,jpeg,raw,output,zoomRatio,exposureEv,lenses.jpegOrientation(route,rotation),
                    effectiveLimits(),appliedWhiteBalanceMode,manualWhiteBalance,onPreview={ result ->
                        if(generation==token) {
                            val first=!cameraOpen
                            cameraOpen=true;streaming=true;zoomReady=true;exposureReady=true
                            if(first) message=notice ?: "Bereit"
                            manualWhiteBalance?.let { manualWbStatus=it.verification(result) }
                            latestResult=JSONObject().put("iso",result[CaptureResult.SENSOR_SENSITIVITY] ?: JSONObject.NULL)
                                .put("afState",result[CaptureResult.CONTROL_AF_STATE] ?: JSONObject.NULL)
                                .put("focusDistanceDiopters",result[CaptureResult.LENS_FOCUS_DISTANCE] ?: JSONObject.NULL).toString()
                            emit()
                        }
                    },onError={ error -> if(generation==token) { clearSession();message="Kamerafehler: $error. Erneut starten.";emit() } })
                } catch(error: Exception) {
                    if(generation==token) { clearSession();message="Kamerafehler: $error. Erneut starten.";emit() }
                }
            }
            override fun onSurfaceTextureSizeChanged(surface: android.graphics.SurfaceTexture,width: Int,height: Int) { transformPersistentPreview() }
            override fun onSurfaceTextureUpdated(surface: android.graphics.SurfaceTexture) {}
            override fun onSurfaceTextureDestroyed(surface: android.graphics.SurfaceTexture): Boolean { operation.cancel();return true }
        }
        view.addView(texture,android.widget.FrameLayout.LayoutParams(android.view.ViewGroup.LayoutParams.MATCH_PARENT,android.view.ViewGroup.LayoutParams.MATCH_PARENT))
        emit()
    }

    private fun transformPersistentPreview() {
        val texture=persistentTexture ?: return;val size=persistentPreviewSize ?: return;val route=activeRoute ?: return
        if(texture.width==0 || texture.height==0) return
        val manager=appContext.getSystemService(android.hardware.camera2.CameraManager::class.java)
        val sensorOrientation=manager.getCameraCharacteristics(route.physicalId ?: route.logicalId)
            .get(android.hardware.camera2.CameraCharacteristics.SENSOR_ORIENTATION) ?: 0
        // TextureView has already applied sensor orientation. Rotate only for the display.
        val geometry=TexturePreviewGeometry.fit(texture.width,texture.height,size.width,size.height,sensorOrientation,rotation)
        val matrix=android.graphics.Matrix()
        matrix.setScale(geometry.scaleX,geometry.scaleY,texture.width/2f,texture.height/2f)
        matrix.postRotate(geometry.rotationDegrees,texture.width/2f,texture.height/2f)
        texture.setTransform(matrix)
    }

    private fun updatePersistentControls(nextZoom: Float, nextEv: Float, rapidEv: Boolean = false) {
        val operation=persistentPhoto ?: return;val route=activeRoute ?: return;val token=generation
        val requestId=++exposureOperation
        val previousEv=exposureEv
        persistentEvPending=rapidEv;controlPending=true
        if(rapidEv) { exposureEv=nextEv;message="EV angefordert" }
        emit()
        operation.updatePersistent(nextZoom,nextEv,checkNotNull(catalog).jpegOrientation(route,rotation)) { error ->
            if(generation==token && exposureOperation==requestId) {
                controlPending=false;persistentEvPending=false
                if(error==null) { zoomRatio=nextZoom;exposureEv=nextEv;message=if(rapidEv) "EV-Anforderung übernommen" else "Bereit" }
                else { exposureEv=previousEv;message="Einstellung fehlgeschlagen: $error" }
                emit()
            }
        }
    }

    private fun capturePersistentPhoto(completed: ((android.net.Uri?,String?)->Unit)?) {
        if(!isReady()) { completed?.invoke(null,"Kamera nicht bereit");return }
        val free=android.os.StatFs(android.os.Environment.getExternalStorageDirectory().path).availableBytes
        if(free<(if(usesRaw()) 160L else 80L)*1024*1024) { message="Zu wenig Speicher für Foto";emit();completed?.invoke(null,message);return }
        val token=generation;val snapshot=exifSnapshot();val requireRaw=usesRaw();val operation=checkNotNull(persistentPhoto)
        busy=true;message="Foto wird aufgenommen";emit()
        operation.takePersistent(snapshot,checkNotNull(catalog).jpegOrientation(checkNotNull(activeRoute),rotation)) { outcome ->
            val metadata=JSONObject(snapshot).put("sensorCapture",JSONObject(outcome.evidence)).toString()
            exifWriter.write(outcome.jpeg,metadata) { exifError -> mainExecutor.execute {
                log(JSONObject(outcome.evidence).put("kind","persistentPhotoResult").put("error",outcome.error ?: exifError ?: JSONObject.NULL)
                    .put("warning",outcome.warning ?: JSONObject.NULL))
                val failure=outcome.error ?: when { outcome.jpeg==null -> "JPEG fehlt";requireRaw && outcome.dng==null -> "DNG fehlt";else -> exifError }
                completed?.invoke(outcome.jpeg,failure)
                if(generation==token) {
                    busy=false;lastRawOutcome=outcome
                    message=if(failure==null) "${if(requireRaw) "RAW + JPEG" else "JPEG"} gespeichert: Pictures/SeaFrogs" +
                        (outcome.warning?.let { "\n$it" } ?: "") else "Aufnahmefehler: $failure"
                    emit()
                }
            } }
        }
    }

    private fun bind(
        availableProvider: ProcessCameraProvider,
        lifecycleOwner: LifecycleOwner,
        view: PreviewView,
        route: CameraLensRoute
    ) {
        val previewResolution = ResolutionSelector.Builder()
            .setAspectRatioStrategy(if (videoMode) AspectRatioStrategy.RATIO_16_9_FALLBACK_AUTO_STRATEGY else AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY)
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
        manualWhiteBalance=null; manualWbStatus=""
        if(whiteBalanceMode in 100..103) {
            val c=appContext.getSystemService(android.hardware.camera2.CameraManager::class.java).getCameraCharacteristics(route.physicalId ?: route.logicalId)
            val prepared=runCatching { ManualWhiteBalance.build(appContext,c,whiteBalanceMode,wbStrength) }
            manualWhiteBalance=prepared.getOrNull()
            manualWbStatus=if(prepared.isSuccess) "WB NICHT BESTÄTIGT" else " · Profil nicht verfügbar: ${prepared.exceptionOrNull()?.message}"
        }
        val wbAvailable = appContext.getSystemService(android.hardware.camera2.CameraManager::class.java)
            .getCameraCharacteristics(route.physicalId ?: route.logicalId)[android.hardware.camera2.CameraCharacteristics.CONTROL_AWB_AVAILABLE_MODES] ?: intArrayOf(CaptureRequest.CONTROL_AWB_MODE_AUTO)
        appliedWhiteBalanceMode = if(manualWhiteBalance!=null) CaptureRequest.CONTROL_AWB_MODE_OFF else if(qualityMode == ExtensionMode.NONE && whiteBalanceMode in wbAvailable) whiteBalanceMode else CaptureRequest.CONTROL_AWB_MODE_AUTO
        if(qualityMode == ExtensionMode.NONE) {
            Camera2Interop.Extender(previewBuilder).setCaptureRequestOption(CaptureRequest.CONTROL_AWB_MODE, appliedWhiteBalanceMode)
            Camera2Interop.Extender(captureBuilder).setCaptureRequestOption(CaptureRequest.CONTROL_AWB_MODE, appliedWhiteBalanceMode)
        }
        manualWhiteBalance?.let { wb ->
            Camera2Interop.Extender(previewBuilder).setCaptureRequestOption(CaptureRequest.COLOR_CORRECTION_MODE,CaptureRequest.COLOR_CORRECTION_MODE_TRANSFORM_MATRIX)
                .setCaptureRequestOption(CaptureRequest.COLOR_CORRECTION_GAINS,wb.gains).setCaptureRequestOption(CaptureRequest.COLOR_CORRECTION_TRANSFORM,wb.transform)
            Camera2Interop.Extender(captureBuilder).setCaptureRequestOption(CaptureRequest.COLOR_CORRECTION_MODE,CaptureRequest.COLOR_CORRECTION_MODE_TRANSFORM_MATRIX)
                .setCaptureRequestOption(CaptureRequest.COLOR_CORRECTION_GAINS,wb.gains).setCaptureRequestOption(CaptureRequest.COLOR_CORRECTION_TRANSFORM,wb.transform)
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
                    .put("aeFpsRange", result.get(CaptureResult.CONTROL_AE_TARGET_FPS_RANGE)?.toString() ?: JSONObject.NULL)
                    .put("frameDurationNs", result.get(CaptureResult.SENSOR_FRAME_DURATION) ?: JSONObject.NULL)
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
                    manualWhiteBalance?.let { manualWbStatus=it.verification(focusResult) }
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
        if (videoMode) {
            val info = availableProvider.getCameraInfo(route.selector())
            check(Quality.UHD in QualitySelector.getSupportedQualities(info)) { "4K nicht unterstützt" }
            val manager = appContext.getSystemService(android.hardware.camera2.CameraManager::class.java)
            val sensor = manager.getCameraCharacteristics(route.physicalId ?: route.logicalId)
            check(sensor[android.hardware.camera2.CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES]
                ?.any { it.contains(videoFps) } == true) { "$videoFps FPS nicht unterstützt" }
            val recorder = Recorder.Builder().setQualitySelector(QualitySelector.from(Quality.UHD)).build()
            val videoBuilder = VideoCapture.Builder(recorder).setTargetRotation(rotation)
                .setTargetFrameRate(android.util.Range(videoFps, videoFps))
            Camera2Interop.Extender(videoBuilder).setCaptureRequestOption(CaptureRequest.CONTROL_AWB_MODE, appliedWhiteBalanceMode)
            manualWhiteBalance?.let { wb ->
                Camera2Interop.Extender(videoBuilder).setCaptureRequestOption(CaptureRequest.COLOR_CORRECTION_MODE,CaptureRequest.COLOR_CORRECTION_MODE_TRANSFORM_MATRIX)
                    .setCaptureRequestOption(CaptureRequest.COLOR_CORRECTION_GAINS,wb.gains).setCaptureRequestOption(CaptureRequest.COLOR_CORRECTION_TRANSFORM,wb.transform)
            }
            if (Build.VERSION.SDK_INT >= 28) route.physicalId?.let {
                Camera2Interop.Extender(videoBuilder).setPhysicalCameraId(it)
            }
            videoCapture = videoBuilder.build()
            camera = availableProvider.bindToLifecycle(lifecycleOwner, route.selector(), preview!!, videoCapture!!)
        } else {
            imageCapture = captureBuilder.build()
            camera = availableProvider.bindToLifecycle(lifecycleOwner, selector, preview!!, imageCapture!!)
        }
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
        if (!isReady() || videoMode) return
        val route = activeRoute ?: return
        val nativeMode = 100
        val supported = mutableListOf(ExtensionMode.NONE)
        // Keep the experimental kernel available only to explicit diagnostic code.
        if (!usesRaw() && !effectiveLimits().enabled && route.physicalId == null)
            supported.addAll(qualityModes.drop(1).filter {
                runCatching { extensions?.isExtensionAvailable(route.selector(), it) == true }.getOrDefault(false)
            })
        val current = if (usesNativeFusion()) nativeMode else qualityMode
        val next = supported[(supported.indexOf(current) + 1) % supported.size]
        nativeEnabled = next == nativeMode
        formatPreferences.edit().putBoolean("nativeFusion", nativeEnabled).apply()
        qualityMode = if (nativeEnabled) ExtensionMode.NONE else next
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
        val mode = if (step.fusion) ExtensionMode.NONE else qualityModes.firstOrNull { qualityName(it) == step.quality } ?: return false
        testNativeFusionOverride = step.fusion
        if (!step.fusion && step.quality !in autoTestQualities(step.lens)) return false
        if (step.fusion && runCatching { catalog?.rawSize(checkNotNull(catalog?.routeFor(step.lens))) }.getOrNull() == null) return false
        if (step.raw && runCatching { catalog?.rawSize(checkNotNull(catalog?.routeFor(step.lens))) }.getOrNull() == null) return false
        val route = checkNotNull(catalog?.routeFor(step.lens))
        if (runCatching { catalog?.supportsProcessing(route, step.processing) }.getOrNull() != true) return false
        if (step.processing != ProcessingVariant.NONE && step.processing != ProcessingVariant.DEFAULT &&
            comparisonReferences[step.lens] == null) return false
        if (step.processing == ProcessingVariant.DEFAULT) comparisonReferences.remove(step.lens)
        testProcessing = step.processing
        testFrameCount = step.frameCount
        testRawOverride = step.raw
        testNativeCapture = step.nativeCapture
        testLimitsOverride = ExposureLimits(step.isoCap, step.longestTimeNs, step.boostCap)
        testCase = step.id
        zoomRatio = step.zoom
        exposureEv = step.ev
        qualityMode = mode
        cyclePosition = step.lens
        open(step.lens)
        return true
    }

    fun autoTestMatches(step: AutoTestStep) = isReady() && activeLens == step.lens &&
        kotlin.math.abs(zoomRatio - step.zoom) < 0.01f && selectedQualityName() == step.quality

    fun readyForCommand() = isReady()
    fun isRecording() = recording != null

    fun autoTestFocusStatus(): String = when {
        qualityMode != ExtensionMode.NONE -> "UNVERIFIED_EXTENSION"
        activeRoute?.supportsPhotoAutofocus != true -> "NOT_REQUIRED_FIXED_FOCUS"
        autofocusStability.stable(android.os.SystemClock.uptimeMillis()) -> "STABLE_PREVIEW_AF"
        else -> "WAITING_FOR_PREVIEW_AF"
    }

    fun cycleZoom() {
        if (!isReady()) return
        if(persistentPhoto!=null) {
            val next=CameraControlCycles.nextZoom(zoomRatio,activeLens==PhotoLens.MACRO,1f,persistentZoomMaximum,activeLens==PhotoLens.ULTRAWIDE)
            if(next!=null && next!=zoomRatio) updatePersistentControls(next,exposureEv)
            return
        }
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

    private fun canAdjustExposure() = isReady() ||
        (persistentEvPending && cameraOpen && streaming && !busy && !focusPending)

    fun cycleExposure() {
        if (!canAdjustExposure()) return
        if(persistentPhoto!=null) {
            val c=appContext.getSystemService(android.hardware.camera2.CameraManager::class.java).getCameraCharacteristics(checkNotNull(activeRoute).logicalId)
            val step=c[android.hardware.camera2.CameraCharacteristics.CONTROL_AE_COMPENSATION_STEP]?.toFloat() ?: 0f
            val range=c[android.hardware.camera2.CameraCharacteristics.CONTROL_AE_COMPENSATION_RANGE]
            if(step>0 && range!=null && persistentEvSupported) updatePersistentControls(zoomRatio,CameraControlCycles.nextExposure(kotlin.math.round(exposureEv/step).toInt(),step,range.lower,range.upper)*step,rapidEv=true)
            return
        }
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
            val previous = exposureEv
            val next = CameraControlCycles.nextExposure(kotlin.math.round(exposureEv/step).toInt(),
                step, range.lower, range.upper)
            val requestId = ++exposureOperation
            exposureEv = next * step
            message = "EV angefordert"
            emit()
            val operation = boundCamera.cameraControl.setExposureCompensationIndex(next)
            operation.addListener({
                if (generation == token && exposureOperation == requestId) {
                    message = try {
                        exposureEv = operation.get() * step
                        "EV bestätigt"
                    } catch (error: Exception) {
                        exposureEv = previous
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
        else -> "${number(zoomRatio)}× Cropfaktor"
    } else "${number(zoomRatio)}×"

    private fun number(value: Float): String = String.format(Locale.GERMANY, "%.2f", value)
        .trimEnd('0').trimEnd(',')

    fun setRotation(targetRotation: Int) {
        if (recording != null) return
        rotation = targetRotation
        transformPersistentPreview()
        videoCapture?.targetRotation = targetRotation
        imageCapture?.targetRotation = targetRotation
    }

    fun toggleCaptureMode() {
        if (!isReady() || recording != null) return
        videoMode = !videoMode
        qualityMode = ExtensionMode.NONE
        open(activeLens)
    }

    fun savedVideoFps() = videoFps
    fun setVideoFps(value: Int) {
        if (!isReady() || recording != null || value !in listOf(30,60)) return
        videoFps = value
        appContext.getSharedPreferences("video", Context.MODE_PRIVATE).edit().putInt("fps",value).apply()
        if (videoMode) open(activeLens) else emit()
    }

    fun triggerCapture() {
        if (!videoMode) { capturePhoto(); return }
        recording?.let {
            if (busy) return
            busy = true; message = "Video wird gespeichert …"; emit()
            try { it.stop() } catch (error: Exception) {
                busy = false; message = "Videostopp fehlgeschlagen: ${error.message}"; emit()
            }
            return
        }
        if (!isReady()) return
        val capture = videoCapture ?: return
        val free = android.os.StatFs(android.os.Environment.getExternalStorageDirectory().path).availableBytes
        if (free < 256L * 1024 * 1024) { message = "Zu wenig Speicher für Video"; emit(); return }
        val token = generation
        val operationId = ++videoOperation
        val requestedFps = videoFps
        val requestedLens = activeLens.name
        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, "SeaFrogs_${System.currentTimeMillis()}.mp4")
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            if (Build.VERSION.SDK_INT >= 29) put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/SeaFrogs")
        }
        val output = MediaStoreOutputOptions.Builder(appContext.contentResolver,
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI).setContentValues(values)
            .setFileSizeLimit(free - 128L * 1024 * 1024).build()
        busy = true; message = "Video startet …"; videoSeconds = 0; emit()
        try {
            // Underwater milestone: silent MP4, no microphone permission needed.
            recording = capture.output.prepareRecording(appContext, output).start(mainExecutor) { event ->
                if (event is VideoRecordEvent.Finalize) {
                    log(JSONObject().put("kind", "videoFinalized").put("uri", event.outputResults.outputUri.toString())
                        .put("error", event.error).put("durationNs", event.recordingStats.recordedDurationNanos)
                        .put("bytes", event.recordingStats.numBytesRecorded).put("requestedFps", requestedFps).put("lens",requestedLens))
                    if (!event.hasError()) inspectVideo(event.outputResults.outputUri, requestedFps, requestedLens)
                }
                if (generation != token || videoOperation != operationId) return@start
                when (event) {
                    is VideoRecordEvent.Start -> { busy = false; message = "REC 0 s" }
                    is VideoRecordEvent.Status -> {
                        videoSeconds = event.recordingStats.recordedDurationNanos / 1_000_000_000L
                        message = "REC $videoSeconds s"
                    }
                    is VideoRecordEvent.Finalize -> {
                        recording = null; busy = false
                        videoSeconds = event.recordingStats.recordedDurationNanos / 1_000_000_000L
                        message = if (event.hasError()) "Videofehler ${event.error}: ${event.cause?.message ?: "Aufnahme beendet"}"
                            else "Video gespeichert (${videoSeconds} s)"
                    }
                }
                emit()
            }
        } catch (error: Exception) {
            recording = null; busy = false; message = "Videostart fehlgeschlagen: ${error.message}"; emit()
        }
    }

    private fun inspectVideo(uri: android.net.Uri, fps: Int, lens: String) {
        Thread({
            val metadata = android.media.MediaMetadataRetriever()
            val value = JSONObject().put("kind","videoFileMetadata").put("uri",uri.toString())
                .put("requestedFps",fps).put("lens",lens)
            try {
                metadata.setDataSource(appContext,uri)
                value.put("width",metadata.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH))
                    .put("height",metadata.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT))
                    .put("durationMs",metadata.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION))
                    .put("captureFrameRate",metadata.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_CAPTURE_FRAMERATE)
                        ?: JSONObject.NULL)
                    .put("rotation",metadata.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION))
            } catch (error: Exception) { value.put("error",error.toString()) }
            finally { runCatching { metadata.release() } }
            mainExecutor.execute { log(value) }
        }, "SeaFrogs-video-metadata").start()
    }

    fun capturePhoto(completed: ((android.net.Uri?, String?) -> Unit)? = null) {
        if (videoMode) { completed?.invoke(null, "Videomodus aktiv"); return }
        lastRawOutcome = null
        if(persistentPhoto!=null) { capturePersistentPhoto(completed);return }
        if (usesNativeFusion() || usesRaw() || effectiveLimits().enabled || testNativeCapture) { captureSensorPhoto(completed); return }
        val free = android.os.StatFs(android.os.Environment.getExternalStorageDirectory().path).availableBytes
        if(free < 80L*1024*1024) { message="Zu wenig Speicher für Foto"; emit(); completed?.invoke(null,message); return }
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
            val output = ImageCapture.OutputFileOptions.Builder(appContext.contentResolver,
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values).build()
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

    private fun captureSensorPhoto(completed: ((android.net.Uri?, String?) -> Unit)?) {
        if (!isReady()) { completed?.invoke(null, "Kamera nicht bereit"); return }
        val routes = catalog ?: return
        val route = activeRoute ?: return
        val includeRaw = usesRaw()
        val freeBytes = android.os.StatFs(android.os.Environment.getExternalStorageDirectory().path).availableBytes
        if (freeBytes < (if (includeRaw) 160L else 80L) * 1024 * 1024) {
            message = "SPEICHER KNAPP: Aufnahme nicht gestartet"; emit()
            completed?.invoke(null, message); return
        }
        val fuse = usesNativeFusion()
        val limits = effectiveLimits()
        val rawSize = if (includeRaw || fuse) runCatching { routes.rawSize(route) }.getOrNull() else null
        val jpegSize = imageCapture?.resolutionInfo?.resolution
        if (((includeRaw || fuse) && rawSize == null) || jpegSize == null) {
            message = "Gewünschtes Fotoformat auf dieser Kameraroute nicht verfügbar"; emit()
            completed?.invoke(null, message); return
        }
        val metadata = exifSnapshot()
        val lens = activeLens
        val processing = if (fuse) ProcessingVariant.DEFAULT else testProcessing
        val frameCount = if (fuse) 5 else testFrameCount
        val reference = if (processing == ProcessingVariant.DEFAULT) null else comparisonReferences[lens]
        val jpegOrientation = routes.jpegOrientation(route, rotation)
        clearSession()
        val token = generation
        busy = true
        message = "${if (fuse) "MEHRBILD: 5 RAW-Aufnahmen + Verarbeitung" else if (includeRaw) "RAW + JPEG" else "JPEG"}: Bitte ruhig halten … Vorschau pausiert"
        emit()
        val operation = RawJpegCapture(appContext)
        rawCapture = operation
        try {
            operation.start(route, jpegSize, rawSize, zoomRatio, exposureEv, jpegOrientation, metadata, limits, processing, reference, frameCount, fuse, includeRaw, whiteBalanceMode = appliedWhiteBalanceMode, manualWb = manualWhiteBalance, onProgress = { stage ->
                if (generation == token && busy) { message = stage; emit() }
            }) { outcome ->
                val actualMetadata = JSONObject(metadata).put(if (includeRaw) "rawCapture" else "sensorCapture", JSONObject(outcome.evidence)).toString()
                exifWriter.write(outcome.jpeg, actualMetadata) { exifError ->
                    mainExecutor.execute {
                        log(JSONObject(outcome.evidence).put("kind", if (includeRaw) "rawJpegCaptureResult" else "sensorPhotoCaptureResult")
                            .put("jpegUri", outcome.jpeg?.toString() ?: JSONObject.NULL)
                            .put("dngUri", outcome.dng?.toString() ?: JSONObject.NULL)
                            .put("error", outcome.error ?: JSONObject.NULL))
                        if (generation != token) return@execute
                        rawCapture = null
                        lastRawOutcome = outcome
                        if (processing == ProcessingVariant.DEFAULT && outcome.error == null)
                            outcome.comparisonSettings?.let { comparisonReferences[lens] = it }
                        busy = false
                        val failure = outcome.error ?: when {
                            outcome.jpeg == null -> "JPEG-Datei fehlt"
                            includeRaw && outcome.dng == null -> "DNG-Datei fehlt"
                            else -> exifError
                        }
                        val result = JSONObject(outcome.evidence)
                        val difference = result.optDouble("brightnessDifferenceEvSensor", 0.0)
                        val applied = if (result.has("iso") && result.has("exposureTimeNs"))
                            "\nSensor-ISO ${result.optString("iso")} · Digital ${number((result.optInt("postRawBoostActual", 100) / 100f))}× · JPEG-ISO ${result.optString("jpegExifIso", "n/v")} | ${number((result.optLong("exposureTimeNs") / 1_000_000.0).toFloat())} ms"
                            else ""
                        val warning = if (result.optBoolean("underexposedByLimits"))
                            "\nDUNKLER DURCH LIMIT: ${number(difference.toFloat())} EV" else ""
                        open(lens, notice = if (failure == null && outcome.jpeg != null && (!includeRaw || outcome.dng != null))
                            "${if (fuse) "MEHRBILD + STANDARD" else if (includeRaw) "RAW + JPEG" else "JPEG"} gespeichert: Pictures/SeaFrogs$applied$warning"
                            else if (fuse && outcome.jpeg != null) "STANDARD-JPEG gespeichert. MEHRBILD fehlgeschlagen: $failure"
                            else "Aufnahmefehler: $failure")
                        completed?.invoke(outcome.jpeg, failure)
                    }
                }
            }
        } catch (error: Exception) {
            rawCapture = null
            operation.cancel()
            if (generation == token) {
                busy = false
                open(lens, notice = "Aufnahmestart fehlgeschlagen: ${error.message}")
            }
            completed?.invoke(null, error.message ?: "Aufnahmestart fehlgeschlagen")
        }
    }

    private fun exifSnapshot(): String {
        val exposure = camera?.cameraInfo?.exposureState
        val zoom = camera?.cameraInfo?.zoomState?.value
        val size = if (videoMode) videoCapture?.resolutionInfo?.resolution else persistentSize ?: imageCapture?.resolutionInfo?.resolution
        return JSONObject()
            .put("app", "SeaFrogs Camera")
            .put("version", "0.8.7-setup")
            .put("mode", "PHOTO").put("whiteBalanceRequested",whiteBalanceMode).put("whiteBalanceApplied",appliedWhiteBalanceMode).put("manualWhiteBalance",manualWhiteBalance?.json() ?: JSONObject.NULL).put("wbPreviewVerification",manualWbStatus)
            .put("testCase", testCase)
            .put("qualityMode", selectedQualityName())
            .put("processingVariantRequested", testProcessing.name)
            .put("photoFormat", if (usesRaw()) "RAW+JPEG" else "JPEG")
            .put("isoCapRequested", effectiveLimits().isoCap).put("digitalBoostCapRequested", effectiveLimits().boostCap)
            .put("longestTimeNsRequested", if (effectiveLimits().enabled) effectiveLimits().longestTimeNs else JSONObject.NULL)
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
        rawCapture?.cancel()
        rawCapture = null
        clearSession()
        owner = null
        previewView = null
    }

    private fun clearSession() {
        generation++
        videoOperation++
        owner?.let { lifecycleOwner ->
            camera?.cameraInfo?.cameraState?.removeObservers(lifecycleOwner)
            previewView?.previewStreamState?.removeObservers(lifecycleOwner)
        }
        releaseUseCases()
    }

    private fun releaseUseCases() {
        persistentPhoto?.cancel();persistentPhoto=null
        persistentTexture?.let { (it.parent as? android.view.ViewGroup)?.removeView(it) };persistentTexture=null
        persistentSurface?.release();persistentSurface=null
        persistentSize=null;persistentPreviewSize=null;persistentEvSupported=false
        manualWhiteBalance=null;manualWbStatus="";appliedWhiteBalanceMode=CaptureRequest.CONTROL_AWB_MODE_AUTO
        exposureOperation++
        recording?.stop()
        recording = null
        val useCases = listOfNotNull(preview, imageCapture, videoCapture)
        if (useCases.isNotEmpty()) provider?.unbind(*useCases.toTypedArray())
        preview = null
        imageCapture = null
        videoCapture = null
        camera = null
        activeRoute = null
        macroEntryFocusResult = "not_requested"
        cameraOpen = false
        streaming = false
        busy = false
        zoomReady = false
        exposureReady = false
        controlPending = false
        persistentEvPending = false
        focusPending = false
        focusTriggered = false
        autofocusStability.reset()
    }

    private fun emit() {
        val size = if (videoMode) videoCapture?.resolutionInfo?.resolution else persistentSize ?: imageCapture?.resolutionInfo?.resolution
        publish(PhotoCameraState(
            lens = activeLens,
            zoomLabel = zoomLabel(),
            exposureLabel = (if (exposureEv > 0f) "+" else "") + number(exposureEv),
            exposureSupported = persistentEvSupported || camera?.cameraInfo?.exposureState?.isExposureCompensationSupported == true,
            exposureAdjustable = canAdjustExposure(),
            ready = isReady(),
            capturing = busy,
            message = message,
            resolution = size?.let { "${it.width} × ${it.height}" } ?: "",
            quality = selectedQualityName(),
            diagnostics = extensionSummary + "\n" + resultSummary(),
            photoFormat = if (videoMode) "MP4 · ohne Ton" else if (usesRaw()) "RAW+JPEG" else "JPEG",
            videoMode = videoMode, recording = recording != null, videoFps = videoFps,
            exposureLimits = effectiveLimits()
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
            "Fokus ${result.optString("focusDistanceDiopters", "?")} dpt | Vorschau Sensor-ISO ${result.optString("iso", "?")}"
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
