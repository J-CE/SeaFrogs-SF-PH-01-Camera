package de.jce.seafrogs

import android.app.ActivityManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.hardware.camera2.CameraCharacteristics as C
import android.hardware.camera2.CaptureResult as R
import java.nio.ByteBuffer

/** Five equal-exposure RAW frames, MIT HDR+ fusion and metadata-driven color output. */
object NativeHdr {
    init {
        System.loadLibrary("seafrogs_hdr")
    }

    external fun process(
        raw: ByteBuffer,
        width: Int,
        height: Int,
        count: Int,
        black: FloatArray,
        white: Int,
        shading: FloatArray,
        mapWidth: Int,
        mapHeight: Int,
        active: FloatArray,
        gains: FloatArray,
        cfa: Int,
        matrix: FloatArray,
        boost: Float,
        output: Bitmap,
    )

    fun allocate(context: Context, width: Int, height: Int): ByteBuffer {
        require(width.toLong() * height in 4096L..13_000_000L && width % 2 == 0 && height % 2 == 0)
        val memory = ActivityManager.MemoryInfo()
        context.getSystemService(ActivityManager::class.java).getMemoryInfo(memory)
        check(!memory.lowMemory && memory.availMem >= 700L * 1024 * 1024) {
            "Zu wenig Arbeitsspeicher für MEHRBILD"
        }
        return ByteBuffer.allocateDirect(Math.multiplyExact(Math.multiplyExact(width, height), 10))
    }

    fun render(
        raw: ByteBuffer,
        width: Int,
        height: Int,
        sensor: R,
        cameraCharacteristics: C,
        zoom: Float,
        orientation: Int,
        jpegWidth: Int,
        jpegHeight: Int,
        rawCrop: android.graphics.Rect? = null,
        zoomUsesRatio: Boolean = true,
    ): Bitmap {
        val pattern =
            checkNotNull(cameraCharacteristics[C.SENSOR_INFO_COLOR_FILTER_ARRANGEMENT]) {
                "Bayer-Muster fehlt"
            }
        val (cfa, colors) =
            when (pattern) {
                C.SENSOR_INFO_COLOR_FILTER_ARRANGEMENT_RGGB -> 1 to intArrayOf(0, 1, 2, 3)
                C.SENSOR_INFO_COLOR_FILTER_ARRANGEMENT_GRBG -> 2 to intArrayOf(1, 0, 3, 2)
                C.SENSOR_INFO_COLOR_FILTER_ARRANGEMENT_GBRG -> 4 to intArrayOf(1, 3, 0, 2)
                C.SENSOR_INFO_COLOR_FILTER_ARRANGEMENT_BGGR -> 3 to intArrayOf(3, 1, 2, 0)
                else -> error("Bayer-Muster nicht unterstützt")
            }
        val fixedBlack =
            checkNotNull(cameraCharacteristics[C.SENSOR_BLACK_LEVEL_PATTERN]) {
                "Schwarzpegel fehlen"
            }
        val black =
            sensor[R.SENSOR_DYNAMIC_BLACK_LEVEL]?.let { values -> FloatArray(4) { values[it] } }
                ?: FloatArray(4) { fixedBlack.getOffsetForIndex(it % 2, it / 2).toFloat() }
        val white =
            sensor[R.SENSOR_DYNAMIC_WHITE_LEVEL]
                ?: checkNotNull(cameraCharacteristics[C.SENSOR_INFO_WHITE_LEVEL])
        check(white in 1..65535 && black.all { it.isFinite() && it >= 0 && it < white }) {
            "Ungültige Sensorpegel"
        }
        val lensShadingMap =
            checkNotNull(sensor[R.STATISTICS_LENS_SHADING_CORRECTION_MAP]) {
                "Objektivkorrektur fehlt; normales JPEG verwendet"
            }
        require(lensShadingMap.columnCount in 1..128 && lensShadingMap.rowCount in 1..128)
        val shading =
            FloatArray(lensShadingMap.columnCount * lensShadingMap.rowCount * 4) { index ->
                val size = lensShadingMap.columnCount * lensShadingMap.rowCount
                val cell = index % size
                lensShadingMap.getGainFactor(
                    colors[index / size],
                    cell % lensShadingMap.columnCount,
                    cell / lensShadingMap.columnCount,
                )
            }
        check(shading.all { it.isFinite() && it in 1f..16f }) { "Ungültige Objektivkorrektur" }
        val whiteBalanceGains = checkNotNull(sensor[R.COLOR_CORRECTION_GAINS])
        val gains = FloatArray(4) { whiteBalanceGains.getComponent(it) }
        check(gains.all { it.isFinite() && it in 0.01f..16f })
        val transform = checkNotNull(sensor[R.COLOR_CORRECTION_TRANSFORM])
        val matrix = FloatArray(9) { transform.getElement(it % 3, it / 3).toFloat() }
        check(matrix.all { it.isFinite() && kotlin.math.abs(it) <= 16 })
        val pixelArray = checkNotNull(cameraCharacteristics[C.SENSOR_INFO_PIXEL_ARRAY_SIZE])
        val active =
            checkNotNull(cameraCharacteristics[C.SENSOR_INFO_PRE_CORRECTION_ACTIVE_ARRAY_SIZE])
        val sensorScaleX = width.toFloat() / pixelArray.width
        val sensorScaleY = height.toFloat() / pixelArray.height
        val activeSensorRectangle =
            floatArrayOf(
                active.left * sensorScaleX,
                active.top * sensorScaleY,
                active.width() * sensorScaleX,
                active.height() * sensorScaleY,
            )
        val boost = (sensor[R.CONTROL_POST_RAW_SENSITIVITY_BOOST] ?: 100) / 100f
        check(boost.isFinite() && boost in 0.1f..16f)
        val fullResolutionBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        var croppedBitmap: Bitmap? = null
        try {
            process(
                raw,
                width,
                height,
                5,
                black,
                white,
                shading,
                lensShadingMap.columnCount,
                lensShadingMap.rowCount,
                activeSensorRectangle,
                gains,
                cfa,
                matrix,
                boost,
                fullResolutionBitmap,
            )
            // Apply measured sensor crop and RAW valid area before one digital zoom.
            val reported = sensor[R.SCALER_CROP_REGION] ?: active
            val bounded =
                PhotoCrop.calculate(
                    width,
                    height,
                    PixelRect(
                        (reported.left * sensorScaleX).toInt(),
                        (reported.top * sensorScaleY).toInt(),
                        (reported.right * sensorScaleX).toInt(),
                        (reported.bottom * sensorScaleY).toInt(),
                    ),
                    rawCrop?.let { PixelRect(it.left, it.top, it.right, it.bottom) }
                        ?: PixelRect(0, 0, width, height),
                    // Pre-30 SCALER_CROP_REGION already carries the zoom.
                    if (zoomUsesRatio) zoom else 1f,
                    jpegWidth.toFloat() / jpegHeight,
                )
            val left = bounded.left
            val top = bounded.top
            val cropWidth = bounded.right - left
            val cropHeight = bounded.bottom - top
            croppedBitmap =
                Bitmap.createBitmap(fullResolutionBitmap, left, top, cropWidth, cropHeight)
            if (croppedBitmap !== fullResolutionBitmap) fullResolutionBitmap.recycle()
            if (orientation == 0) return croppedBitmap
            val rotated =
                Bitmap.createBitmap(
                    croppedBitmap,
                    0,
                    0,
                    cropWidth,
                    cropHeight,
                    Matrix().apply { postRotate(orientation.toFloat()) },
                    true,
                )
            if (rotated !== croppedBitmap) croppedBitmap.recycle()
            return rotated
        } catch (error: Throwable) {
            if (!fullResolutionBitmap.isRecycled) fullResolutionBitmap.recycle()
            croppedBitmap?.takeIf { !it.isRecycled && it !== fullResolutionBitmap }?.recycle()
            throw error
        }
    }
}
