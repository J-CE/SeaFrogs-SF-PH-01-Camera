package de.jce.seafrogs

data class PixelRect(val left: Int, val top: Int, val right: Int, val bottom: Int)

/** Intersect measured valid regions, apply zoom once and center the output aspect. */
object PhotoCrop {
    fun calculate(width: Int, height: Int, sensor: PixelRect, valid: PixelRect,
                  zoom: Float, aspect: Float): PixelRect {
        require(width > 0 && height > 0 && zoom.isFinite() && zoom >= 1f && aspect.isFinite() && aspect > 0)
        val left = maxOf(0, sensor.left, valid.left)
        val top = maxOf(0, sensor.top, valid.top)
        val right = minOf(width, sensor.right, valid.right)
        val bottom = minOf(height, sensor.bottom, valid.bottom)
        require(right > left && bottom > top) { "Sensor-/RAW-Ausschnitt ungültig" }
        var w = ((right-left)/zoom).toInt().coerceAtLeast(1)
        var h = ((bottom-top)/zoom).toInt().coerceAtLeast(1)
        if (w.toFloat()/h > aspect) w = (h*aspect).toInt().coerceAtLeast(1)
        else h = (w/aspect).toInt().coerceAtLeast(1)
        val x = left + (right-left-w)/2
        val y = top + (bottom-top-h)/2
        return PixelRect(x,y,x+w,y+h)
    }
}
