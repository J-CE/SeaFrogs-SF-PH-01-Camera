package de.jce.seafrogs

import org.junit.Assert.*
import org.junit.Test

class PhotoCropTest {
    @Test
    fun offsetSensorRegionIsPreservedWithoutZoom() {
        assertEquals(
            PixelRect(20, 30, 820, 630),
            PhotoCrop.calculate(
                1000,
                800,
                PixelRect(20, 30, 820, 630),
                PixelRect(0, 0, 1000, 800),
                1f,
                4f / 3,
            ),
        )
    }

    @Test
    fun zoomAppliedExactlyOnceInsideMeasuredValidArea() {
        assertEquals(
            PixelRect(260, 230, 660, 530),
            PhotoCrop.calculate(
                1000,
                800,
                PixelRect(20, 30, 920, 730),
                PixelRect(60, 80, 860, 680),
                2f,
                4f / 3,
            ),
        )
    }

    @Test
    fun wideOutputCentersLetterboxWithoutUpscaling() {
        assertEquals(
            PixelRect(0, 100, 800, 500),
            PhotoCrop.calculate(
                800,
                600,
                PixelRect(0, 0, 800, 600),
                PixelRect(0, 0, 800, 600),
                1f,
                2f,
            ),
        )
    }

    @Test
    fun invalidGeometryCannotSilentlyBecomeFullFrame() {
        assertThrows(IllegalArgumentException::class.java) {
            PhotoCrop.calculate(
                800,
                600,
                PixelRect(900, 0, 1000, 600),
                PixelRect(0, 0, 800, 600),
                1f,
                1f,
            )
        }
    }
}
