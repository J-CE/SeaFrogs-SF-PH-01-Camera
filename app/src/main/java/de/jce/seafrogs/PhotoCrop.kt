package de.jce.seafrogs

data class PixelRect(val left: Int, val top: Int, val right: Int, val bottom: Int)

/** Intersect measured valid regions, apply zoom once and center the output aspect. */
object PhotoCrop {
    fun calculate(
        width: Int,
        height: Int,
        sensor: PixelRect,
        valid: PixelRect,
        zoom: Float,
        aspect: Float,
    ): PixelRect {
        require(
            width > 0 &&
                height > 0 &&
                zoom.isFinite() &&
                zoom >= 1f &&
                aspect.isFinite() &&
                aspect > 0
        )
        val left = maxOf(0, sensor.left, valid.left)
        val top = maxOf(0, sensor.top, valid.top)
        val right = minOf(width, sensor.right, valid.right)
        val bottom = minOf(height, sensor.bottom, valid.bottom)
        require(right > left && bottom > top) { "Sensor-/RAW-Ausschnitt ungültig" }
        var cropWidth = ((right - left) / zoom).toInt().coerceAtLeast(1)
        var cropHeight = ((bottom - top) / zoom).toInt().coerceAtLeast(1)
        if (cropWidth.toFloat() / cropHeight > aspect)
            cropWidth = (cropHeight * aspect).toInt().coerceAtLeast(1)
        else cropHeight = (cropWidth / aspect).toInt().coerceAtLeast(1)
        val cropLeft = left + (right - left - cropWidth) / 2
        val cropTop = top + (bottom - top - cropHeight) / 2
        return PixelRect(cropLeft, cropTop, cropLeft + cropWidth, cropTop + cropHeight)
    }
}
