package de.jce.seafrogs

/** Decode each sample, including history, on one consistent screen-coordinate basis. */
class MouseMotionDecoder {
    private var previousX: Float? = null
    private var previousY: Float? = null
    fun reset() { previousX = null; previousY = null }
    fun position(x: Float, y: Float) { previousX = x; previousY = y }
    fun sample(x: Float, y: Float, relativeX: Float, relativeY: Float): Pair<Float, Float> {
        val delta = if (relativeX != 0f || relativeY != 0f) relativeX to relativeY
        else (previousX?.let { x - it } ?: 0f) to (previousY?.let { y - it } ?: 0f)
        position(x, y)
        return delta
    }
}
