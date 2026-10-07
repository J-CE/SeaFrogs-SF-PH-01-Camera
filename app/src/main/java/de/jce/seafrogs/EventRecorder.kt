package de.jce.seafrogs

import android.content.Context
import android.net.Uri
import android.os.SystemClock
import org.json.JSONObject
import java.io.File
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Writes on one worker, never on the input callback. The queue has a hard limit.
 * If overloaded, the export explicitly reports lost records: it is not valid
 * evidence for a complete HID trace. UI text is independent of this file.
 */
class EventRecorder(private val context: Context, private val report: (String) -> Unit) {
    private val file = File(context.filesDir, "hid-${System.currentTimeMillis()}.jsonl")
    private val worker = ThreadPoolExecutor(
        1, 1, 0, TimeUnit.MILLISECONDS, ArrayBlockingQueue(8192)
    )
    private val dropped = AtomicInteger()
    private val failures = AtomicInteger()
    private var sequence = 0L
    private val writer = file.bufferedWriter()

    fun record(event: JSONObject) {
        event.put("sequence", ++sequence)
            .put("receivedWallTimeMs", System.currentTimeMillis())
            .put("receivedUptimeMs", SystemClock.uptimeMillis())
        val line = event.toString()
        try {
            worker.execute {
                try { writer.appendLine(line) }
                catch (error: Exception) {
                    if (failures.incrementAndGet() == 1) report("Protokollfehler: ${error.message}")
                }
            }
        } catch (_: java.util.concurrent.RejectedExecutionException) {
            dropped.incrementAndGet()
        }
    }

    /** All earlier writes finish before this snapshot; later input is not included. */
    fun export(uri: Uri, photos: List<Pair<String, String>> = emptyList(), testReport: String? = null,
               capabilityReport: String? = null) {
        val photoSnapshot = photos.toList()
        val summary = """
            SeaFrogs Kamera/HID Test 0.6.7
            Android-App-Ereignisse, keine rohen Bluetooth-HID-Reports.
            Datensatznummer am Export: $sequence
            Verlorene Datensätze durch Warteschlangenlimit: ${dropped.get()}
            Geräte und Markierungen stehen chronologisch in events.jsonl.
            Mapping für Einzelrichtungen anhand capture.zip/normal.zip; Kombinationen offen.
            Koordinaten im normalen Modus sind Positionen, keine Rohdeltas.
            Im captured-Modus sind X/Y relative Bewegungen.
        """.trimIndent()
        try {
            worker.execute {
                try {
                    writer.flush()
                    val output = context.contentResolver.openOutputStream(uri, "w")
                        ?: error("Speicherziel lässt sich nicht öffnen")
                    ZipOutputStream(output).use { zip ->
                        zip.putNextEntry(ZipEntry("events.jsonl"))
                        file.inputStream().use { it.copyTo(zip) }
                        zip.closeEntry()
                        val exportErrors = org.json.JSONArray()
                        for ((name, photoUri) in photoSnapshot) {
                            try {
                                val input = context.contentResolver.openInputStream(Uri.parse(photoUri))
                                    ?: error("Originalfoto nicht mehr verfügbar")
                                input.use {
                                    zip.putNextEntry(ZipEntry(name))
                                    it.copyTo(zip)
                                    zip.closeEntry()
                                }
                            } catch (error: Exception) {
                                exportErrors.put(JSONObject().put("file", name).put("error", error.message))
                            }
                        }
                        if (testReport != null) {
                            zip.putNextEntry(ZipEntry("camera-test-report.json"))
                            zip.write(testReport.toByteArray())
                            zip.closeEntry()
                        }
                        if (capabilityReport != null) {
                            zip.putNextEntry(ZipEntry("camera-capabilities.json"))
                            zip.write(capabilityReport.toByteArray())
                            zip.closeEntry()
                        }
                        zip.putNextEntry(ZipEntry("export-errors.json"))
                        zip.write(exportErrors.toString(2).toByteArray())
                        zip.closeEntry()
                        zip.putNextEntry(ZipEntry("summary.txt"))
                        zip.write((summary + "\nSchreibfehler: ${failures.get()}\n").toByteArray())
                        zip.closeEntry()
                    }
                    report("Export gespeichert. Originalfotos und mögliche Exportfehler stehen im ZIP.")
                } catch (error: Exception) { report("Exportfehler: ${error.message}") }
            }
        } catch (_: java.util.concurrent.RejectedExecutionException) {
            report("Exportwarteschlange voll. Nach kurzer Pause erneut exportieren.")
        }
    }

    fun close() {
        // Activity callbacks stop before this call. Closing follows queued writes.
        try { worker.execute { writer.close() } }
        catch (_: java.util.concurrent.RejectedExecutionException) {
            Thread {
                worker.shutdown()
                while (!worker.awaitTermination(1, TimeUnit.SECONDS)) { /* drain */ }
                writer.close()
            }.start()
        }
        worker.shutdown()
    }
}
