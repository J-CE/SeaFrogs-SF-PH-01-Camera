package de.jce.seafrogs

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CameraControlCyclesTest {
    @Test fun normalZoomWrapsAndSkipsUnsupportedSteps() {
        assertEquals(1.5f, CameraControlCycles.nextZoom(1f, false, 1f, 3f))
        assertEquals(1f, CameraControlCycles.nextZoom(3f, false, 1f, 3f))
        assertEquals(1f, CameraControlCycles.nextZoom(2f, false, 1f, 2f))
        assertEquals(5f, CameraControlCycles.nextZoom(3f, false, 1f, 5f))
        assertEquals(1f, CameraControlCycles.nextZoom(5f, false, 1f, 5f))
        assertNull(CameraControlCycles.nextZoom(1f, false, 6f, 8f))
    }
    @Test fun macroUsesSensorRelativeFactors() {
        assertEquals(2f, CameraControlCycles.nextZoom(1f, true, 1f, 4f))
        assertEquals(1f, CameraControlCycles.nextZoom(2f, true, 1f, 4f))
        assertEquals(1f, CameraControlCycles.nextZoom(4f, true, 1f, 4f))
    }
    @Test fun thirdEvStepsPreserveNegativeAndPositiveCycleOrder() {
        val step = 1f / 3f
        var index = 0
        listOf(3, 6, -3, -6, 0).forEach { expected ->
            index = CameraControlCycles.nextExposure(index, step, -6, 6)
            assertEquals(expected, index)
        }
    }
    @Test fun narrowEvRangeDeduplicatesClampedTargets() {
        assertEquals(listOf(0, 1, -1, 0), buildList {
            var index = 0
            add(index)
            repeat(3) {
                index = CameraControlCycles.nextExposure(index, 1f, -1, 1)
                add(index)
            }
        })
        assertEquals(0, CameraControlCycles.nextExposure(0, 1f, 0, 0))
    }
}
