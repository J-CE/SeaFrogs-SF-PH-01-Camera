package de.jce.seafrogs

import android.content.Context
import android.graphics.ImageFormat
import android.graphics.Rect
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.hardware.camera2.params.StreamConfigurationMap
import android.os.Build
import android.util.Range
import android.util.Rational
import android.util.Size
import org.json.JSONArray
import org.json.JSONObject

/** Read-only metadata query. Offered sizes do not prove a working capture session. */
object CameraCapabilityReport {
    fun collect(context: Context): JSONObject {
        val report = JSONObject()
            .put("schemaVersion", 2).put("cameraXVersion", "1.6.2").put("appVersion", "0.7.2-color-gain")
            .put("createdWallTimeMs", System.currentTimeMillis())
            .put("device", Build.MODEL).put("androidBuild", Build.FINGERPRINT)
            .put("sdk", Build.VERSION.SDK_INT)
            .put("evidence", "advertisedCamera2MetadataOnly")
            .put("note", "Sizes are advertised, not verified captures. Physical metadata does not prove independent openability. No RAW/DNG capture is performed.")
        val errors = JSONArray()
        report.put("errors", errors)
        try {
            val manager = context.getSystemService(CameraManager::class.java)
            val publicIds = manager.cameraIdList.toList()
            report.put("publicCameraIds", JSONArray(publicIds))
            val parents = linkedMapOf<String, MutableSet<String>>()
            val characteristics = linkedMapOf<String, CameraCharacteristics>()
            fun read(id: String): CameraCharacteristics? {
                characteristics[id]?.let { return it }
                return try {
                    manager.getCameraCharacteristics(id).also { characteristics[id] = it }
                } catch (error: Exception) {
                    errors.put(JSONObject().put("cameraId", id).put("operation", "getCameraCharacteristics")
                        .put("error", error.toString()))
                    null
                }
            }
            for (id in publicIds) {
                val c = read(id) ?: continue
                if (Build.VERSION.SDK_INT >= 28) {
                    for (physical in c.physicalCameraIds.sorted()) {
                        parents.getOrPut(physical) { linkedSetOf() }.add(id)
                    }
                }
            }
            val cameras = JSONArray()
            for (id in (publicIds + parents.keys).distinct()) {
                val entry = JSONObject().put("id", id)
                    .put("listedAsPublicCamera", id in publicIds)
                    .put("physicalOf", JSONArray(parents[id]?.toList() ?: emptyList<String>()))
                val c = read(id)
                if (c == null) entry.put("metadataAvailable", false)
                else {
                    entry.put("metadataAvailable", true)
                    describe(c, entry)
                    if (id in publicIds && Build.VERSION.SDK_INT >= 31) {
                        entry.put("camera2Extensions", extensionReport(manager, id))
                    }
                }
                cameras.put(entry)
            }
            report.put("cameras", cameras)
        } catch (error: Exception) {
            errors.put(JSONObject().put("operation", "enumerateCameras").put("error", error.toString()))
        }
        return report
    }


    @androidx.annotation.RequiresApi(31)
    private fun extensionReport(manager: CameraManager, id: String): JSONObject {
        val out = JSONObject().put("evidence", "advertisedOnlyNotCaptured")
        val errors = JSONArray(); out.put("errors", errors)
        try {
            val c = manager.getCameraExtensionCharacteristics(id)
            val offered = c.supportedExtensions
            out.put("supportedTypes", JSONArray(offered))
            val variants = JSONArray()
            val names = listOf(0 to "AUTO", 1 to "FACE_RETOUCH", 2 to "BOKEH", 3 to "HDR", 4 to "NIGHT")
            for ((type, name) in names) {
                val item = JSONObject().put("type", type).put("name", name).put("supported", type in offered)
                if (type in offered) {
                    for ((format, label) in listOf(ImageFormat.JPEG to "jpegSizes", ImageFormat.YUV_420_888 to "yuvSizes")) {
                        try { item.put(label, json(c.getExtensionSupportedSizes(type, format))) }
                        catch (error: Exception) { errors.put(JSONObject().put("mode", name).put("field", label).put("error", error.toString())) }
                    }
                    if (Build.VERSION.SDK_INT >= 34) try {
                        item.put("ultraHdrSizes", json(c.getExtensionSupportedSizes(type, ImageFormat.JPEG_R)))
                    } catch (error: Exception) { errors.put(JSONObject().put("mode", name).put("field", "ultraHdrSizes").put("error", error.toString())) }
                    if (Build.VERSION.SDK_INT >= 33) try {
                        item.put("requestKeys", JSONArray(c.getAvailableCaptureRequestKeys(type).map { it.name }.sorted()))
                        item.put("resultKeys", JSONArray(c.getAvailableCaptureResultKeys(type).map { it.name }.sorted()))
                    } catch (error: Exception) { errors.put(JSONObject().put("mode", name).put("field", "keys").put("error", error.toString())) }
                }
                variants.put(item)
            }
            out.put("modes", variants)
        } catch (error: Exception) { errors.put(error.toString()) }
        return out
    }

    private fun describe(c: CameraCharacteristics, out: JSONObject) {
        val errors = JSONArray()
        out.put("errors", errors)
        fun field(name: String, value: () -> Any?) {
            try { out.put(name, json(value())) }
            catch (error: Exception) {
                errors.put(JSONObject().put("field", name).put("error", error.toString()))
            }
        }
        field("lensFacing") { c[CameraCharacteristics.LENS_FACING] }
        field("hardwareLevel") { c[CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL] }
        field("capabilities") { c[CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES] }
        field("rawCapability") {
            c[CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES]?.contains(
                CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_RAW)
        }
        field("manualSensorCapability") {
            c[CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES]?.contains(
                CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_MANUAL_SENSOR)
        }
        field("manualPostProcessingCapability") {
            c[CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES]?.contains(
                CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_MANUAL_POST_PROCESSING)
        }
        field("pixelArray") { c[CameraCharacteristics.SENSOR_INFO_PIXEL_ARRAY_SIZE] }
        field("activeArray") { c[CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE] }
        field("preCorrectionActiveArray") { c[CameraCharacteristics.SENSOR_INFO_PRE_CORRECTION_ACTIVE_ARRAY_SIZE] }
        field("sensorSizeMm") { c[CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE]?.let {
            JSONObject().put("width", it.width).put("height", it.height)
        } }
        field("sensorOrientation") { c[CameraCharacteristics.SENSOR_ORIENTATION] }
        field("isoRange") { c[CameraCharacteristics.SENSOR_INFO_SENSITIVITY_RANGE] }
        field("maxAnalogIso") { c[CameraCharacteristics.SENSOR_MAX_ANALOG_SENSITIVITY] }
        field("exposureTimeRangeNs") { c[CameraCharacteristics.SENSOR_INFO_EXPOSURE_TIME_RANGE] }
        field("maxFrameDurationNs") { c[CameraCharacteristics.SENSOR_INFO_MAX_FRAME_DURATION] }
        field("evIndexRange") { c[CameraCharacteristics.CONTROL_AE_COMPENSATION_RANGE] }
        field("evStep") { c[CameraCharacteristics.CONTROL_AE_COMPENSATION_STEP] }
        field("aeModes") { c[CameraCharacteristics.CONTROL_AE_AVAILABLE_MODES] }
        field("aeFpsRanges") { c[CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES]?.map { json(it) } }
        field("awbModes") { c[CameraCharacteristics.CONTROL_AWB_AVAILABLE_MODES] }
        field("awbLockAvailable") { c[CameraCharacteristics.CONTROL_AWB_LOCK_AVAILABLE] }
        field("afModes") { c[CameraCharacteristics.CONTROL_AF_AVAILABLE_MODES] }
        field("minimumFocusDistanceDiopters") { c[CameraCharacteristics.LENS_INFO_MINIMUM_FOCUS_DISTANCE] }
        field("hyperfocalDistanceDiopters") { c[CameraCharacteristics.LENS_INFO_HYPERFOCAL_DISTANCE] }
        field("focusDistanceCalibration") { c[CameraCharacteristics.LENS_INFO_FOCUS_DISTANCE_CALIBRATION] }
        field("focalLengthsMm") { c[CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS] }
        field("apertures") { c[CameraCharacteristics.LENS_INFO_AVAILABLE_APERTURES] }
        field("oisModes") { c[CameraCharacteristics.LENS_INFO_AVAILABLE_OPTICAL_STABILIZATION] }
        field("edgeModes") { c[CameraCharacteristics.EDGE_AVAILABLE_EDGE_MODES] }
        field("noiseReductionModes") { c[CameraCharacteristics.NOISE_REDUCTION_AVAILABLE_NOISE_REDUCTION_MODES] }
        field("maxDigitalZoom") { c[CameraCharacteristics.SCALER_AVAILABLE_MAX_DIGITAL_ZOOM] }
        field("availableRequestKeys") { c.availableCaptureRequestKeys?.map { it.name }?.sorted() }
        field("availableCharacteristicKeys") { c.keys.map { it.name }.sorted() }
        field("defaultStreams") { streams(c[CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP]) }
        if (Build.VERSION.SDK_INT >= 28) field("physicalCameraIds") { c.physicalCameraIds.sorted() }
        if (Build.VERSION.SDK_INT >= 30) field("zoomRatioRange") { c[CameraCharacteristics.CONTROL_ZOOM_RATIO_RANGE] }
        if (Build.VERSION.SDK_INT >= 31) {
            field("maximumResolutionPixelArray") { c[CameraCharacteristics.SENSOR_INFO_PIXEL_ARRAY_SIZE_MAXIMUM_RESOLUTION] }
            field("maximumResolutionActiveArray") { c[CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE_MAXIMUM_RESOLUTION] }
            field("maximumResolutionStreams") { streams(c[CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP_MAXIMUM_RESOLUTION]) }
        }
    }

    private fun streams(map: StreamConfigurationMap?): Any {
        if (map == null) return JSONObject.NULL
        val out = JSONObject().put("advertisedFormats", json(map.outputFormats))
        val formats = linkedMapOf("JPEG" to ImageFormat.JPEG, "RAW_SENSOR" to ImageFormat.RAW_SENSOR,
            "RAW10" to ImageFormat.RAW10, "RAW12" to ImageFormat.RAW12,
            "YUV_420_888" to ImageFormat.YUV_420_888, "PRIVATE" to ImageFormat.PRIVATE)
        for ((name, format) in formats) {
            val entry = JSONObject().put("format", format).put("advertised", format in map.outputFormats)
            // Skip unsupported formats: vendor implementations can throw on these queries.
            if (format in map.outputFormats) {
                try {
                    fun sizes(values: Array<Size>?): Any = if (values == null) JSONObject.NULL else JSONArray(
                        values.sortedByDescending { it.width.toLong() * it.height }.map { size ->
                            val detail = json(size) as JSONObject
                            try { detail.put("minFrameDurationNs", map.getOutputMinFrameDuration(format, size)) }
                            catch (error: Exception) { detail.put("minFrameDurationError", error.toString()) }
                            try { detail.put("stallDurationNs", map.getOutputStallDuration(format, size)) }
                            catch (error: Exception) { detail.put("stallDurationError", error.toString()) }
                            detail
                        })
                    entry.put("normalSizes", sizes(map.getOutputSizes(format)))
                    entry.put("highResolutionSizes", sizes(map.getHighResolutionOutputSizes(format)))
                } catch (error: Exception) { entry.put("error", error.toString()) }
            }
            out.put(name, entry)
        }
        return out
    }

    private fun json(value: Any?): Any = when (value) {
        null -> JSONObject.NULL
        is Size -> JSONObject().put("width", value.width).put("height", value.height)
            .put("pixels", value.width.toLong() * value.height)
            .put("megapixels", value.width.toDouble() * value.height / 1_000_000)
        is Rect -> JSONObject().put("left", value.left).put("top", value.top)
            .put("right", value.right).put("bottom", value.bottom)
            .put("width", value.width()).put("height", value.height())
        is Range<*> -> JSONObject().put("lower", json(value.lower)).put("upper", json(value.upper))
        is Rational -> JSONObject().put("numerator", value.numerator).put("denominator", value.denominator)
        is IntArray -> JSONArray(value.toList())
        is FloatArray -> JSONArray(value.toList())
        is Collection<*> -> JSONArray(value.map { json(it) })
        else -> value
    }
}
