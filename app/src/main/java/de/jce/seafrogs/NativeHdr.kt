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
    init { System.loadLibrary("seafrogs_hdr") }
    external fun process(raw: ByteBuffer, width: Int, height: Int, count: Int,
        black: FloatArray, white: Int, shading: FloatArray, mapWidth: Int, mapHeight: Int,
        active: FloatArray, gains: FloatArray, cfa: Int, matrix: FloatArray, boost: Float, output: Bitmap)

    fun allocate(context: Context, width: Int, height: Int): ByteBuffer {
        require(width.toLong() * height in 4096L..13_000_000L && width % 2 == 0 && height % 2 == 0)
        val memory = ActivityManager.MemoryInfo()
        context.getSystemService(ActivityManager::class.java).getMemoryInfo(memory)
        check(!memory.lowMemory && memory.availMem >= 700L * 1024 * 1024) { "Zu wenig Arbeitsspeicher für MEHRBILD" }
        return ByteBuffer.allocateDirect(Math.multiplyExact(Math.multiplyExact(width, height), 10))
    }

    fun render(raw: ByteBuffer, width: Int, height: Int, sensor: R, c: C,
               zoom: Float, orientation: Int, jpegWidth: Int, jpegHeight: Int): Bitmap {
        val pattern = checkNotNull(c[C.SENSOR_INFO_COLOR_FILTER_ARRANGEMENT]) { "Bayer-Muster fehlt" }
        val (cfa, colors) = when (pattern) {
            C.SENSOR_INFO_COLOR_FILTER_ARRANGEMENT_RGGB -> 1 to intArrayOf(0,1,2,3)
            C.SENSOR_INFO_COLOR_FILTER_ARRANGEMENT_GRBG -> 2 to intArrayOf(1,0,3,2)
            C.SENSOR_INFO_COLOR_FILTER_ARRANGEMENT_GBRG -> 4 to intArrayOf(1,3,0,2)
            C.SENSOR_INFO_COLOR_FILTER_ARRANGEMENT_BGGR -> 3 to intArrayOf(3,1,2,0)
            else -> error("Bayer-Muster nicht unterstützt")
        }
        val fixedBlack = checkNotNull(c[C.SENSOR_BLACK_LEVEL_PATTERN]) { "Schwarzpegel fehlen" }
        val black = sensor[R.SENSOR_DYNAMIC_BLACK_LEVEL]?.let { values -> FloatArray(4) { values[it] } }
            ?: FloatArray(4) { fixedBlack.getOffsetForIndex(it % 2, it / 2).toFloat() }
        val white = sensor[R.SENSOR_DYNAMIC_WHITE_LEVEL] ?: checkNotNull(c[C.SENSOR_INFO_WHITE_LEVEL])
        check(white in 1..65535 && black.all { it.isFinite() && it >= 0 && it < white }) { "Ungültige Sensorpegel" }
        val map = checkNotNull(sensor[R.STATISTICS_LENS_SHADING_CORRECTION_MAP]) { "Objektivkorrektur fehlt; normales JPEG verwendet" }
        require(map.columnCount in 1..128 && map.rowCount in 1..128)
        val shading = FloatArray(map.columnCount * map.rowCount * 4) { index ->
            val size = map.columnCount * map.rowCount
            val cell = index % size
            map.getGainFactor(colors[index / size], cell % map.columnCount, cell / map.columnCount)
        }
        check(shading.all { it.isFinite() && it in 1f..16f }) { "Ungültige Objektivkorrektur" }
        val wb = checkNotNull(sensor[R.COLOR_CORRECTION_GAINS])
        val gains = FloatArray(4) { wb.getComponent(it) }
        check(gains.all { it.isFinite() && it in 0.01f..16f })
        val transform = checkNotNull(sensor[R.COLOR_CORRECTION_TRANSFORM])
        val matrix = FloatArray(9) { transform.getElement(it % 3, it / 3).toFloat() }
        check(matrix.all { it.isFinite() && kotlin.math.abs(it) <= 16 })
        val pixelArray = checkNotNull(c[C.SENSOR_INFO_PIXEL_ARRAY_SIZE])
        val active = checkNotNull(c[C.SENSOR_INFO_PRE_CORRECTION_ACTIVE_ARRAY_SIZE])
        val sx = width.toFloat() / pixelArray.width; val sy = height.toFloat() / pixelArray.height
        val rect = floatArrayOf(active.left * sx, active.top * sy, active.width() * sx, active.height() * sy)
        val boost = (sensor[R.CONTROL_POST_RAW_SENSITIVITY_BOOST] ?: 100) / 100f
        check(boost.isFinite() && boost in 0.1f..16f)
        val full = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        var cropped: Bitmap? = null
        try {
            process(raw, width, height, 5, black, white, shading, map.columnCount, map.rowCount, rect, gains, cfa, matrix, boost, full)
            // RAW ignores JPEG's digital crop. Apply it once, after fusion; never upscale.
            var cw = (rect[2] / zoom).toInt().coerceIn(1, width)
            var ch = (rect[3] / zoom).toInt().coerceIn(1, height)
            val aspect = jpegWidth.toFloat() / jpegHeight
            if (cw.toFloat() / ch > aspect) cw = (ch * aspect).toInt().coerceAtLeast(1)
            else ch = (cw / aspect).toInt().coerceAtLeast(1)
            val left = (rect[0] + (rect[2] - cw) / 2).toInt().coerceIn(0, width - cw)
            val top = (rect[1] + (rect[3] - ch) / 2).toInt().coerceIn(0, height - ch)
            cropped = Bitmap.createBitmap(full, left, top, cw, ch)
            if (cropped !== full) full.recycle()
            if (orientation == 0) return cropped
            val rotated = Bitmap.createBitmap(cropped, 0, 0, cw, ch, Matrix().apply { postRotate(orientation.toFloat()) }, true)
            if (rotated !== cropped) cropped.recycle()
            return rotated
        } catch (error: Throwable) {
            if (!full.isRecycled) full.recycle()
            cropped?.takeIf { !it.isRecycled && it !== full }?.recycle()
            throw error
        }
    }
}
