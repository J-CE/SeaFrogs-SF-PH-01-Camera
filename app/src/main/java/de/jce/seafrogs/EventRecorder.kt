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
    fun export(uri: Uri) {
        val summary = """
            SeaFrogs HID Diagnose 0.1.0
            Android-App-Ereignisse, keine rohen Bluetooth-HID-Reports.
            Datensatznummer am Export: $sequence
            Verlorene Datensätze durch Warteschlangenlimit: ${dropped.get()}
            Geräte und Markierungen stehen chronologisch in events.jsonl.
            Kein Mapping der Gehäusetasten bestätigt.
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
                        zip.putNextEntry(ZipEntry("summary.txt"))
                        zip.write((summary + "\nSchreibfehler: ${failures.get()}\n").toByteArray())
                        zip.closeEntry()
                    }
                    report("Export gespeichert")
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
