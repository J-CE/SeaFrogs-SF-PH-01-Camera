package de.jce.seafrogs

import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import org.json.JSONArray
import org.json.JSONObject

/** Snapshot events synchronously: Android recycles them after dispatch returns. */
object EventEncoder {
    fun motion(event: MotionEvent, route: String): JSONObject {
        val samples = JSONArray()
        for (history in 0 until event.historySize) {
            samples.put(sample(event, history))
        }
        samples.put(sample(event, null))
        return JSONObject().put("kind", "motion").put("route", route)
            .put("deviceId", event.deviceId).put("source", event.source)
            .put("action", event.action).put("actionName", MotionEvent.actionToString(event.action))
            .put("actionIndex", event.actionIndex).put("buttonState", event.buttonState)
            .put("actionButton", event.actionButton).put("metaState", event.metaState)
            .put("flags", event.flags).put("edgeFlags", event.edgeFlags)
            .put("downTimeMs", event.downTime).put("eventTimeMs", event.eventTime)
            .put("rawX", event.rawX).put("rawY", event.rawY)
            .put("xPrecision", event.xPrecision).put("yPrecision", event.yPrecision)
            .put("samples", samples)
    }

    private fun sample(event: MotionEvent, history: Int?): JSONObject {
        val pointers = JSONArray()
        for (pointer in 0 until event.pointerCount) {
            val axes = JSONObject()
            // Android reserves 0..63 for axes. Preserve all non-zero values and
            // explicit zeros for the main movement/scroll axes, without guessing HID.
            for (axis in 0..63) {
                val value = if (history == null) event.getAxisValue(axis, pointer)
                    else event.getHistoricalAxisValue(axis, pointer, history)
                if (value != 0f || axis in listOf(0, 1, 9, 10, 27, 28)) {
                    axes.put(MotionEvent.axisToString(axis), value.toDouble())
                }
            }
            pointers.put(JSONObject().put("id", event.getPointerId(pointer))
                .put("toolType", event.getToolType(pointer)).put("axes", axes))
        }
        return JSONObject().put("historical", history != null)
            .put("eventTimeMs", if (history == null) event.eventTime
                else event.getHistoricalEventTime(history)).put("pointers", pointers)
    }

    fun key(event: KeyEvent) = JSONObject().put("kind", "key")
        .put("deviceId", event.deviceId).put("source", event.source)
        .put("action", event.action).put("keyCode", event.keyCode)
        .put("keyName", KeyEvent.keyCodeToString(event.keyCode))
        .put("scanCode", event.scanCode).put("repeatCount", event.repeatCount)
        .put("metaState", event.metaState).put("flags", event.flags)
        .put("downTimeMs", event.downTime).put("eventTimeMs", event.eventTime)

    fun device(device: InputDevice): JSONObject {
        val ranges = JSONArray()
        device.motionRanges.forEach { range ->
            ranges.put(JSONObject().put("axis", MotionEvent.axisToString(range.axis))
                .put("source", range.source).put("min", range.min.toDouble())
                .put("max", range.max.toDouble()).put("flat", range.flat.toDouble())
                .put("fuzz", range.fuzz.toDouble()).put("resolution", range.resolution.toDouble()))
        }
        return JSONObject().put("id", device.id).put("name", device.name)
            .put("descriptor", device.descriptor).put("vendorId", device.vendorId)
            .put("productId", device.productId).put("sources", device.sources)
            .put("keyboardType", device.keyboardType).put("isVirtual", device.isVirtual)
            .put("motionRanges", ranges)
    }
}
