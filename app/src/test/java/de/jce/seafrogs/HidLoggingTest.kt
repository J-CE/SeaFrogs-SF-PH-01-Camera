package de.jce.seafrogs

import android.content.Context
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class HidLoggingTest {
    @Test
    fun disabledRecordingSkipsInputSnapshotAndPreservesCameraRecords() {
        val context = RuntimeEnvironment.getApplication() as Context
        val recorder = EventRecorder(context) { fail(it) }
        var inputSnapshotCreated = false
        recorder.recordHidEvent {
            inputSnapshotCreated = true
            JSONObject().put("type", "hid")
        }
        recorder.record(JSONObject().put("type", "camera-test"))
        recorder.close()
        assertFalse(inputSnapshotCreated)
        val logFile = context.filesDir.listFiles()!!.single { it.name.startsWith("hid-") }
        val deadline = System.nanoTime() + 5_000_000_000L
        while (logFile.length() == 0L && System.nanoTime() < deadline) Thread.yield()
        val records = logFile.readLines()
        assertEquals(1, records.size)
        assertEquals("camera-test", JSONObject(records.single()).getString("type"))
    }
}
