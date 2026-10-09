package de.jce.seafrogs

import org.junit.Assert.*
import org.junit.Test

class WhiteBalanceMathTest {
    private val identity = doubleArrayOf(1.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 1.0)

    @Test
    fun fullCorrectionNeutralizesTheModeledWhite() {
        val white = doubleArrayOf(0.25, 1.0, 2.0)
        val gains = WhiteBalanceMath.gains(identity, white, doubleArrayOf(1.0, 1.0, 1.0), 1.0)
        assertArrayEquals(doubleArrayOf(8.0, 2.0, 1.0), gains, 1e-10)
        val corrected = DoubleArray(3) { white[it] * gains[it] }
        assertArrayEquals(doubleArrayOf(2.0, 2.0, 2.0), corrected, 1e-10)
    }

    @Test
    fun strengthZeroUsesReferenceWhiteNotUnityGains() {
        val gains =
            WhiteBalanceMath.gains(
                identity,
                doubleArrayOf(0.25, 1.0, 2.0),
                doubleArrayOf(0.5, 1.0, 1.0),
                0.0,
            )
        assertArrayEquals(doubleArrayOf(2.0, 1.0, 1.0), gains, 1e-10)
    }

    @Test
    fun partialCorrectionInterpolatesRatiosInLogSpace() {
        val gains =
            WhiteBalanceMath.gains(
                identity,
                doubleArrayOf(0.25, 1.0, 1.0),
                doubleArrayOf(1.0, 1.0, 1.0),
                0.5,
            )
        assertArrayEquals(doubleArrayOf(2.0, 1.0, 1.0), gains, 1e-10)
    }

    @Test
    fun normalizedColorTransformKeepsGrayNeutral() {
        val matrix =
            WhiteBalanceMath.normalizeRows(
                doubleArrayOf(1.4, -0.2, -0.1, -0.1, 1.2, 0.1, 0.0, -0.1, 0.9)
            )
        assertArrayEquals(
            doubleArrayOf(1.0, 1.0, 1.0),
            WhiteBalanceMath.vector(matrix, doubleArrayOf(1.0, 1.0, 1.0)),
            1e-10,
        )
    }

    @Test
    fun halClampingAndMissingValuesAreNotAccepted() {
        assertFalse(
            WhiteBalanceMath.matches(doubleArrayOf(9.0, 1.0, 1.0), doubleArrayOf(4.0, 1.0, 1.0))
        )
        assertFalse(WhiteBalanceMath.matches(doubleArrayOf(2.0), doubleArrayOf(Double.NaN)))
        assertTrue(WhiteBalanceMath.matches(doubleArrayOf(2.0), doubleArrayOf(2.01)))
    }

    @Test(expected = IllegalArgumentException::class)
    fun invalidSensorResponseIsRejected() {
        WhiteBalanceMath.gains(
            identity,
            doubleArrayOf(-0.1, 1.0, 1.0),
            doubleArrayOf(1.0, 1.0, 1.0),
            1.0,
        )
    }
}
