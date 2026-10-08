package de.jce.seafrogs

import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Context
import android.graphics.ImageFormat
import android.hardware.camera2.*
import android.hardware.camera2.params.OutputConfiguration
import android.media.Image
import android.media.ImageReader
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import android.provider.MediaStore
import android.util.Size
import androidx.core.content.ContextCompat
import org.json.JSONObject
import java.util.UUID

data class RawSeriesFrame(val jpeg: Uri, val dng: Uri?, val evidence: String)

data class RawPairOutcome(val jpeg: Uri?, val dng: Uri?, val evidence: String, val error: String?,
    val comparisonSettings: FrozenCaptureSettings? = null,
    val frames: List<RawSeriesFrame> = emptyList())

/** One Camera2 exposure with JPEG and optional RAW targets. CameraX must be unbound first.
 * All camera/image state lives on one worker. Match sensor timestamps before
 * writing DNG; pinned outputs require the matching physical CaptureResult.
 */
class RawJpegCapture(context: Context) {
    private val app = context.applicationContext
    private val main = ContextCompat.getMainExecutor(app)
    private val thread = HandlerThread("SeaFrogs-RAW").apply { start() }
    private val worker = Handler(thread.looper)
    private var device: CameraDevice? = null
    private var session: CameraCaptureSession? = null
    private var jpegReader: ImageReader? = null
    private var rawReader: ImageReader? = null
    private val jpegFrames = linkedMapOf<Long, ByteArray>()
    private var rawImage: Image? = null
    private var stillResult: TotalCaptureResult? = null
    private var characteristics: CameraCharacteristics? = null
    private var logicalCharacteristics: CameraCharacteristics? = null
    private lateinit var route: CameraLensRoute
    private var zoom = 1f
    private var ev = 0f
    private var orientation = 0
    private var limits = ExposureLimits()
    private var manualExposure: LimitedExposure? = null
    private var meterIso = 0
    private var meterTimeNs = 0L
    private var requestedBoost: Int? = null
    private var description = ""
    private var processing = ProcessingVariant.NONE
    private var suppliedReference: FrozenCaptureSettings? = null
    private var requestedReference: FrozenCaptureSettings? = null
    private var actualReference: FrozenCaptureSettings? = null
    private var manualSettling = false
    private var manualStartedAt = 0L
    private var shotRequested = false
    private var finished = false
    private var callback: ((RawPairOutcome) -> Unit)? = null
    private var openedAt = 0L
    private var firstFrameAt = 0L
    private val focus = AutofocusStability()
    private var savedJpeg: Uri? = null
    private var savedDng: Uri? = null
    private var progress: ((String) -> Unit)? = null
    private var nativeFusion = false
    private var retainDng = true
    private var rawStack: java.nio.ByteBuffer? = null
    private var fusionSensor: CaptureResult? = null
    private var fallbackBytes: ByteArray? = null
    private var fusionSize: Size? = null
    private var fusionRawCrop: android.graphics.Rect? = null
    private var targetJpegSize: Size? = null
    private val fusionTimestamps = mutableListOf<Long>()
    private val cancelRequested = java.util.concurrent.atomic.AtomicBoolean(false)
    private val evidence = JSONObject()
    private var finishedOutcome: RawPairOutcome? = null
    private var delivered = false
    private var seriesCount = 1
    private val seriesFrames = mutableListOf<RawSeriesFrame>()
    private var opening = false

    fun start(selected: CameraLensRoute, jpegSize: Size, rawSize: Size?, zoomRatio: Float,
              exposureEv: Float, jpegOrientation: Int, metadata: String,
              exposureLimits: ExposureLimits = ExposureLimits(),
              processingVariant: ProcessingVariant = ProcessingVariant.NONE,
              reference: FrozenCaptureSettings? = null,
              frameCount: Int = 1,
              fuse: Boolean = false,
              keepDng: Boolean = true,
              onProgress: ((String) -> Unit)? = null,
              complete: (RawPairOutcome) -> Unit) {
        worker.post {
            if (finished) return@post
            callback = complete; progress = onProgress
            reportProgress("Fokus und Belichtung vorbereiten …")
            require(frameCount in 1..5) { "RAW-Serie muss 1 bis 5 Bilder enthalten" }
            require(frameCount == 1 || rawSize != null && processingVariant == ProcessingVariant.DEFAULT) { "RAW-Serie benötigt RAW und fixierte Szenenwerte" }
            seriesCount = frameCount
            nativeFusion = fuse; retainDng = keepDng; targetJpegSize = jpegSize
            evidence.put("nativeFusionRequested", fuse).put("nativeFusionApplied", false)
            if (fuse) check(frameCount == 5 && rawSize != null && processingVariant == ProcessingVariant.DEFAULT)

            evidence.put("seriesCountRequested", frameCount).put("seriesFrameIndex", 0)
            route = selected; zoom = zoomRatio; ev = exposureEv
            orientation = jpegOrientation; description = metadata; limits = exposureLimits
            processing = processingVariant; suppliedReference = reference
            evidence.put("processingVariantRequested", processing.name)
                .put("comparisonReferenceReused", reference != null)
            evidence.put("backend", if (rawSize == null) "Camera2Jpeg" else "Camera2RawJpeg")
                .put("isoCapRequested", limits.isoCap).put("digitalBoostCapRequested", limits.boostCap)
                .put("longestTimeNsRequested", if (limits.isoCap > 0) limits.longestTimeNs else JSONObject.NULL)
            evidence.put("logicalId", route.logicalId).put("physicalId", route.physicalId ?: JSONObject.NULL)
                .put("jpegRequestedSize", jpegSize.toString()).put("rawRequestedSize", rawSize?.toString() ?: JSONObject.NULL)
                .put("zoomRequested", zoom.toDouble()).put("evRequested", ev.toDouble())
            try {
                val manager = app.getSystemService(CameraManager::class.java)
                characteristics = manager.getCameraCharacteristics(route.physicalId ?: route.logicalId)
                logicalCharacteristics = manager.getCameraCharacteristics(route.logicalId)
                jpegReader = ImageReader.newInstance(jpegSize.width, jpegSize.height, ImageFormat.JPEG, 3)
                rawReader = rawSize?.let { ImageReader.newInstance(it.width, it.height, ImageFormat.RAW_SENSOR, 2) }
                if (limits.enabled) {
                    val c = checkNotNull(characteristics)
                    check(c[CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES]?.contains(
                        CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_MANUAL_SENSOR) == true) { "Manuelle Belichtung auf dieser Route nicht verfügbar" }
                }
                jpegReader!!.setOnImageAvailableListener({ reader ->
                    val image = reader.acquireNextImage() ?: return@setOnImageAvailableListener
                    try {
                        if (!finished && shotRequested) {
                            val buffer = image.planes[0].buffer
                            jpegFrames[image.timestamp] = ByteArray(buffer.remaining()).also { buffer.get(it) }
                            while (jpegFrames.size > 4) jpegFrames.remove(jpegFrames.keys.first())
                        }
                    } finally { image.close() }
                    trySave()
                }, worker)
                rawReader?.setOnImageAvailableListener({ reader ->
                    val image = reader.acquireNextImage() ?: return@setOnImageAvailableListener
                    if (finished || rawImage != null) image.close()
                    else { rawImage = image; trySave() }
                }, worker)
                openedAt = SystemClock.uptimeMillis()
                worker.postDelayed({ finish("RAW-Aufnahme/Serie nach 60 Sekunden nicht beendet") }, 60000)
                open(manager)
            } catch (error: Exception) { finish(error.toString()) }
        }
    }

    @SuppressLint("MissingPermission") // Caller has already checked CAMERA; revocation is caught.
    private fun open(manager: CameraManager) {
        opening = true
        try { manager.openCamera(route.logicalId, object : CameraDevice.StateCallback() {
            override fun onOpened(camera: CameraDevice) {
                opening = false
                if (finished) { camera.close(); return }
                device = camera
                try {
                    val surfaces = listOfNotNull(jpegReader!!.surface, rawReader?.surface)
                    val state = object : CameraCaptureSession.StateCallback() {
                        override fun onConfigured(value: CameraCaptureSession) {
                            if (finished) { value.close(); return }
                            session = value
                            try {
                                val request = request(CameraDevice.TEMPLATE_PREVIEW)
                                request.addTarget(jpegReader!!.surface)
                                value.setRepeatingRequest(request.build(), captureCallback, worker)
                            } catch (error: Exception) { finish(error.toString()) }
                        }
                        override fun onConfigureFailed(value: CameraCaptureSession) {
                            value.close(); finish("RAW+JPEG-Streamkombination nicht unterstützt")
                        }
                    }
                    if (Build.VERSION.SDK_INT >= 28 && route.physicalId != null) {
                        val outputs = surfaces.map { OutputConfiguration(it).apply { setPhysicalCameraId(route.physicalId) } }
                        camera.createCaptureSessionByOutputConfigurations(outputs, state, worker)
                    } else camera.createCaptureSession(surfaces, state, worker)
                } catch (error: Exception) { finish(error.toString()) }
            }
            override fun onDisconnected(camera: CameraDevice) {
                opening = false; camera.close(); finish("RAW-Kamera getrennt")
                if (finished) deliver()
            }
            override fun onError(camera: CameraDevice, error: Int) {
                opening = false
                camera.close()
                if (finished) { deliver(); return }
                if (device == null && error == ERROR_CAMERA_IN_USE && SystemClock.uptimeMillis() - openedAt < 5000)
                    worker.postDelayed({ if (!finished) try { open(manager) } catch (e: Exception) { finish(e.toString()) } }, 250)
                else finish("RAW-Kamerafehler $error")
            }
            override fun onClosed(camera: CameraDevice) { if (finished) deliver() }
        }, worker) } catch (error: Exception) { opening = false; throw error }
    }

    private fun request(template: Int): CaptureRequest.Builder {
        val camera = checkNotNull(device)
        // setPhysicalCameraKey requires an explicitly initialized physical-ID
        // builder. Pinning OutputConfiguration alone does not initialize it.
        val physical = route.physicalId?.takeIf {
            Build.VERSION.SDK_INT >= 28 && (limits.enabled && template == CameraDevice.TEMPLATE_STILL_CAPTURE ||
                processing != ProcessingVariant.NONE && requestedReference != null)
        }
        val builder = if (Build.VERSION.SDK_INT >= 28 && physical != null)
            camera.createCaptureRequest(template, setOf(physical)) else camera.createCaptureRequest(template)
        builder.set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_AUTO)
        builder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON)
        builder.set(CaptureRequest.CONTROL_AWB_MODE, CaptureRequest.CONTROL_AWB_MODE_AUTO)
        builder.set(CaptureRequest.CONTROL_AF_MODE, if (route.supportsPhotoAutofocus)
            CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE else CaptureRequest.CONTROL_AF_MODE_OFF)
        val c = checkNotNull(logicalCharacteristics)
        if (nativeFusion) setSensorKey(builder, CaptureRequest.STATISTICS_LENS_SHADING_MAP_MODE, CaptureRequest.STATISTICS_LENS_SHADING_MAP_MODE_ON)
        val step = c[CameraCharacteristics.CONTROL_AE_COMPENSATION_STEP]?.toFloat() ?: 0f
        val range = c[CameraCharacteristics.CONTROL_AE_COMPENSATION_RANGE]
        if (step > 0 && range != null) builder.set(CaptureRequest.CONTROL_AE_EXPOSURE_COMPENSATION,
            CameraControlCycles.exposureIndex(ev, step, range.lower, range.upper))
        if (Build.VERSION.SDK_INT >= 30 && c[CameraCharacteristics.CONTROL_ZOOM_RATIO_RANGE] != null) {
            builder.set(CaptureRequest.CONTROL_ZOOM_RATIO, zoom)
        } else {
            c[CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE]?.let { active ->
                val width = (active.width() / zoom).toInt(); val height = (active.height() / zoom).toInt()
                val left = active.left + (active.width() - width) / 2
                val top = active.top + (active.height() - height) / 2
                builder.set(CaptureRequest.SCALER_CROP_REGION, android.graphics.Rect(left, top, left + width, top + height))
            }
        }
        builder.set(CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE, CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE_OFF)
        if (Build.VERSION.SDK_INT >= 31 && c[CameraCharacteristics.SCALER_AVAILABLE_ROTATE_AND_CROP_MODES]?.contains(
                CaptureRequest.SCALER_ROTATE_AND_CROP_NONE) == true)
            builder.set(CaptureRequest.SCALER_ROTATE_AND_CROP, CaptureRequest.SCALER_ROTATE_AND_CROP_NONE)
        builder.set(CaptureRequest.JPEG_QUALITY, 100.toByte())
        builder.set(CaptureRequest.JPEG_ORIENTATION, orientation)
        if (Build.VERSION.SDK_INT >= 28 && physical != null) {
            // Preserve the chosen AF/WB/zoom settings before overriding just
            // exposure. Physical template defaults must not undo those values.
            c.availablePhysicalCameraRequestKeys?.forEach { key -> copyPhysicalSetting(builder, key, physical) }
        }
        if (processing != ProcessingVariant.NONE && requestedReference != null) {
            applyFrozen(builder, checkNotNull(requestedReference))
            if (template == CameraDevice.TEMPLATE_STILL_CAPTURE) applyProcessing(builder)
        }
        return builder
    }

    @androidx.annotation.RequiresApi(28)
    private fun <T> copyPhysicalSetting(builder: CaptureRequest.Builder, key: CaptureRequest.Key<T>, id: String) {
        builder.get(key)?.let { builder.setPhysicalCameraKey(key, it, id) }
    }

    private fun sensorResult(result: TotalCaptureResult): CaptureResult? =
        if (Build.VERSION.SDK_INT >= 28 && route.physicalId != null) result.physicalCameraResults[route.physicalId]
        else result

    private val captureCallback = object : CameraCaptureSession.CaptureCallback() {
        override fun onCaptureCompleted(value: CameraCaptureSession, request: CaptureRequest, result: TotalCaptureResult) {
            if (finished) return
            if (request.tag == "SENSOR_PHOTO") { stillResult = result; trySave(); return }
            if (manualSettling) {
                if (request.tag == "QUALITY_LOCK") {
                    val sensor = sensorResult(result)
                    val now = SystemClock.uptimeMillis()
                    focus.frame(sensor?.let { frozenFocusMatches(it) }, sensor?.get(CaptureResult.SENSOR_TIMESTAMP), now)
                    if (!shotRequested && focus.stable(now)) submitStill(value, sensor, this)
                    else if (!shotRequested && now - manualStartedAt > 8000) finish("Vergleichsfokus nach 8 Sekunden nicht bestätigt")
                }
                return
            }
            val now = SystemClock.uptimeMillis()
            if (firstFrameAt == 0L) firstFrameAt = now
            val sensor = sensorResult(result)
            val af = sensor?.get(CaptureResult.CONTROL_AF_STATE)
            focus.frame(af?.let { it == CaptureResult.CONTROL_AF_STATE_PASSIVE_FOCUSED ||
                it == CaptureResult.CONTROL_AF_STATE_FOCUSED_LOCKED }, sensor?.get(CaptureResult.SENSOR_TIMESTAMP), now)
            val ae = sensor?.get(CaptureResult.CONTROL_AE_STATE)
            val aeReady = ae == CaptureResult.CONTROL_AE_STATE_CONVERGED || ae == CaptureResult.CONTROL_AE_STATE_FLASH_REQUIRED
            val wbReady = processing == ProcessingVariant.NONE ||
                sensor?.get(CaptureResult.CONTROL_AWB_STATE) == CaptureResult.CONTROL_AWB_STATE_CONVERGED
            if (!shotRequested && now - firstFrameAt >= 1500 && aeReady && wbReady &&
                (!route.supportsPhotoAutofocus || focus.stable(now))) {
                evidence.put("preCaptureAfState", af ?: JSONObject.NULL).put("preCaptureAeState", ae)
                if (processing == ProcessingVariant.NONE) submitStill(value, sensor, this)
                else try {
                    requestedReference = suppliedReference ?: FrozenCaptureSettings.from(checkNotNull(sensor))
                    if (nativeFusion && limits.enabled) {
                        val measured = checkNotNull(requestedReference)
                        val c = checkNotNull(characteristics)
                        val isoRange = checkNotNull(c[CameraCharacteristics.SENSOR_INFO_SENSITIVITY_RANGE])
                        val timeRange = checkNotNull(c[CameraCharacteristics.SENSOR_INFO_EXPOSURE_TIME_RANGE])
                        val bounded = ExposureLimitCalculator.calculate(measured.iso, measured.timeNs, limits,
                            isoRange.lower, isoRange.upper, timeRange.lower, timeRange.upper)
                        requestedReference = measured.copy(iso = bounded.iso, timeNs = bounded.timeNs,
                            frameNs = maxOf(measured.frameNs, bounded.timeNs),
                            boost = selectedBoost(measured.boost))
                    }
                    evidence.put("comparisonRequestedSettings", checkNotNull(requestedReference).json())
                    focus.reset()
                    manualSettling = true
                    manualStartedAt = now
                    val locked = this@RawJpegCapture.request(CameraDevice.TEMPLATE_PREVIEW)
                    locked.setTag("QUALITY_LOCK")
                    locked.addTarget(jpegReader!!.surface)
                    value.setRepeatingRequest(locked.build(), this, worker)
                } catch (error: Exception) { finish(error.toString()) }
            } else if (!shotRequested && now - openedAt > 15000) finish("RAW: AF/AE nicht rechtzeitig bestätigt")
        }
        override fun onCaptureFailed(value: CameraCaptureSession, request: CaptureRequest, failure: CaptureFailure) {
            finish("RAW-Capture fehlgeschlagen: ${failure.reason}")
        }
    }

    private fun submitStill(value: CameraCaptureSession, sensor: CaptureResult?, callback: CameraCaptureSession.CaptureCallback) {
        try {
            if (cancelRequested.get()) { finish("Aufnahme abgebrochen; gespeicherte Fotos erhalten"); return }
            reportProgress("${if (nativeFusion) "MEHRBILD" else "Aufnahme"}: ${seriesFrames.size + 1}/$seriesCount")
            shotRequested = true
            value.stopRepeating()
            val still = request(CameraDevice.TEMPLATE_STILL_CAPTURE)
            if (limits.enabled) applyExposureLimit(still, checkNotNull(sensor))
            evidence.put("noiseReductionRequested", still.get(CaptureRequest.NOISE_REDUCTION_MODE) ?: JSONObject.NULL)
                .put("edgeRequested", still.get(CaptureRequest.EDGE_MODE) ?: JSONObject.NULL)
            still.setTag("SENSOR_PHOTO")
            still.addTarget(jpegReader!!.surface)
            rawReader?.surface?.let { still.addTarget(it) }
            value.capture(still.build(), callback, worker)
        } catch (error: Exception) { finish(error.toString()) }
    }

    private fun trySave() {
        if (finished) return
        val raw = rawImage
        if (rawReader != null && raw == null) return
        val total = stillResult ?: return
        val sensor = sensorResult(total) ?: run { finish("Physische RAW-CaptureResult-Metadaten fehlen"); return }
        val timestamp = sensor[CaptureResult.SENSOR_TIMESTAMP] ?: run { finish("RAW-Sensorzeitstempel fehlt"); return }
        if (raw != null && timestamp != raw.timestamp) { finish("RAW-Bild und Sensor-Metadaten haben unterschiedliche Zeitstempel"); return }
        val jpeg = jpegFrames[timestamp] ?: return
        try {
            val c = checkNotNull(characteristics)
            if (raw != null) {
                val sizes = c[CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP]?.getOutputSizes(ImageFormat.RAW_SENSOR)
                check(sizes?.any { it.width == raw.width && it.height == raw.height } == true) { "RAW-Größe passt nicht zum Sensor" }
            }
            evidence.put("sensorTimestampNs", timestamp).put("jpegTimestampNs", timestamp)
                .put("rawTimestampNs", raw?.timestamp ?: JSONObject.NULL).put("sameExposureVerified", true)
                .put("rawWidth", raw?.width ?: JSONObject.NULL).put("rawHeight", raw?.height ?: JSONObject.NULL)
                .put("iso", sensor[CaptureResult.SENSOR_SENSITIVITY] ?: JSONObject.NULL)
                .put("postRawBoostActual", sensor[CaptureResult.CONTROL_POST_RAW_SENSITIVITY_BOOST] ?: JSONObject.NULL)
                .put("exposureTimeNs", sensor[CaptureResult.SENSOR_EXPOSURE_TIME] ?: JSONObject.NULL)
                .put("afState", sensor[CaptureResult.CONTROL_AF_STATE] ?: JSONObject.NULL)
                .put("noiseReductionActual", sensor[CaptureResult.NOISE_REDUCTION_MODE] ?: JSONObject.NULL)
                .put("edgeActual", sensor[CaptureResult.EDGE_MODE] ?: JSONObject.NULL)
                .put("colorCorrectionModeActual", sensor[CaptureResult.COLOR_CORRECTION_MODE] ?: JSONObject.NULL)
                .put("lensStateActual", sensor[CaptureResult.LENS_STATE] ?: JSONObject.NULL)
                .put("focalLengthMm", sensor[CaptureResult.LENS_FOCAL_LENGTH]?.toDouble() ?: JSONObject.NULL)
                .put("sensorCropRegion", sensor[CaptureResult.SCALER_CROP_REGION]?.toShortString() ?: JSONObject.NULL)
                .put("logicalCropRegion", total[CaptureResult.SCALER_CROP_REGION]?.toShortString() ?: JSONObject.NULL)
                .put("rawImageCropRect", raw?.cropRect?.toShortString() ?: JSONObject.NULL)
                .put("jpegRotateAndCropActual", if (Build.VERSION.SDK_INT >= 31) total[CaptureResult.SCALER_ROTATE_AND_CROP] ?: JSONObject.NULL else JSONObject.NULL)
                .put("videoStabilizationActual", total[CaptureResult.CONTROL_VIDEO_STABILIZATION_MODE] ?: JSONObject.NULL)
                .put("distortionCorrectionActual", if (Build.VERSION.SDK_INT >= 28) sensor[CaptureResult.DISTORTION_CORRECTION_MODE] ?: JSONObject.NULL else JSONObject.NULL)
                .put("lensIntrinsicCalibration", sensor[CaptureResult.LENS_INTRINSIC_CALIBRATION]?.let { org.json.JSONArray(it.map { value -> value.toDouble() }) } ?: JSONObject.NULL)
                .put("lensDistortion", if (Build.VERSION.SDK_INT >= 28) sensor[CaptureResult.LENS_DISTORTION]?.let { org.json.JSONArray(it.map { value -> value.toDouble() }) } ?: JSONObject.NULL else JSONObject.NULL)
            // Read the JPEG's own ISO tag: sensor gain and JPEG post-RAW
            // amplification are distinct and the HAL may report their product.
            runCatching {
                val exif = androidx.exifinterface.media.ExifInterface(java.io.ByteArrayInputStream(jpeg))
                evidence.put("jpegExifIso", exif.getAttributeInt(
                    androidx.exifinterface.media.ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY, 0).takeIf { it > 0 } ?: JSONObject.NULL)
            }.onFailure { evidence.put("jpegExifReadError", it.toString()) }
            val exposureError = validateExposure(sensor) ?: validateComparison(sensor)
            val prefix = "SeaFrogs_${System.currentTimeMillis()}_${UUID.randomUUID()}"
            // Persist the first conventional JPEG before allocating fusion memory.
            if (!nativeFusion || seriesFrames.isEmpty()) {
                savedJpeg = save("$prefix.jpg", "image/jpeg") { it.write(jpeg) }
                if (nativeFusion) fallbackBytes = jpeg
            }
            if (raw != null && (!nativeFusion || retainDng && seriesFrames.isEmpty())) DngCreator(c, sensor).use { creator ->
                creator.setOrientation(when (orientation) { 90 -> 6; 180 -> 3; 270 -> 8; else -> 1 })
                creator.setDescription(JSONObject(description).put("rawCapture", JSONObject(evidence.toString())).toString())
                savedDng = save("$prefix.dng", "image/x-adobe-dng") { creator.writeImage(it, raw) }
            }
            if (nativeFusion) {
                checkNotNull(raw)
                check(timestamp !in fusionTimestamps) { "Doppelter RAW-Sensorzeitstempel" }
                if (rawStack == null) {
                    rawStack = NativeHdr.allocate(app, raw.width, raw.height)
                    fusionSize = Size(raw.width, raw.height); fusionSensor = sensor
                    fusionRawCrop = android.graphics.Rect(raw.cropRect)
                }
                check(fusionSize == Size(raw.width, raw.height)) { "RAW-Größe innerhalb der Serie verändert" }
                val plane = raw.planes[0]
                RawPlaneCopy.copy(plane.buffer, plane.rowStride, plane.pixelStride, raw.width, raw.height,
                    checkNotNull(rawStack), seriesFrames.size)
                fusionTimestamps.add(timestamp)
                evidence.put("nativeFusionFramesCopied", fusionTimestamps.size)
                    .put("nativeFusionTimestampsNs", org.json.JSONArray(fusionTimestamps))
            }
            seriesFrames.add(RawSeriesFrame(checkNotNull(savedJpeg), savedDng, evidence.toString()))
            rawImage?.close(); rawImage = null
            stillResult = null
            jpegFrames.clear()
            if (exposureError != null) finish(exposureError)
            else if (seriesFrames.size >= seriesCount) {
                if (nativeFusion) finishFusion() else finish(null)
            }
            else {
                // Keep the camera and manual AE/AWB/AF settings alive. Only one
                // outstanding RAW image, so DNG writing cannot exhaust the reader.
                evidence.put("seriesFrameIndex", seriesFrames.size)
                worker.post { if (!finished) submitStill(checkNotNull(session), sensor, captureCallback) }
            }
        } catch (error: Throwable) { finish(error.toString()) }
    }

    private fun finishFusion() {
        reportProgress("MEHRBILD: 5/5 erfasst · Bilder verarbeiten …")
        val started = SystemClock.elapsedRealtime()
        evidence.put("nativeCaptureSpanNs", fusionTimestamps.last() - fusionTimestamps.first())
        val sensor = checkNotNull(fusionSensor)
        val size = checkNotNull(fusionSize)
        check(!cancelRequested.get()) { "MEHRBILD abgebrochen" }
        val target = checkNotNull(targetJpegSize)
        val bitmap = NativeHdr.render(checkNotNull(rawStack), size.width, size.height, sensor,
            checkNotNull(characteristics), zoom, orientation, target.width, target.height, fusionRawCrop,
            Build.VERSION.SDK_INT >= 30 && logicalCharacteristics?.get(CameraCharacteristics.CONTROL_ZOOM_RATIO_RANGE) != null)
        try {
            check(!cancelRequested.get()) { "MEHRBILD abgebrochen; normales JPEG erhalten" }
            reportProgress("MEHRBILD: JPEG und EXIF speichern …")
            val uri = save("SeaFrogs_${System.currentTimeMillis()}_MEHRBILD.jpg", "image/jpeg") {
                check(bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 98, it)) { "MEHRBILD-JPEG nicht komprimiert" }
            }
            try {
                val original = androidx.exifinterface.media.ExifInterface(java.io.ByteArrayInputStream(checkNotNull(fallbackBytes)))
                app.contentResolver.openFileDescriptor(uri, "rw").use { fd ->
                    val exif = androidx.exifinterface.media.ExifInterface(checkNotNull(fd).fileDescriptor)
                    // Sensor tags describe the reference exposure. Output is already rotated.
                    listOf("Make", "Model", "DateTime", "DateTimeOriginal", "ExposureTime", "FNumber",
                        "PhotographicSensitivity", "ISOSpeedRatings", "FocalLength", "ExposureBiasValue").forEach { key ->
                        original.getAttribute(key)?.let { exif.setAttribute(key, it) }
                    }
                    exif.setAttribute(androidx.exifinterface.media.ExifInterface.TAG_ORIENTATION, "1")
                    exif.saveAttributes()
                }
            } catch (error: Throwable) {
                app.contentResolver.delete(uri, null, null); throw error
            }
            evidence.put("nativeFusionApplied", true).put("nativeFusionEngine", "timothybrooks/hdr-plus MIT")
                .put("nativeFusionElapsedMs", SystemClock.elapsedRealtime() - started)
                .put("nativeOutputWidth", bitmap.width).put("nativeOutputHeight", bitmap.height)
                .put("fallbackJpegUri", savedJpeg.toString()).put("equalExposureBurst", true)
                .put("lensShadingApplied", true).put("toneCompression", 1).put("toneGain", 1)
                .put("colorHeadroomPreserved", true).put("chromaFilter16BitScaled", true)
                .put("chromaDenoisingApplied", true).put("neutralToneMapBypass", true)
            savedJpeg = uri
        } finally { bitmap.recycle() }
        finish(null)
    }

    /** AE meters the current scene/EV. Disable only AE for the final exposure;
     * CAF and AWB remain active. Pinned sensors receive supported per-camera
     * keys explicitly; applied physical results, never logical guesses, verify
     * that the HAL respected the ceiling.
     */
    private fun applyExposureLimit(builder: CaptureRequest.Builder, sensor: CaptureResult) {
        val c = checkNotNull(characteristics)
        meterIso = checkNotNull(sensor[CaptureResult.SENSOR_SENSITIVITY]) { "ISO-Messwert fehlt" }
        meterTimeNs = checkNotNull(sensor[CaptureResult.SENSOR_EXPOSURE_TIME]) { "Zeit-Messwert fehlt" }
        val isoRange = checkNotNull(c[CameraCharacteristics.SENSOR_INFO_SENSITIVITY_RANGE])
        val timeRange = checkNotNull(c[CameraCharacteristics.SENSOR_INFO_EXPOSURE_TIME_RANGE])
        val selected = ExposureLimitCalculator.calculate(meterIso, meterTimeNs, limits,
            isoRange.lower, isoRange.upper, timeRange.lower, timeRange.upper)
        manualExposure = selected
        setSensorKey(builder, CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_OFF)
        setSensorKey(builder, CaptureRequest.SENSOR_SENSITIVITY, selected.iso)
        setSensorKey(builder, CaptureRequest.SENSOR_EXPOSURE_TIME, selected.timeNs)
        val maxFrame = checkNotNull(c[CameraCharacteristics.SENSOR_INFO_MAX_FRAME_DURATION])
        val frame = maxOf(selected.timeNs, sensor[CaptureResult.SENSOR_FRAME_DURATION] ?: selected.timeNs)
            .coerceAtMost(maxFrame)
        setSensorKey(builder, CaptureRequest.SENSOR_FRAME_DURATION, frame)
        // Preserve the meter's JPEG gain, rather than silently changing brightness
        // when switching AE off. RAW remains the unboosted sensor exposure.
        val boostRange = c[CameraCharacteristics.CONTROL_POST_RAW_SENSITIVITY_BOOST_RANGE]
        val boost = sensor[CaptureResult.CONTROL_POST_RAW_SENSITIVITY_BOOST]
        if (boost != null && boostRange != null && boost in boostRange &&
            checkNotNull(logicalCharacteristics).availableCaptureRequestKeys.contains(CaptureRequest.CONTROL_POST_RAW_SENSITIVITY_BOOST)) {
            requestedBoost = selectedBoost(boost)
            setSensorKey(builder, CaptureRequest.CONTROL_POST_RAW_SENSITIVITY_BOOST, checkNotNull(requestedBoost))
        } else if (limits.boostCap > 0 || (boost != null && boost != 100)) {
            error("JPEG-Verstärkung der Messung lässt sich nicht übernehmen")
        }
        evidence.put("meterIso", meterIso).put("meterTimeNs", meterTimeNs)
            .put("isoRequested", selected.iso).put("timeNsRequested", selected.timeNs)
            .put("brightnessDifferenceEvPlanned", selected.brightnessDifferenceEv)
            .put("postRawBoostRequested", requestedBoost ?: JSONObject.NULL)
    }

    private fun selectedBoost(measured: Int?): Int? {
        if (limits.boostCap == 0) return measured
        val c = checkNotNull(characteristics)
        val range = checkNotNull(c[CameraCharacteristics.CONTROL_POST_RAW_SENSITIVITY_BOOST_RANGE]) {
            "Digitale Verstärkungsgrenze nicht unterstützt"
        }
        check(checkNotNull(logicalCharacteristics).availableCaptureRequestKeys.contains(
            CaptureRequest.CONTROL_POST_RAW_SENSITIVITY_BOOST)) { "Digitale Verstärkung nicht steuerbar" }
        return DigitalGainLimit.select(checkNotNull(measured), limits.boostCap, range.lower, range.upper)
    }

    private fun <T> setSensorKey(builder: CaptureRequest.Builder, key: CaptureRequest.Key<T>, value: T) {
        builder.set(key, value)
        if (Build.VERSION.SDK_INT >= 28 && route.physicalId != null &&
            logicalCharacteristics?.availablePhysicalCameraRequestKeys?.contains(key) == true)
            builder.setPhysicalCameraKey(key, value, checkNotNull(route.physicalId))
    }

    private fun validateExposure(sensor: CaptureResult): String? {
        if (manualExposure == null) return null
        val iso = sensor[CaptureResult.SENSOR_SENSITIVITY]
        val time = sensor[CaptureResult.SENSOR_EXPOSURE_TIME]
        val boost = sensor[CaptureResult.CONTROL_POST_RAW_SENSITIVITY_BOOST]
        val honored = iso != null && time != null && (limits.isoCap == 0 || iso <= limits.isoCap) &&
            (limits.isoCap == 0 || time <= limits.longestTimeNs) && sensor[CaptureResult.CONTROL_AE_MODE] == CaptureResult.CONTROL_AE_MODE_OFF
        val gainHonored = (requestedBoost == null || boost == requestedBoost) &&
            (limits.boostCap == 0 || (boost != null && boost <= limits.boostCap))
        evidence.put("limitsVerified", honored).put("postRawBoostActual", boost ?: JSONObject.NULL)
            .put("postRawBoostVerified", gainHonored)
        if (iso != null && time != null) {
            val difference = ExposureLimitCalculator.differenceEv(meterIso, meterTimeNs, iso, time)
            evidence.put("brightnessDifferenceEvSensor", difference)
                .put("underexposedByLimits", difference < -0.1)
        }
        if (!honored) return "Foto gespeichert, aber Kamera hat ISO-/Zeitgrenzen nicht bestätigt"
        if (!gainHonored) return "Foto gespeichert, aber JPEG-Verstärkung weicht vom Messwert ab"
        return null
    }

    private fun applyFrozen(builder: CaptureRequest.Builder, value: FrozenCaptureSettings) {
        setSensorKey(builder, CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_OFF)
        setSensorKey(builder, CaptureRequest.SENSOR_SENSITIVITY, value.iso)
        setSensorKey(builder, CaptureRequest.SENSOR_EXPOSURE_TIME, value.timeNs)
        setSensorKey(builder, CaptureRequest.SENSOR_FRAME_DURATION, value.frameNs)
        value.boost?.let { setSensorKey(builder, CaptureRequest.CONTROL_POST_RAW_SENSITIVITY_BOOST, it) }
        setSensorKey(builder, CaptureRequest.CONTROL_AWB_MODE, CaptureRequest.CONTROL_AWB_MODE_OFF)
        setSensorKey(builder, CaptureRequest.COLOR_CORRECTION_MODE, CaptureRequest.COLOR_CORRECTION_MODE_TRANSFORM_MATRIX)
        setSensorKey(builder, CaptureRequest.COLOR_CORRECTION_GAINS, value.gains)
        setSensorKey(builder, CaptureRequest.COLOR_CORRECTION_TRANSFORM, value.transform)
        setSensorKey(builder, CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_OFF)
        setSensorKey(builder, CaptureRequest.LENS_FOCUS_DISTANCE, value.focusDiopters)
    }

    private fun applyProcessing(builder: CaptureRequest.Builder) {
        val c = checkNotNull(characteristics)
        if (processing == ProcessingVariant.NR_HIGH_QUALITY || processing == ProcessingVariant.NR_EDGE_HIGH_QUALITY) {
            check(c[CameraCharacteristics.NOISE_REDUCTION_AVAILABLE_NOISE_REDUCTION_MODES]?.contains(
                CaptureRequest.NOISE_REDUCTION_MODE_HIGH_QUALITY) == true) { "HQ-Entrauschen nicht unterstützt" }
            setSensorKey(builder, CaptureRequest.NOISE_REDUCTION_MODE, CaptureRequest.NOISE_REDUCTION_MODE_HIGH_QUALITY)
        }
        if (processing == ProcessingVariant.NR_HIGH_QUALITY) {
            checkNotNull(requestedReference?.edge) { "Referenz-Schärfungsmodus fehlt" }.let {
                setSensorKey(builder, CaptureRequest.EDGE_MODE, it)
            }
        }
        if (processing == ProcessingVariant.NR_EDGE_HIGH_QUALITY) {
            check(c[CameraCharacteristics.EDGE_AVAILABLE_EDGE_MODES]?.contains(CaptureRequest.EDGE_MODE_HIGH_QUALITY) == true) {
                "HQ-Schärfung nicht unterstützt" }
            setSensorKey(builder, CaptureRequest.EDGE_MODE, CaptureRequest.EDGE_MODE_HIGH_QUALITY)
        }
    }

    private fun closeValue(actual: Double, expected: Double, absolute: Double, relative: Double = 0.001) =
        actual.isFinite() && kotlin.math.abs(actual - expected) <= maxOf(absolute, kotlin.math.abs(expected) * relative)

    private fun frozenFocusMatches(sensor: CaptureResult): Boolean {
        val expected = requestedReference ?: return false
        val actual = sensor[CaptureResult.LENS_FOCUS_DISTANCE] ?: return false
        return sensor[CaptureResult.CONTROL_AF_MODE] == CaptureResult.CONTROL_AF_MODE_OFF &&
            sensor[CaptureResult.LENS_STATE] != CaptureResult.LENS_STATE_MOVING &&
            closeValue(actual.toDouble(), expected.focusDiopters.toDouble(), 0.02)
    }

    private fun validateComparison(sensor: CaptureResult): String? {
        if (processing == ProcessingVariant.NONE) return null
        val expected = checkNotNull(requestedReference)
        val actual = try { FrozenCaptureSettings.from(sensor) }
            catch (error: Exception) { return "Vergleichsdaten fehlen: $error" }
        actualReference = actual
        val exposureMatches = actual.iso == expected.iso &&
            closeValue(actual.timeNs.toDouble(), expected.timeNs.toDouble(), 20_000.0) &&
            actual.boost == expected.boost && sensor[CaptureResult.CONTROL_AE_MODE] == CaptureResult.CONTROL_AE_MODE_OFF
        val wbMatches = (0..3).all { closeValue(actual.gains.getComponent(it).toDouble(), expected.gains.getComponent(it).toDouble(), 0.005, 0.005) } &&
            (0..8).all { closeValue(actual.transform.getElement(it % 3, it / 3).toDouble(),
                expected.transform.getElement(it % 3, it / 3).toDouble(), 0.005, 0.005) } &&
            sensor[CaptureResult.CONTROL_AWB_MODE] == CaptureResult.CONTROL_AWB_MODE_OFF &&
            sensor[CaptureResult.COLOR_CORRECTION_MODE] == CaptureResult.COLOR_CORRECTION_MODE_TRANSFORM_MATRIX
        val focusMatches = frozenFocusMatches(sensor)
        val nr = sensor[CaptureResult.NOISE_REDUCTION_MODE]
        val edge = sensor[CaptureResult.EDGE_MODE]
        val processingMatches = nr != null && edge != null &&
            (evidence.isNull("noiseReductionRequested") || nr == evidence.optInt("noiseReductionRequested", -1)) &&
            (evidence.isNull("edgeRequested") || edge == evidence.optInt("edgeRequested", -1))
        evidence.put("comparisonActualSettings", actual.json()).put("exposureFrozenVerified", exposureMatches)
            .put("whiteBalanceFrozenVerified", wbMatches).put("focusFrozenVerified", focusMatches)
            .put("processingVerified", processingMatches)
            .put("comparisonVerified", exposureMatches && wbMatches && focusMatches && processingMatches)
        return when {
            !exposureMatches -> "Vergleichsbelichtung weicht ab; Foto gespeichert"
            !wbMatches -> "Vergleichsweißabgleich weicht ab; Foto gespeichert"
            !focusMatches -> "Vergleichsfokus weicht ab; Foto gespeichert"
            !processingMatches -> "Bildverarbeitung nicht bestätigt; Foto gespeichert"
            else -> null
        }
    }

    private fun save(name: String, mime: String, write: (java.io.OutputStream) -> Unit): Uri {
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, name); put(MediaStore.Images.Media.MIME_TYPE, mime)
            if (Build.VERSION.SDK_INT >= 29) {
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/SeaFrogs")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            } else {
                val folder = java.io.File(android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_PICTURES), "SeaFrogs")
                check(folder.exists() || folder.mkdirs()) { "Speicherordner fehlt" }
                put(MediaStore.Images.Media.DATA, java.io.File(folder, name).absolutePath)
            }
        }
        val resolver = app.contentResolver
        val uri = checkNotNull(resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values))
        try {
            checkNotNull(resolver.openOutputStream(uri)).use(write)
            if (Build.VERSION.SDK_INT >= 29) resolver.update(uri, ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }, null, null)
            return uri
        } catch (error: Exception) { resolver.delete(uri, null, null); throw error }
    }

    private fun reportProgress(value: String) {
        main.execute { if (!cancelRequested.get()) progress?.invoke(value) }
    }

    fun cancel() { cancelRequested.set(true); worker.post { finish("RAW-Aufnahme durch Lifecycle-Wechsel abgebrochen") } }

    private fun finish(error: String?) {
        if (finished) return
        finished = true
        rawStack = null; fusionSensor = null; fallbackBytes = null
        if (nativeFusion && !evidence.optBoolean("nativeFusionApplied"))
            evidence.put("nativeFusionFallback", true).put("nativeFusionFallbackReason", error ?: "Verarbeitung nicht abgeschlossen")
        worker.removeCallbacksAndMessages(null)
        rawImage?.close(); rawImage = null
        jpegFrames.clear()
        session?.close(); session = null
        jpegReader?.close(); jpegReader = null
        rawReader?.close(); rawReader = null
        finishedOutcome = RawPairOutcome(savedJpeg, savedDng, evidence.toString(), error, actualReference, seriesFrames.toList())
        val closing = device
        device = null
        if (closing != null) {
            closing.close()
            worker.postDelayed({ deliver() }, 2000) // Some HALs omit onClosed after an error.
        } else if (!opening) deliver()
    }

    private fun deliver() {
        if (delivered) return
        val outcome = finishedOutcome ?: return
        delivered = true
        main.execute { callback?.invoke(outcome) }
        thread.quitSafely()
    }
}
