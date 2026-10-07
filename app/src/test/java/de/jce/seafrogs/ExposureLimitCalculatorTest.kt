package de.jce.seafrogs

import org.junit.Assert.*
import org.junit.Test

class ExposureLimitCalculatorTest {
    private fun calculate(iso: Int, time: Long, cap: Int, ceiling: Long) =
        ExposureLimitCalculator.calculate(iso, time, ExposureLimits(cap, ceiling), 48, 10133, 1000L, 1_000_000_000L)

    @Test fun loweringIsoPreservesBrightnessWhenShutterHasRoom() {
        val result = calculate(800, 10_000_000L, 400, 33_333_333L)
        assertEquals(400, result.iso)
        assertEquals(20_000_000L, result.timeNs)
        assertEquals(0.0, result.brightnessDifferenceEv, 0.000001)
    }

    @Test fun twoCeilingsProduceExplicitBrightnessDeficit() {
        val result = calculate(762, 39_998_667L, 400, 33_333_333L)
        assertEquals(400, result.iso)
        assertEquals(33_333_333L, result.timeNs)
        assertTrue(result.brightnessDifferenceEv < -1.1)
    }

    @Test fun shutterCeilingRaisesIsoWithinCapToPreserveBrightness() {
        val result = calculate(100, 100_000_000L, 400, 33_333_333L)
        assertEquals(301, result.iso)
        assertTrue(result.timeNs <= 33_333_333L)
        assertEquals(0.0, result.brightnessDifferenceEv, 0.000001)
    }

    @Test fun brightSceneKeepsMeteredIsoAndTime() {
        val result = calculate(100, 2_000_000L, 400, 8_000_000L)
        assertEquals(100, result.iso)
        assertEquals(2_000_000L, result.timeNs)
    }

    @Test(expected = IllegalArgumentException::class)
    fun impossibleSensorRangeFailsInsteadOfRelaxingUserLimit() {
        ExposureLimitCalculator.calculate(200, 10_000_000L, ExposureLimits(400), 800, 1600, 1000L, 1_000_000_000L)
    }
}
