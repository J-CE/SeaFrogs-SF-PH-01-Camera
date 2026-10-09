package de.jce.seafrogs

import org.junit.Assert.*
import org.junit.Test

class DigitalGainLimitTest {
    @Test
    fun autoKeepsMeasuredGain() {
        assertEquals(318, DigitalGainLimit.select(318, 0, 100, 1600))
    }

    @Test
    fun ceilingActuallyClamps() {
        assertEquals(100, DigitalGainLimit.select(318, 100, 100, 1600))
    }

    @Test
    fun ceilingDoesNotRaiseLowerGain() {
        assertEquals(150, DigitalGainLimit.select(150, 200, 100, 1600))
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsUnachievableCeiling() {
        DigitalGainLimit.select(318, 100, 200, 1600)
    }

    @Test
    fun digitalOnlyDoesNotIntroduceSensorOrTimeCeiling() {
        val value =
            ExposureLimitCalculator.calculate(
                667,
                50_000_000L,
                ExposureLimits(boostCap = 100),
                50,
                6400,
                1_000L,
                1_000_000_000L,
            )
        assertEquals(667, value.iso)
        assertEquals(50_000_000L, value.timeNs)
    }
}
