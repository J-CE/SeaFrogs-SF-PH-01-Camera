package de.jce.seafrogs

import android.content.Context
import android.net.Uri
import android.os.SystemClock
import java.io.File
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.json.JSONObject

/**
 * Writes on one worker, never on the input callback. The queue has a hard limit. If overloaded, the
 * export explicitly reports lost records: it is not valid evidence for a complete HID trace. UI
 * text is independent of this file.
 */
class EventRecorder(private val context: Context, private val report: (String) -> Unit) {
    private val eventLogFile = File(context.filesDir, "hid-${System.currentTimeMillis()}.jsonl")
    private val recordingExecutor =
        ThreadPoolExecutor(1, 1, 0, TimeUnit.MILLISECONDS, ArrayBlockingQueue(8192))
    private val droppedRecordCount = AtomicInteger()
    private val writeFailureCount = AtomicInteger()
    private var recordSequence = 0L
    private val writer = eventLogFile.bufferedWriter()

    /** Do not construct event snapshots when HID recording is disabled. */
    inline fun recordHidEvent(createEvent: () -> JSONObject) {
        if (HidLogging.ENABLED) record(createEvent())
    }

    fun record(event: JSONObject) {
        event
            .put("sequence", ++recordSequence)
            .put("receivedWallTimeMs", System.currentTimeMillis())
            .put("receivedUptimeMs", SystemClock.uptimeMillis())
        val line = event.toString()
        try {
            recordingExecutor.execute {
                try {
                    writer.appendLine(line)
                } catch (error: Exception) {
                    if (writeFailureCount.incrementAndGet() == 1)
                        report("Protokollfehler: ${error.message}")
                }
            }
        } catch (_: java.util.concurrent.RejectedExecutionException) {
            droppedRecordCount.incrementAndGet()
        }
    }

    /** All earlier writes finish before this snapshot; later input is not included. */
    fun export(
        uri: Uri,
        photos: List<Pair<String, String>> = emptyList(),
        testReport: String? = null,
        capabilityReport: String? = null,
        progress: (String, Boolean) -> Unit = { _, _ -> },
    ) {
        val photoSnapshot = photos.toList()
        progress("EXPORT: ZIP vorbereiten …", true)
        val summary =
            """
            SeaFrogs Kamera-Test ${BuildConfig.VERSION_NAME}
            Wir speichern Kamera- und Testereignisse. Die HID-Protokollierung ist deaktiviert.
            Datensatznummer am Export: $recordSequence
            Verlorene Datensätze durch Warteschlangenlimit: ${droppedRecordCount.get()}
            Wir erfassen keine HID-Gerätewechsel, Markierungen, Mausachsen oder HID-Befehle.
        """
                .trimIndent()
        try {
            recordingExecutor.execute {
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
                        eventLogFile.inputStream().use { it.copyTo(zip) }
                        zip.closeEntry()
                        for ((index, photo) in photoSnapshot.withIndex()) {
                            val (name, photoUri) = photo
                            progress(
                                "EXPORT: Packen ${index + 1}/${photoSnapshot.size}: $name",
                                true,
                            )
                            val input =
                                try {
                                    context.contentResolver.openInputStream(Uri.parse(photoUri))
                                        ?: error("Originalfoto nicht mehr verfügbar")
                                } catch (error: Exception) {
                                    exportErrors.put(
                                        JSONObject().put("file", name).put("error", error.message)
                                    )
                                    continue
                                }
                            // A copy/write failure aborts the archive instead of hiding damaged
                            // entries.
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
                        zip.write(
                            (summary + "\nSchreibfehler: ${writeFailureCount.get()}\n")
                                .toByteArray()
                        )
                        zip.closeEntry()
                    }
                    progress("EXPORT: ZIP prüfen …", true)
                    val bytes =
                        VerifiedExport.copy(
                            staged,
                            {
                                context.contentResolver.openOutputStream(uri, "wt")
                                    ?: error("Speicherziel lässt sich nicht öffnen")
                            },
                        ) { copied, total ->
                            progress(
                                "EXPORT: Kopieren ${copied * 100 / total}% (${copied / 1_048_576}/${total / 1_048_576} MiB)",
                                true,
                            )
                        }
                    val message =
                        "Export gespeichert: $bytes Byte." +
                            if (exportErrors.length() > 0)
                                " ${exportErrors.length()} Originaldateien fehlen, siehe export-errors.json."
                            else " ${photoSnapshot.size} Originaldateien enthalten."
                    progress(message, false)
                    report(message)
                } catch (error: Exception) {
                    val message =
                        "EXPORTFEHLER: ${error.javaClass.simpleName}: ${error.message}. Testdaten bleiben erhalten; Export erneut starten."
                    progress(message, false)
                    report(message)
                } finally {
                    archive?.delete()
                }
            }
        } catch (_: java.util.concurrent.RejectedExecutionException) {
            val message = "Exportwarteschlange voll. Nach kurzer Pause erneut exportieren."
            progress(message, false)
            report(message)
        }
    }

    fun close() {
        // Activity callbacks stop before this call. Closing follows queued writes.
        try {
            recordingExecutor.execute { writer.close() }
        } catch (_: java.util.concurrent.RejectedExecutionException) {
            Thread {
                    recordingExecutor.shutdown()
                    while (!recordingExecutor.awaitTermination(1, TimeUnit.SECONDS)) {
                        /* drain */
                    }
                    writer.close()
                }
                .start()
        }
        recordingExecutor.shutdown()
    }
}
