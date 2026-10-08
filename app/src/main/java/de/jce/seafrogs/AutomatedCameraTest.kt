package de.jce.seafrogs

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import org.json.JSONArray
import org.json.JSONObject

/** Main-thread coordinator. Every delayed callback carries a run token.
 * A finished exposure is never inferred from UI text. The controller reports
 * saved JPEGs only after EXIF processing; cancellation preserves completed files.
 */
class AutomatedCameraTest(context: Context, private val controller: PhotoCameraController,
    private val record: (JSONObject) -> Unit, private val display: (String, Boolean) -> Unit) {
    private val preferences = context.applicationContext.getSharedPreferences("autoTests", Context.MODE_PRIVATE)
    private val handler = Handler(Looper.getMainLooper())
    private var token = 0
    private var steps = emptyList<AutoTestStep>()
    private var index = 0
    private var deadline = 0L
    private var settledAt = 0L
    private var group = ""
    private var runId = ""
    private var preCaptureFocus = "NOT_CHECKED"
    var running = false
        private set
    private val results = runCatching { JSONArray(preferences.getString("results", "[]")) }.getOrDefault(JSONArray())

    fun start(macro: Boolean, raw: Boolean = false, iso: Boolean = false, processing: Boolean = false, libraries: Boolean = false, fusion: Boolean = false) {
        if (running || !controller.readyForCommand()) { display("Kamera noch nicht bereit", false); return }
        token++
        running = true
        group = if (fusion) "FUSION" else if (libraries) "LIBRARY" else if (processing) "QUALITY" else if (iso) "ISO" else if (raw) "RAW" else if (macro) "MACRO" else "NORMAL"
        runId = System.currentTimeMillis().toString()
        // Replace only the previous run of this group. Keep the other group for
        // the joint ZIP; JPEGs remain in MediaStore and are never deleted here.
        for (i in results.length() - 1 downTo 0) if (results.getJSONObject(i).optString("group") == group) results.remove(i)
        persist()
        steps = if (fusion) AutoTestPlan.fusion() else if (libraries) AutoTestPlan.libraries() else if (processing) AutoTestPlan.processing() else if (iso) AutoTestPlan.iso(controller.savedExposureLimits().longestTimeNs) else if (raw) AutoTestPlan.raw() else AutoTestPlan.create(macro)
        index = 0
        record(JSONObject().put("kind", "autoTestStart").put("group", group).put("runId", runId))
        next(token)
    }

    private fun next(run: Int) {
        if (!running || token != run) return
        if (index >= steps.size) {
            running = false
            controller.endAutoTest()
            record(JSONObject().put("kind", "autoTestFinished").put("group", group).put("runId", runId))
            display("$group fertig. ${if (group == "LIBRARY") "BIB ZIP" else "TEST ZIP"} enthält Fotos und Bericht.", false)
            return
        }
        val step = steps[index]
        preCaptureFocus = "NOT_CHECKED"
        display("$group ${index + 1}/${steps.size}: ${step.id}", true)
        record(JSONObject().put("kind", "autoTestStep").put("id", step.id).put("runId", runId))
        if ((!step.fusion && step.quality !in controller.autoTestQualities(step.lens)) || !controller.configureAutoTest(step)) {
            save(step, null, "SKIPPED", "Einstellung/Kameraroute nicht unterstützt, Vergleichsreferenz fehlt oder Extension physisch nicht sicher zuordenbar")
            index++
            handler.post { next(run) }
            return
        }
        deadline = SystemClock.uptimeMillis() + 30000
        settledAt = 0
        handler.postDelayed({ awaitReady(run, step) }, 250)
    }

    private fun awaitReady(run: Int, step: AutoTestStep) {
        if (!running || token != run) return
        val now = SystemClock.uptimeMillis()
        if (!controller.autoTestMatches(step)) {
            settledAt = 0
            if (now >= deadline) {
                save(step, null, "FAILED", "Kamera/Einstellung nach 30 Sekunden nicht bereit")
                index++; next(run); return
            }
            handler.postDelayed({ awaitReady(run, step) }, 250)
            return
        }
        if (settledAt == 0L) settledAt = now
        if (now - settledAt < 3000) {
            handler.postDelayed({ awaitReady(run, step) }, 250)
            return
        }
        preCaptureFocus = controller.autoTestFocusStatus()
        if (preCaptureFocus == "WAITING_FOR_PREVIEW_AF") {
            if (now - settledAt >= 11000) {
                save(step, null, "FAILED", "Kein stabiler Vorschau-AF innerhalb von 8 Sekunden nach Beruhigungszeit; keine Aufnahme")
                index++; next(run); return
            }
            display("$group ${index + 1}/${steps.size}: Warte auf stabilen Fokus", true)
            handler.postDelayed({ awaitReady(run, step) }, 250)
            return
        }
        record(JSONObject().put("kind", "autoTestFocusBeforeCapture").put("id", step.id)
            .put("runId", runId).put("focusEvidence", preCaptureFocus))
        display("$group ${index + 1}/${steps.size}: Aufnahme ${step.id}", true)
        // Night processing may take substantially longer than standard JPEG.
        handler.postDelayed({
            if (running && token == run) {
                save(step, null, "FAILED", "Aufnahme nach 90 Sekunden nicht beendet")
                cancel("Aufnahme-Timeout. Erneut starten, wenn die Kamera wieder bereit ist.")
            }
        }, 90000)
        controller.capturePhoto { uri, error ->
            if (!running || token != run) return@capturePhoto
            val rawOutcome = controller.rawOutcome()
            val status = when {
                uri == null || (step.raw && rawOutcome?.dng == null) ||
                    ((step.raw || step.nativeCapture) && rawOutcome?.error != null) ||
                    (step.frameCount > 1 && rawOutcome?.frames?.size != step.frameCount) -> "FAILED"
                error != null -> "EXIF_WARNING"
                else -> "SAVED"
            }
            save(step, uri?.toString(), status, error)
            // Invalidates this step's timeout without invalidating the run.
            handler.removeCallbacksAndMessages(null)
            index++
            handler.post { next(run) }
        }
    }

    private fun save(step: AutoTestStep, uri: String?, status: String, error: String?) {
        val value = JSONObject().put("group", group).put("runId", runId).put("id", step.id)
            .put("lens", step.lens.name).put("zoomRequested", step.zoom.toDouble())
            .put("evRequested", step.ev.toDouble()).put("qualityRequested", step.quality)
            .put("frameCountRequested", step.frameCount)
            .put("processingVariantRequested", step.processing.name)
            .put("comparisonType", if (step.processing != ProcessingVariant.NONE) "LOCKED_SENSOR_COMPARISON" else if (group == "QUALITY" || group == "LIBRARY") "INDEPENDENT_EXTENSION_REFERENCE" else "OTHER")
            .put("preCaptureFocus", preCaptureFocus)
            .put("isoCapRequested", step.isoCap).put("longestTimeNsRequested", if (step.isoCap > 0) step.longestTimeNs else JSONObject.NULL)
            .put("formatRequested", if (step.raw) "RAW+JPEG" else "JPEG")
            .put("status", status).put("uri", uri ?: JSONObject.NULL).put("error", error ?: JSONObject.NULL)
        if ((step.raw || step.nativeCapture) && uri != null) controller.rawOutcome()?.let { outcome ->
            if (step.frameCount > 1 && !step.fusion) value.put("seriesFrames", JSONArray().apply {
                outcome.frames.forEachIndexed { index, frame -> put(JSONObject()
                    .put("index", index).put("jpegUri", frame.jpeg.toString())
                    .put("dngUri", frame.dng?.toString() ?: JSONObject.NULL)
                    .put("capture", JSONObject(frame.evidence))) }
            }).put("frameCountSaved", outcome.frames.size)
            value.put("fallbackJpegUri", JSONObject(outcome.evidence).optString("fallbackJpegUri", outcome.jpeg?.toString() ?: ""))
                .put("dngUri", outcome.dng?.toString() ?: JSONObject.NULL)
                .put(if (step.raw) "rawCapture" else "sensorCapture", JSONObject(outcome.evidence))
        }
        results.put(value)
        persist()
        record(JSONObject(value.toString()).put("kind", "autoTestResult"))
    }

    fun cancel(reason: String = "Test abgebrochen. Gespeicherte Fotos bleiben erhalten.") {
        if (!running) return
        running = false
        controller.endAutoTest()
        token++
        handler.removeCallbacksAndMessages(null)
        record(JSONObject().put("kind", "autoTestCancelled").put("reason", reason).put("runId", runId))
        display(reason, false)
    }

    fun report(groupOnly: String? = null): String = JSONObject().put("version", "0.8.8-ui-clean")
        .put("device", android.os.Build.MODEL).put("androidBuild", android.os.Build.FINGERPRINT)
        .put("note", "HAL reference JPEGs and processed MEHRBILD when requested; actual capture settings and fusion outcome in report/EXIF. No automated sharpness score.")
        .put("results", selectedResults(groupOnly)).toString(2)

    fun photos(groupOnly: String? = null): List<Pair<String, String>> = (0 until results.length()).flatMap { i ->
        val value = results.getJSONObject(i)
        if (groupOnly != null && value.optString("group") != groupOnly) return@flatMap emptyList()
        if (value.optString("group") == "FUSION") return@flatMap listOf("uri" to "mehrbild", "fallbackJpegUri" to "standard").mapNotNull { (key,label) ->
            value.optString(key).takeIf { it.isNotBlank() && it != "null" }?.let {
                "photos/${value.getString("id")}_$label.jpg" to it
            }
        }.distinctBy { it.second }
        val series = value.optJSONArray("seriesFrames")
        if (series != null) return@flatMap (0 until series.length()).flatMap { index ->
            val frame = series.getJSONObject(index)
            listOf("jpegUri" to "jpg", "dngUri" to "dng").mapNotNull { (key, suffix) ->
                frame.optString(key).takeIf { it.isNotBlank() && it != "null" }?.let {
                    "photos/${value.getString("id")}/frame_${index.toString().padStart(2, '0')}.$suffix" to it
                }
            }
        }
        listOf("uri" to "jpg", "dngUri" to "dng").mapNotNull { (key, suffix) ->
            val uri = value.optString(key)
            if (uri.isBlank() || uri == "null") null else "photos/${value.getString("id")}.$suffix" to uri
        }
    }

    private fun selectedResults(groupOnly: String?) = JSONArray().apply {
        for (i in 0 until results.length()) {
            val value = results.getJSONObject(i)
            if (groupOnly == null || value.optString("group") == groupOnly) put(value)
        }
    }

    private fun persist() { preferences.edit().putString("results", results.toString()).apply() }
}
