package de.jce.seafrogs

import android.hardware.camera2.CaptureResult
import android.hardware.camera2.params.ColorSpaceTransform
import android.hardware.camera2.params.RggbChannelVector
import org.json.JSONArray
import org.json.JSONObject

/** One measured/verified reference per sensor and test run. Not persisted:
 * reopening the app or starting another comparison must meter a new scene. */
data class FrozenCaptureSettings(val iso: Int, val timeNs: Long, val frameNs: Long,
    val boost: Int?, val focusDiopters: Float, val gains: RggbChannelVector,
    val transform: ColorSpaceTransform, val noiseReduction: Int?, val edge: Int?) {
    fun json() = JSONObject().put("iso", iso).put("timeNs", timeNs).put("frameNs", frameNs)
        .put("postRawBoost", boost ?: JSONObject.NULL).put("focusDiopters", focusDiopters.toDouble())
        .put("noiseReduction", noiseReduction ?: JSONObject.NULL).put("edge", edge ?: JSONObject.NULL)
        .put("wbGains", JSONArray((0..3).map { gains.getComponent(it).toDouble() }))
        .put("wbTransform", JSONArray((0..8).map { transform.getElement(it % 3, it / 3).toDouble() }))

    companion object {
        fun from(result: CaptureResult) = FrozenCaptureSettings(
            checkNotNull(result[CaptureResult.SENSOR_SENSITIVITY]) { "Referenz-ISO fehlt" },
            checkNotNull(result[CaptureResult.SENSOR_EXPOSURE_TIME]) { "Referenzzeit fehlt" },
            checkNotNull(result[CaptureResult.SENSOR_FRAME_DURATION]) { "Referenz-Framedauer fehlt" },
            result[CaptureResult.CONTROL_POST_RAW_SENSITIVITY_BOOST],
            checkNotNull(result[CaptureResult.LENS_FOCUS_DISTANCE]) { "Referenzfokus fehlt" },
            checkNotNull(result[CaptureResult.COLOR_CORRECTION_GAINS]) { "WB-Gains fehlen" },
            checkNotNull(result[CaptureResult.COLOR_CORRECTION_TRANSFORM]) { "WB-Matrix fehlt" },
            result[CaptureResult.NOISE_REDUCTION_MODE], result[CaptureResult.EDGE_MODE])
    }
}
