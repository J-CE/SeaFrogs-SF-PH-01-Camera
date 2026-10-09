package de.jce.seafrogs

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.CaptureResult
import android.hardware.camera2.params.ColorSpaceTransform
import android.hardware.camera2.params.RggbChannelVector
import android.util.Rational
import org.json.JSONObject

/** Each route uses its own reported D65 color/calibration/forward matrices. */
data class ManualWhiteBalance(
    val gains: RggbChannelVector,
    val transform: ColorSpaceTransform,
    val profile: String,
    val strength: Int,
) {
    fun json() =
        JSONObject()
            .put("profile", profile)
            .put("strengthPercent", strength)
            .put("gains", org.json.JSONArray((0..3).map { gains.getComponent(it).toDouble() }))
            .put(
                "transform",
                org.json.JSONArray((0..8).map { transform.getElement(it % 3, it / 3).toDouble() }),
            )

    fun verification(result: CaptureResult?): String {
        val actual =
            result?.get(CaptureResult.COLOR_CORRECTION_GAINS) ?: return "WB NICHT BESTÄTIGT"
        val matrix =
            result.get(CaptureResult.COLOR_CORRECTION_TRANSFORM) ?: return "WB NICHT BESTÄTIGT"
        val equal =
            WhiteBalanceMath.matches(
                DoubleArray(4) { gains.getComponent(it).toDouble() },
                DoubleArray(4) { actual.getComponent(it).toDouble() },
            ) &&
                WhiteBalanceMath.matches(
                    DoubleArray(9) { transform.getElement(it % 3, it / 3).toDouble() },
                    DoubleArray(9) { matrix.getElement(it % 3, it / 3).toDouble() },
                ) &&
                result[CaptureResult.CONTROL_AWB_MODE] == CaptureRequest.CONTROL_AWB_MODE_OFF
        return if (equal) "WB ANGEWENDET" else "WB ABWEICHEND / LIMITIERT"
    }

    companion object {
        val names =
            listOf(
                "Unterwasser flach · 0–8 m",
                "Unterwasser mittel · >8–20 m",
                "Unterwasser tief · >20 m",
                "Videolicht DL08 · 5000 K",
            )
        val keys = listOf("SHALLOW", "MEDIUM", "DEEP", "DL08")

        fun build(
            context: Context,
            cameraCharacteristics: CameraCharacteristics,
            mode: Int,
            strength: Int,
        ): ManualWhiteBalance {
            require(mode in 100..103 && strength in 0..100)
            check(
                cameraCharacteristics[CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES]
                    ?.contains(
                        CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_MANUAL_POST_PROCESSING
                    ) == true
            ) {
                "Manueller WB nicht unterstützt"
            }
            check(
                cameraCharacteristics[CameraCharacteristics.CONTROL_AWB_AVAILABLE_MODES]?.contains(
                    CaptureRequest.CONTROL_AWB_MODE_OFF
                ) == true
            ) {
                "AWB OFF nicht unterstützt"
            }
            val usesSecondD65Illuminant =
                cameraCharacteristics[CameraCharacteristics.SENSOR_REFERENCE_ILLUMINANT2]
                    ?.toInt() == 21
            check(
                usesSecondD65Illuminant ||
                    cameraCharacteristics[CameraCharacteristics.SENSOR_REFERENCE_ILLUMINANT1]
                        ?.toInt() == 21
            ) {
                "D65-Sensorkalibrierung fehlt"
            }
            fun matrix(key: CameraCharacteristics.Key<ColorSpaceTransform>): DoubleArray {
                val calibrationTransform =
                    checkNotNull(cameraCharacteristics[key]) { "WB-Kalibrierungsmatrix fehlt" }
                return DoubleArray(9) { calibrationTransform.getElement(it % 3, it / 3).toDouble() }
            }
            val color =
                matrix(
                    if (usesSecondD65Illuminant) CameraCharacteristics.SENSOR_COLOR_TRANSFORM2
                    else CameraCharacteristics.SENSOR_COLOR_TRANSFORM1
                )
            val calibration =
                matrix(
                    if (usesSecondD65Illuminant) CameraCharacteristics.SENSOR_CALIBRATION_TRANSFORM2
                    else CameraCharacteristics.SENSOR_CALIBRATION_TRANSFORM1
                )
            val forward =
                matrix(
                    if (usesSecondD65Illuminant) CameraCharacteristics.SENSOR_FORWARD_MATRIX2
                    else CameraCharacteristics.SENSOR_FORWARD_MATRIX1
                )
            val data =
                JSONObject(
                    context.assets.open("wb/whitepoints.json").bufferedReader().use {
                        it.readText()
                    }
                )
            fun xyz(key: String): DoubleArray {
                val xyzComponents =
                    if (key == "base") data.getJSONArray(key)
                    else data.getJSONObject("profiles").getJSONArray(key)
                return DoubleArray(3) { xyzComponents.getDouble(it) }
            }
            val key = keys[mode - 100]
            val bayerGains =
                WhiteBalanceMath.gains(
                    WhiteBalanceMath.multiply(calibration, color),
                    xyz(key),
                    xyz("base"),
                    if (mode == 103) 1.0 else strength / 100.0,
                )
            // Forward matrix consumes white-balanced reference camera colors. Map
            // actual to reference after balancing, then D50 XYZ to linear sRGB D65.
            val calibrationCoefficients =
                doubleArrayOf(
                    calibration[0],
                    calibration[1],
                    calibration[2],
                    calibration[3],
                    calibration[4],
                    calibration[5],
                    calibration[6],
                    calibration[7],
                    calibration[8],
                )
            check(
                calibrationCoefficients.indices.all {
                    it in listOf(0, 4, 8) || kotlin.math.abs(calibrationCoefficients[it]) < 1e-6
                }
            ) {
                "Nichtdiagonale Sensorkalibrierung benötigt erweitertes WB-Modell"
            }
            val reference =
                doubleArrayOf(
                    1 / calibrationCoefficients[0],
                    0.0,
                    0.0,
                    0.0,
                    1 / calibrationCoefficients[4],
                    0.0,
                    0.0,
                    0.0,
                    1 / calibrationCoefficients[8],
                )
            val d50ToD65 =
                doubleArrayOf(
                    0.9555766,
                    -0.0230393,
                    0.0631636,
                    -0.0282895,
                    1.0099416,
                    0.0210077,
                    0.0122982,
                    -0.0204830,
                    1.3299098,
                )
            val xyzToSrgb =
                doubleArrayOf(
                    3.2404542,
                    -1.5371385,
                    -0.4985314,
                    -0.9692660,
                    1.8760108,
                    0.0415560,
                    0.0556434,
                    -0.2040259,
                    1.0572252,
                )
            val transform =
                WhiteBalanceMath.normalizeRows(
                    WhiteBalanceMath.multiply(
                        WhiteBalanceMath.multiply(xyzToSrgb, d50ToD65),
                        WhiteBalanceMath.multiply(forward, reference),
                    )
                )
            check(transform.all { it in -1.5..3.0 }) {
                "WB-Matrix außerhalb des garantierten Camera2-Bereichs"
            }
            return ManualWhiteBalance(
                RggbChannelVector(
                    bayerGains[0].toFloat(),
                    bayerGains[1].toFloat(),
                    bayerGains[1].toFloat(),
                    bayerGains[2].toFloat(),
                ),
                ColorSpaceTransform(
                    Array(9) { Rational((transform[it] * 1000000).toInt(), 1000000) }
                ),
                key,
                if (mode == 103) 100 else strength,
            )
        }
    }
}
