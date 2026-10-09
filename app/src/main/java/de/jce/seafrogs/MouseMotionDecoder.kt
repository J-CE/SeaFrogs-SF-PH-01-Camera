package de.jce.seafrogs

/** Decode each sample, including history, on one consistent screen-coordinate basis. */
class MouseMotionDecoder {
    private var previousScreenX: Float? = null
    private var previousScreenY: Float? = null

    fun reset() {
        previousScreenX = null
        previousScreenY = null
    }

    fun position(x: Float, y: Float) {
        previousScreenX = x
        previousScreenY = y
    }

    fun sample(x: Float, y: Float, relativeX: Float, relativeY: Float): Pair<Float, Float> {
        val movementDelta =
            if (relativeX != 0f || relativeY != 0f) relativeX to relativeY
            else (previousScreenX?.let { x - it } ?: 0f) to (previousScreenY?.let { y - it } ?: 0f)
        position(x, y)
        return movementDelta
    }
}
