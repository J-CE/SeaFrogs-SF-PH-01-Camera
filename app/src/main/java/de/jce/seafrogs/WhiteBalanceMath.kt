package de.jce.seafrogs

import kotlin.math.pow

/** Linear sensor-space approximation; not a spectral calibration of seawater or LED. */
object WhiteBalanceMath {
    fun multiply(a: DoubleArray, b: DoubleArray): DoubleArray {
        require(a.size == 9 && b.size == 9)
        return DoubleArray(9) { n -> (0..2).sumOf { a[n / 3 * 3 + it] * b[it * 3 + n % 3] } }
    }

    fun vector(a: DoubleArray, b: DoubleArray) =
        DoubleArray(3) { row -> (0..2).sumOf { a[row * 3 + it] * b[it] } }

    fun gains(
        sensorMatrix: DoubleArray,
        white: DoubleArray,
        base: DoubleArray,
        strength: Double,
    ): DoubleArray {
        require(strength in 0.0..1.0)
        val neutral = vector(sensorMatrix, white)
        val reference = vector(sensorMatrix, base)
        require(
            neutral.all { it.isFinite() && it > 0 } && reference.all { it.isFinite() && it > 0 }
        ) {
            "Weißpunkt außerhalb der Sensorkalibrierung"
        }
        val values =
            DoubleArray(3) { (1 / reference[it]) * (reference[it] / neutral[it]).pow(strength) }
        val min = values.min()
        return values.map { it / min }.toDoubleArray()
    }

    fun normalizeRows(matrix: DoubleArray): DoubleArray {
        require(matrix.size == 9 && matrix.all(Double::isFinite))
        return DoubleArray(9) { n ->
            val sum = (0..2).sumOf { matrix[n / 3 * 3 + it] }
            require(sum > 0.000001)
            matrix[n] / sum
        }
    }

    fun matches(requested: DoubleArray, actual: DoubleArray): Boolean =
        requested.size == actual.size &&
            requested.indices.all {
                actual[it].isFinite() &&
                    kotlin.math.abs(requested[it] - actual[it]) <=
                        maxOf(0.03, kotlin.math.abs(requested[it]) * 0.02)
            }
}
