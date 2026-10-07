package de.jce.seafrogs

import java.io.File
import java.io.OutputStream
import java.util.zip.CRC32
import java.util.zip.ZipFile

/** Validate a closed local archive before opening/truncating the selected destination. */
object VerifiedExport {
    fun copy(archive: File, openDestination: () -> OutputStream,
             progress: (Long, Long) -> Unit = { _, _ -> }): Long {
        require(archive.length() > 0) { "Internes ZIP ist leer" }
        ZipFile(archive).use { zip ->
            require(zip.size() > 0) { "Internes ZIP enthält keine Dateien" }
            val names = mutableSetOf<String>()
            val entries = zip.entries()
            val buffer = ByteArray(128 * 1024)
            while (entries.hasMoreElements()) {
                val entry = entries.nextElement()
                require(names.add(entry.name)) { "Doppelter ZIP-Eintrag: ${entry.name}" }
                val crc = CRC32()
                var count = 0L
                zip.getInputStream(entry).use { input ->
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        crc.update(buffer, 0, read)
                        count += read
                    }
                }
                check(count == entry.size && crc.value == entry.crc) { "ZIP-Prüfung fehlgeschlagen: ${entry.name}" }
            }
        }
        val expected = archive.length()
        var copied = 0L
        var notified = 0L
        progress(0, expected)
        openDestination().use { output ->
            archive.inputStream().buffered(128 * 1024).use { input ->
                val buffer = ByteArray(128 * 1024)
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    output.write(buffer, 0, read)
                    copied += read
                    if (copied < expected && copied - notified >= 4 * 1024 * 1024) {
                        progress(copied, expected)
                        notified = copied
                    }
                }
            }
            output.flush()
            check(copied == expected) { "Unvollständiger Export: $copied / $expected Byte" }
        }
        // Success follows destination close, including provider close errors.
        progress(copied, expected)
        return copied
    }
}
