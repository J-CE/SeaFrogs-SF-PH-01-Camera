package de.jce.seafrogs

import org.junit.Assert.*
import org.junit.Test

class ExposureVerificationTest {
    private val limits = ExposureLimits(400, 8_000_000, 100)

    @Test
    fun exactCeilingsAreConfirmed() {
        val r = ExposureVerification.check(limits, 400, 8_000_000, 0, 100, 100)
        assertTrue(r.exposureConfirmed)
        assertTrue(r.gainConfirmed)
        assertTrue(r.issues.isEmpty())
    }

    @Test
    fun missingAeModeDoesNotInventAnIsoViolation() {
        val r = ExposureVerification.check(limits, 200, 4_000_000, null, 100, 100)
        assertFalse(r.exposureConfirmed)
        assertEquals(listOf("AE-Modus fehlt"), r.issues)
    }

    @Test
    fun evenOneNanosecondAboveCeilingRemainsUnconfirmed() {
        val r = ExposureVerification.check(limits, 400, 8_000_001, 0, 100, 100)
        assertFalse(r.exposureConfirmed)
        assertEquals(listOf("Zeit über Grenze"), r.issues)
    }

    @Test
    fun isoAboveCapDoesNotPassAsQuantizationTolerance() {
        val r = ExposureVerification.check(limits, 401, 8_000_000, 0, 100, 100)
        assertFalse(r.exposureConfirmed)
        assertEquals(listOf("ISO über Grenze 400"), r.issues)
    }

    @Test
    fun gainViolationIsIndependentOfSensorExposure() {
        val r = ExposureVerification.check(limits, 400, 8_000_000, 0, 200, 100)
        assertTrue(r.exposureConfirmed)
        assertFalse(r.gainConfirmed)
    }

    @Test
    fun missingSensorValuesRemainUnconfirmed() {
        val r = ExposureVerification.check(limits, null, null, 0, 100, 100)
        assertFalse(r.exposureConfirmed)
        assertTrue(r.issues.contains("Sensor-ISO fehlt"))
        assertTrue(r.issues.contains("Sensorzeit fehlt"))
    }
}
