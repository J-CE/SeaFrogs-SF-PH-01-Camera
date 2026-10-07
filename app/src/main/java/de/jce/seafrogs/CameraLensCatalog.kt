package de.jce.seafrogs

import android.content.Context
import android.graphics.ImageFormat
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.os.Build
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.core.CameraSelector
import androidx.camera.lifecycle.ProcessCameraProvider
import kotlin.math.abs

enum class PhotoLens(val label: String) {
    MAIN("1×"), MACRO("MACRO"), ULTRAWIDE("0,5×");

    fun next(): PhotoLens = entries[(ordinal + 1) % entries.size]
}

/** A public camera, or a physical output belonging to a public logical camera. */
@androidx.annotation.OptIn(markerClass = [ExperimentalCamera2Interop::class])
data class CameraLensRoute(
    val logicalId: String,
    val physicalId: String? = null,
    val supportsPhotoAutofocus: Boolean,
    val focalLengthPerSensorWidth: Float,
    val sensorWidth: Float,
    val pixelCount: Long
) {
    fun selector(): CameraSelector = CameraSelector.Builder()
        .addCameraFilter { cameras ->
            cameras.filter { Camera2CameraInfo.from(it).cameraId == logicalId }
        }.build()
}

/**
 * No Pixel camera ID is embedded here. Compare focal length relative to sensor
 * width, not focal length alone: different sensor sizes change the field of view.
 * Public standalone routes win ties over pinned physical outputs.
 */
@androidx.annotation.OptIn(markerClass = [ExperimentalCamera2Interop::class])
class CameraLensCatalog(context: Context, provider: ProcessCameraProvider) {
    private val manager = context.getSystemService(CameraManager::class.java)
    private val rearInfos = provider.availableCameraInfos.filter {
        Camera2CameraInfo.from(it).getCameraCharacteristic(CameraCharacteristics.LENS_FACING) ==
            CameraCharacteristics.LENS_FACING_BACK
    }
    private val mainId = Camera2CameraInfo.from(
        CameraSelector.DEFAULT_BACK_CAMERA.filter(provider.availableCameraInfos).first()
    ).cameraId
    private val mainCharacteristics = manager.getCameraCharacteristics(mainId)
    private val allRoutes = rearInfos.flatMap { info ->
        val id = Camera2CameraInfo.from(info).cameraId
        val characteristics = manager.getCameraCharacteristics(id)
        val physicalRoutes = if (Build.VERSION.SDK_INT >= 28) {
            characteristics.physicalCameraIds.sorted().mapNotNull { physicalId ->
                // Some older HALs expose a physical ID but deny its metadata.
                runCatching { route(id, physicalId, manager.getCameraCharacteristics(physicalId)) }
                    .getOrNull()
            }
        } else emptyList()
        if (physicalRoutes.isNotEmpty()) physicalRoutes
        else listOfNotNull(route(id, null, characteristics))
    }

    // Logical sensor metadata normally describes its primary physical sensor.
    // Matching sensor width and pixel count identifies that reference without IDs.
    private val primaryReference = allRoutes.filter { it.logicalId == mainId }
        .minWithOrNull(compareBy<CameraLensRoute> {
            abs(it.sensorWidth - (mainCharacteristics.get(
                CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE
            )?.width ?: it.sensorWidth))
        }.thenBy {
            val pixels = mainCharacteristics.get(CameraCharacteristics.SENSOR_INFO_PIXEL_ARRAY_SIZE)
            abs(it.pixelCount - (pixels?.let { size -> size.width.toLong() * size.height } ?: it.pixelCount))
        })
    private val widerRoutes = primaryReference?.let { reference ->
        allRoutes.filter {
            it.focalLengthPerSensorWidth < reference.focalLengthPerSensorWidth * 0.8f
        }.sortedWith(compareBy<CameraLensRoute> { it.focalLengthPerSensorWidth }
            .thenBy { it.physicalId != null }.thenBy { it.logicalId })
    } ?: emptyList()

    fun routeFor(lens: PhotoLens): CameraLensRoute? = when (lens) {
        PhotoLens.MAIN -> route(mainId, null, mainCharacteristics)
        PhotoLens.ULTRAWIDE -> widerRoutes.firstOrNull()
        PhotoLens.MACRO -> widerRoutes.firstOrNull { it.supportsPhotoAutofocus }
    }

    /** CameraX negotiates through the logical parent, so pinned outputs must
     * use sizes advertised by both parent and physical sensor. No fixed IDs/sizes. */
    fun commonOutputSizes(route: CameraLensRoute, format: Int): Set<android.util.Size> {
        fun sizes(id: String): Set<android.util.Size> {
            val map = manager.getCameraCharacteristics(id)
                .get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP) ?: return emptySet()
            val normal = map.getOutputSizes(format)?.toList() ?: emptyList()
            val high = if (format == ImageFormat.JPEG)
                map.getHighResolutionOutputSizes(format)?.toList() ?: emptyList() else emptyList()
            return (normal + high).toSet()
        }
        val logical = sizes(route.logicalId)
        return route.physicalId?.let { logical.intersect(sizes(it)) } ?: logical
    }

    fun supportsProcessing(route: CameraLensRoute, variant: ProcessingVariant): Boolean {
        if (variant == ProcessingVariant.NONE) return true
        val c = manager.getCameraCharacteristics(route.physicalId ?: route.logicalId)
        val caps = c[CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES] ?: return false
        if (!caps.contains(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_MANUAL_SENSOR) ||
            !caps.contains(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_MANUAL_POST_PROCESSING)) return false
        if (variant != ProcessingVariant.DEFAULT &&
            c[CameraCharacteristics.NOISE_REDUCTION_AVAILABLE_NOISE_REDUCTION_MODES]?.contains(CaptureRequest.NOISE_REDUCTION_MODE_HIGH_QUALITY) != true) return false
        return variant != ProcessingVariant.NR_EDGE_HIGH_QUALITY ||
            c[CameraCharacteristics.EDGE_AVAILABLE_EDGE_MODES]?.contains(CaptureRequest.EDGE_MODE_HIGH_QUALITY) == true
    }

    fun rawSize(route: CameraLensRoute): android.util.Size? {
        val c = manager.getCameraCharacteristics(route.physicalId ?: route.logicalId)
        if (c[CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES]?.contains(
                CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_RAW) != true) return null
        return c[CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP]?.getOutputSizes(ImageFormat.RAW_SENSOR)
            ?.maxByOrNull { it.width.toLong() * it.height }
    }

    fun jpegOrientation(route: CameraLensRoute, rotation: Int): Int {
        val sensor = manager.getCameraCharacteristics(route.physicalId ?: route.logicalId)
            .get(CameraCharacteristics.SENSOR_ORIENTATION) ?: 0
        // Routes are rear-facing; preserve the same Surface rotation as CameraX.
        return (sensor - rotation * 90 + 360) % 360
    }

    private fun route(
        logicalId: String, physicalId: String?, characteristics: CameraCharacteristics
    ): CameraLensRoute? {
        val width = characteristics.get(CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE)?.width
            ?: return null
        val focal = characteristics.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)
            ?.filter { it > 0f }?.minOrNull() ?: return null
        if (width <= 0f) return null
        val jpegSizes = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
            ?.getOutputSizes(ImageFormat.JPEG)
        if (jpegSizes.isNullOrEmpty()) return null
        val modes = characteristics.get(CameraCharacteristics.CONTROL_AF_AVAILABLE_MODES)
            ?: intArrayOf()
        val minimumFocus = characteristics.get(CameraCharacteristics.LENS_INFO_MINIMUM_FOCUS_DISTANCE)
            ?: 0f
        val autofocus = minimumFocus > 0f &&
            modes.contains(CaptureRequest.CONTROL_AF_MODE_AUTO) &&
            modes.contains(CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
        val pixels = characteristics.get(CameraCharacteristics.SENSOR_INFO_PIXEL_ARRAY_SIZE)
        return CameraLensRoute(logicalId, physicalId, autofocus, focal / width, width,
            pixels?.let { it.width.toLong() * it.height } ?: 0L)
    }
}
