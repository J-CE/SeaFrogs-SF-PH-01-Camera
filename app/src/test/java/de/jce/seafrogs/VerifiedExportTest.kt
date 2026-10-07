package de.jce.seafrogs

import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class VerifiedExportTest {
    private fun archive(): File = File.createTempFile("export-test-", ".zip").apply {
        ZipOutputStream(outputStream()).use { zip ->
            zip.putNextEntry(ZipEntry("camera-test-report.json"))
            zip.write("{\"results\":[]}".toByteArray())
            zip.closeEntry()
        }
    }

    @Test fun destinationContainsExactlyTheValidatedArchiveAndProgressEndsAfterClose() {
        val source = archive()
        val destination = ByteArrayOutputStream()
        var closed = false
        try {
            val bytes = VerifiedExport.copy(source, { object : OutputStream() {
                override fun write(b: Int) { destination.write(b) }
                override fun write(b: ByteArray, off: Int, len: Int) { destination.write(b, off, len) }
                override fun close() { closed = true }
            } }) { copied, total -> if (copied == total) assertTrue(closed) }
            assertEquals(source.length(), bytes)
            assertArrayEquals(source.readBytes(), destination.toByteArray())
        } finally { source.delete() }
    }

    @Test fun invalidArchiveNeverOpensOrTruncatesDestination() {
        val source = File.createTempFile("invalid-export-", ".zip")
        var opened = false
        try {
            source.writeText("broken zip")
            try {
                VerifiedExport.copy(source, { opened = true; ByteArrayOutputStream() })
                fail("Invalid archive accepted")
            } catch (_: IOException) { }
            assertFalse(opened)
        } finally { source.delete() }
    }

    @Test fun destinationCloseFailureNeverSignalsSuccessfulCompletion() {
        val source = archive()
        var completed = false
        try {
            try {
                VerifiedExport.copy(source, { object : ByteArrayOutputStream() {
                    override fun close() { throw IOException("provider close failed") }
                } }) { copied, total -> if (copied == total) completed = true }
                fail("Provider error ignored")
            } catch (error: IOException) { assertEquals("provider close failed", error.message) }
            assertFalse(completed)
        } finally { source.delete() }
    }
    @Test fun corruptEntryCrcNeverOpensDestination() {
        val source = File.createTempFile("corrupt-export-", ".zip")
        var opened = false
        val payload = "unique-original-raw-data".toByteArray()
        try {
            ZipOutputStream(source.outputStream()).use { zip ->
                val crc = java.util.zip.CRC32().apply { update(payload) }
                zip.putNextEntry(ZipEntry("photo.dng").apply {
                    method = ZipEntry.STORED
                    size = payload.size.toLong()
                    compressedSize = size
                    this.crc = crc.value
                })
                zip.write(payload)
                zip.closeEntry()
            }
            val bytes = source.readBytes()
            val offset = bytes.indices.first { start ->
                start + payload.size <= bytes.size && payload.indices.all { bytes[start + it] == payload[it] }
            }
            bytes[offset] = (bytes[offset].toInt() xor 1).toByte()
            source.writeBytes(bytes)
            assertTrue(runCatching { VerifiedExport.copy(source, { opened = true; ByteArrayOutputStream() }) }.isFailure)
            assertFalse(opened)
        } finally { source.delete() }
    }

    @Test fun failedWriteAbortsWithoutFinalProgress() {
        val source = archive()
        var completed = false
        try {
            try {
                VerifiedExport.copy(source, { object : OutputStream() {
                    override fun write(b: Int) { throw IOException("storage full") }
                } }) { copied, total -> if (copied == total) completed = true }
                fail("Write failure ignored")
            } catch (error: IOException) { assertEquals("storage full", error.message) }
            assertFalse(completed)
        } finally { source.delete() }
    }

}
