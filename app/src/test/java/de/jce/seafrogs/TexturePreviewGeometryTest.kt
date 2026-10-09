package de.jce.seafrogs

import org.junit.Assert.*
import org.junit.Test

class TexturePreviewGeometryTest {
    @Test
    fun pixelPortraitDoesNotApplyTheSensorRotationAgain() {
        val g = TexturePreviewGeometry.fit(1080, 1440, 1920, 1440, 90, 0)
        assertEquals(0f, g.rotationDegrees, 0f)
        assertEquals(1f, g.scaleX, 0.00001f)
        assertEquals(1f, g.scaleY, 0.00001f)
    }

    @Test
    fun allDisplayRotationsPreserveFullFourByThreeFrame() {
        for (sensor in listOf(0, 90, 180, 270)) for (rotation in 0..3) {
            val swaps = (sensor / 90 + rotation) % 2 != 0
            val w = if (swaps) 1080 else 1440
            val h = if (swaps) 1440 else 1080
            val g = TexturePreviewGeometry.fit(w, h, 1920, 1440, sensor, rotation)
            val preWidth = w * g.scaleX
            val preHeight = h * g.scaleY
            val finalWidth = if (rotation % 2 == 0) preWidth else preHeight
            val finalHeight = if (rotation % 2 == 0) preHeight else preWidth
            assertEquals(w.toFloat(), finalWidth, 0.001f)
            assertEquals(h.toFloat(), finalHeight, 0.001f)
            assertEquals(-90f * rotation, g.rotationDegrees, 0f)
        }
    }

    @Test
    fun wideWindowLetterboxesInsteadOfCroppingThePortraitFrame() {
        val g = TexturePreviewGeometry.fit(1600, 900, 1920, 1440, 90, 0)
        assertEquals(675f, 1600 * g.scaleX, 0.001f)
        assertEquals(900f, 900 * g.scaleY, 0.001f)
    }

    @Test
    fun rotatedSquareWindowKeepsFullLandscapeFrame() {
        val g = TexturePreviewGeometry.fit(1000, 1000, 1920, 1440, 90, 1)
        assertEquals(1000f, 1000 * g.scaleY, 0.001f)
        assertEquals(750f, 1000 * g.scaleX, 0.001f)
    }
}
