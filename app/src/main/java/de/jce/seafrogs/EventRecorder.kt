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
               capabilityReport: String? = null, progress: (String, Boolean) -> Unit = { _, _ -> }) {
        val photoSnapshot = photos.toList()
        progress("EXPORT: ZIP vorbereiten …", true)
        val summary = """
            SeaFrogs Kamera/HID Test 0.7.0
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
                var archive: File? = null
                try {
                    writer.flush()
                    val staged = File.createTempFile("seafrogs-export-", ".zip", context.cacheDir)
                    archive = staged
                    val exportErrors = org.json.JSONArray()
                    ZipOutputStream(staged.outputStream().buffered(128 * 1024)).use { zip ->
                        // JPEG/DNG are already compressed. Avoid minutes of redundant deflation.
                        zip.setLevel(java.util.zip.Deflater.NO_COMPRESSION)
                        zip.putNextEntry(ZipEntry("events.jsonl"))
                        file.inputStream().use { it.copyTo(zip) }
                        zip.closeEntry()
                        for ((index, photo) in photoSnapshot.withIndex()) {
                            val (name, photoUri) = photo
                            progress("EXPORT: Packen ${index + 1}/${photoSnapshot.size}: $name", true)
                            val input = try {
                                context.contentResolver.openInputStream(Uri.parse(photoUri))
                                    ?: error("Originalfoto nicht mehr verfügbar")
                            } catch (error: Exception) {
                                exportErrors.put(JSONObject().put("file", name).put("error", error.message))
                                continue
                            }
                            // A copy/write failure aborts the archive instead of hiding damaged entries.
                            input.use {
                                zip.putNextEntry(ZipEntry(name))
                                it.copyTo(zip, 128 * 1024)
                                zip.closeEntry()
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
                    progress("EXPORT: ZIP prüfen …", true)
                    val bytes = VerifiedExport.copy(staged, {
                        context.contentResolver.openOutputStream(uri, "wt")
                            ?: error("Speicherziel lässt sich nicht öffnen")
                    }) { copied, total ->
                        progress("EXPORT: Kopieren ${copied * 100 / total}% (${copied / 1_048_576}/${total / 1_048_576} MiB)", true)
                    }
                    val message = "Export gespeichert: $bytes Byte." +
                        if (exportErrors.length() > 0) " ${exportErrors.length()} Originaldateien fehlen, siehe export-errors.json." else " ${photoSnapshot.size} Originaldateien enthalten."
                    progress(message, false)
                    report(message)
                } catch (error: Exception) {
                    val message = "EXPORTFEHLER: ${error.javaClass.simpleName}: ${error.message}. Testdaten bleiben erhalten; Export erneut starten."
                    progress(message, false)
                    report(message)
                } finally { archive?.delete() }
            }
        } catch (_: java.util.concurrent.RejectedExecutionException) {
            val message = "Exportwarteschlange voll. Nach kurzer Pause erneut exportieren."
            progress(message, false)
            report(message)
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
