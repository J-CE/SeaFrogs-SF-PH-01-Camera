package de.jce.seafrogs

import kotlin.math.roundToInt

/** Pure cycle calculations. Hardware limits come from the currently bound camera. */
object CameraControlCycles {
    var zoomRatios = listOf(1f, 1.5f, 3f, 5f)
    var ultrawideZoomRatios = listOf(1f, 1.5f, 3f)
    // Labels are relative to nominal main-camera FOV; factors are relative to UW.
    val macroCropRatios = listOf(1f, 2f)
    var exposureValues = listOf(0f, 1f, 2f, -1f, -2f)

    fun nextZoom(current: Float, macro: Boolean, minimum: Float, maximum: Float, ultrawide: Boolean = false): Float? {
        val supported = (if (macro) macroCropRatios else if (ultrawide) ultrawideZoomRatios else zoomRatios)
            .filter { it >= minimum && it <= maximum }
        if (supported.isEmpty()) return null
        val position = supported.indexOf(current)
        return supported[(position + 1) % supported.size]
    }

    fun exposureIndex(ev: Float, step: Float, minimum: Int, maximum: Int): Int {
        require(step.isFinite() && step > 0f && minimum <= maximum)
        return (ev / step).roundToInt().coerceIn(minimum, maximum)
    }

    fun nextExposure(current: Int, step: Float, minimum: Int, maximum: Int): Int {
        val supported = exposureValues.map { exposureIndex(it, step, minimum, maximum) }.distinct()
        return supported[(supported.indexOf(current) + 1) % supported.size]
    }
}
