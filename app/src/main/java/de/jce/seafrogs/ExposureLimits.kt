package de.jce.seafrogs

import kotlin.math.ceil
import kotlin.math.ln
import kotlin.math.roundToLong

data class ExposureLimits(val isoCap: Int = 0, val longestTimeNs: Long = 33_333_333L) {
    val enabled get() = isoCap > 0
    companion object {
        val isoChoices = listOf(0, 400, 800, 1600)
        val timeChoices = listOf(33_333_333L, 16_666_666L, 8_000_000L)
    }
}

data class LimitedExposure(val iso: Int, val timeNs: Long, val brightnessDifferenceEv: Double)

/** The meter already includes the requested EV correction. Preserve its
 * sensitivity × time product where both limits permit it. If the shutter
 * ceiling requires higher ISO, raise ISO only as far as the configured cap.
 * No second application of EV and no silent relaxation of either ceiling.
 */
object ExposureLimitCalculator {
    fun calculate(meterIso: Int, meterTimeNs: Long, limits: ExposureLimits,
                  minIso: Int, maxIso: Int, minTimeNs: Long, maxTimeNs: Long): LimitedExposure {
        require(meterIso > 0 && meterTimeNs > 0 && limits.enabled)
        val upperIso = minOf(maxIso, limits.isoCap)
        val upperTime = minOf(maxTimeNs, limits.longestTimeNs)
        require(minIso > 0 && upperIso >= minIso && minTimeNs > 0 && upperTime >= minTimeNs)
        val targetProduct = meterIso.toDouble() * meterTimeNs
        val isoForShutter = ceil(targetProduct / upperTime).coerceAtMost(upperIso.toDouble()).toInt()
        val iso = maxOf(minOf(meterIso, upperIso), isoForShutter).coerceIn(minIso, upperIso)
        val time = (targetProduct / iso).roundToLong().coerceIn(minTimeNs, upperTime)
        return LimitedExposure(iso, time, differenceEv(meterIso, meterTimeNs, iso, time))
    }

    fun differenceEv(meterIso: Int, meterTimeNs: Long, actualIso: Int, actualTimeNs: Long): Double =
        ln(actualIso.toDouble() * actualTimeNs / (meterIso.toDouble() * meterTimeNs)) / ln(2.0)
}
