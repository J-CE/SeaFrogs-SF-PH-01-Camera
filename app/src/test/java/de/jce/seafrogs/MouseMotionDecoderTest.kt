package de.jce.seafrogs

import org.junit.Assert.*
import org.junit.Test

class MouseMotionDecoderTest {
    @Test
    fun relativeHistoryPreservesBothChordDirectionsAtCursorEdge() {
        val decoder = MouseMotionDecoder()
        val gate = HidCommandGate()
        // Android batches a horizontal delta before the final vertical delta.
        val history = decoder.sample(0f, 0f, -16f, 0f)
        val current = decoder.sample(0f, 0f, 0f, -16f)
        gate.movement(history.first, history.second, 10)
        gate.movement(current.first, current.second, 10)
        assertEquals(HidCommandGate.ModeSwitch.CAMERA, gate.takeModeSwitch())
    }

    @Test
    fun positionsUseScreenCoordinatesAcrossTargets() {
        val decoder = MouseMotionDecoder()
        decoder.position(100f, 100f)
        assertEquals(-16f to 0f, decoder.sample(84f, 100f, 0f, 0f))
        assertEquals(0f to -16f, decoder.sample(84f, 84f, 0f, 0f))
    }

    @Test
    fun relativeAndPositionReportsKeepOneBaseline() {
        val decoder = MouseMotionDecoder()
        assertEquals(15f to 0f, decoder.sample(200f, 100f, 15f, 0f))
        assertEquals(0f to 15f, decoder.sample(200f, 115f, 0f, 0f))
        decoder.reset()
        assertEquals(0f to 0f, decoder.sample(10f, 20f, 0f, 0f))
    }
}
