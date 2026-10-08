package de.jce.seafrogs

import android.app.Activity
import android.content.Intent
import android.hardware.input.InputManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import org.json.JSONArray
import org.json.JSONObject

/**
 * Deliberately uses platform Views for this first diagnostic milestone.
 * Activity dispatch observes uncaptured events once, before child controls.
 * The dedicated capture surface receives captured events exactly once.
 * No direction classifier and no SeaFrogs-specific device filter exist yet.
 */
class DiagnosticActivity : Activity(), InputManager.InputDeviceListener {
    private lateinit var recorder: EventRecorder
    private lateinit var inputManager: InputManager
    private lateinit var surface: TextView
    private lateinit var status: TextView
    private lateinit var deviceText: TextView
    private lateinit var eventText: TextView
    private val recent = ArrayDeque<String>()
    private val handler = Handler(Looper.getMainLooper())
    private var selectedMarker = "UNMARKED"
    private var count = 0L
    private var wantsCapture = false
    private var initialized = false
    private val refresh = object : Runnable {
        override fun run() {
            status.text = "Ereignisse: $count | Markierung: $selectedMarker\n" +
                "Pointer Capture: ${surface.hasPointerCapture()} | angefordert: $wantsCapture"
            eventText.text = recent.joinToString("\n")
            handler.postDelayed(this, 250)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        recorder = EventRecorder(applicationContext) { message ->
            handler.post { Toast.makeText(this, message, Toast.LENGTH_LONG).show() }
        }
        inputManager = getSystemService(InputManager::class.java)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16, 16, 16, 16)
            // target 35 uses edge-to-edge. Keep controls outside system bars.
            setOnApplyWindowInsetsListener { view, insets ->
                val bars = insets.systemWindowInsetTop to insets.systemWindowInsetBottom
                view.setPadding(16 + insets.systemWindowInsetLeft, 16 + bars.first,
                    16 + insets.systemWindowInsetRight, 16 + bars.second)
                insets
            }
        }
        val scroll = ScrollView(this).apply { addView(root) }
        setContentView(scroll)
        root.addView(text("SeaFrogs HID Diagnose", 24f))
        root.addView(text("Vor dem Test per Touch markieren. Dann die Gehäusetaste drücken. " +
            "Markierungen bezeichnen den Test, keine erkannte Richtung.", 16f))
        status = text("", 18f); root.addView(status)
        surface = text("Eingabefläche\nCapture über die Taste darunter einschalten.\n" +
            "Im Capture-Modus verschwindet der Cursor.", 20f).apply {
            isFocusable = true
            isFocusableInTouchMode = true
            minHeight = 180
            setBackgroundColor(0xff234351.toInt())
            setOnCapturedPointerListener { _, event ->
                observe(EventEncoder.motion(event, "captured"),
                    "${MotionEvent.actionToString(event.action)} dx=${event.x} dy=${event.y} buttons=${event.buttonState}")
                true
            }
        }
        root.addView(surface)
        row(root, listOf("Normal" to { setCapture(false) }, "Capture" to { setCapture(true) }))
        row(root, listOf("Links" to { mark("LEFT") }, "Rechts" to { mark("RIGHT") },
            "Hoch" to { mark("UP") }))
        row(root, listOf("Runter" to { mark("DOWN") }, "Klick" to { mark("CLICK") },
            "Loslassen" to { mark("RELEASE") }))
        row(root, listOf("Neutral" to { mark("NEUTRAL") }, "Export ZIP" to { export() }))
        row(root, listOf("Links+Hoch" to { mark("LEFT+UP") }, "Rechts+Hoch" to { mark("RIGHT+UP") }))
        row(root, listOf("Links+Runter" to { mark("LEFT+DOWN") }, "Rechts+Runter" to { mark("RIGHT+DOWN") }))
        row(root, listOf("Links+Rechts" to { mark("LEFT+RIGHT") }, "Hoch+Runter" to { mark("UP+DOWN") }))
        row(root, listOf("Links+Klick" to { mark("LEFT+CLICK") }, "Rechts+Klick" to { mark("RIGHT+CLICK") }))
        row(root, listOf("Hoch+Klick" to { mark("UP+CLICK") }, "Runter+Klick" to { mark("DOWN+CLICK") }))
        deviceText = text("", 14f); root.addView(deviceText)
        eventText = text("", 13f).apply { typeface = android.graphics.Typeface.MONOSPACE }
        root.addView(eventText)
        initialized = true
        recorder.record(JSONObject().put("kind", "session")
            .put("appVersion", "0.7.2-color-gain").put("manufacturer", Build.MANUFACTURER)
            .put("model", Build.MODEL).put("sdk", Build.VERSION.SDK_INT)
            .put("androidRelease", Build.VERSION.RELEASE).put("buildFingerprint", Build.FINGERPRINT))
        devices("initial")
        handler.post(refresh)
    }

    private fun text(value: String, size: Float) = TextView(this).apply {
        text = value; textSize = size; setPadding(4, 8, 4, 8)
    }

    private fun row(root: LinearLayout, items: List<Pair<String, () -> Unit>>) {
        val row = LinearLayout(this)
        items.forEach { (label, action) ->
            row.addView(Button(this).apply {
                text = label
                setOnClickListener { action() }
            }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        }
        root.addView(row)
    }

    private fun mark(label: String) {
        selectedMarker = label
        recorder.record(JSONObject().put("kind", "marker").put("label", label))
        // Touching a control can change view focus. Restore the capture surface.
        if (wantsCapture) surface.post { setCapture(true) }
    }

    private fun setCapture(enabled: Boolean) {
        wantsCapture = enabled
        if (enabled) {
            surface.requestFocus()
            if (hasWindowFocus()) surface.requestPointerCapture()
        } else surface.releasePointerCapture()
        recorder.record(JSONObject().put("kind", "captureRequest").put("enabled", enabled))
    }

    override fun onPointerCaptureChanged(hasCapture: Boolean) {
        super.onPointerCaptureChanged(hasCapture)
        if (initialized) recorder.record(JSONObject().put("kind", "captureChanged")
            .put("captured", hasCapture))
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (!initialized) return
        recorder.record(JSONObject().put("kind", "windowFocus").put("focused", hasFocus))
        if (hasFocus && wantsCapture) surface.post { setCapture(true) }
    }

    private fun observe(json: JSONObject, detail: String) {
        json.put("marker", selectedMarker)
        recorder.record(json)
        count++
        if (recent.size >= 14) recent.removeFirst()
        recent.addLast("$count ${json.optString("kind")} device=${json.optInt("deviceId")} " +
            "${json.optString("route")} $detail")
    }

    override fun dispatchGenericMotionEvent(event: MotionEvent): Boolean {
        if (initialized) observe(EventEncoder.motion(event, "generic"),
            "${MotionEvent.actionToString(event.action)} x=${event.x} y=${event.y} buttons=${event.buttonState}")
        if (event.isFromSource(InputDevice.SOURCE_MOUSE)) return true
        return super.dispatchGenericMotionEvent(event)
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        if (initialized) observe(EventEncoder.motion(event, "touch"),
            "${MotionEvent.actionToString(event.action)} x=${event.x} y=${event.y} buttons=${event.buttonState}")
        // Diagnostic controls remain touch-only; a housing click must not
        // accidentally change the annotation or open the export picker.
        if (event.isFromSource(InputDevice.SOURCE_MOUSE)) return true
        return super.dispatchTouchEvent(event)
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (initialized) observe(EventEncoder.key(event),
            "${KeyEvent.keyCodeToString(event.keyCode)} action=${event.action} repeat=${event.repeatCount}")
        return super.dispatchKeyEvent(event)
    }

    private fun devices(reason: String) {
        val all = InputDevice.getDeviceIds().toList().mapNotNull { InputDevice.getDevice(it) }
        val snapshots = JSONArray()
        all.forEach { snapshots.put(EventEncoder.device(it)) }
        recorder.record(JSONObject().put("kind", "devices").put("reason", reason)
            .put("devices", snapshots))
        deviceText.text = "Android-Eingabegeräte:\n" + all.joinToString("\n") {
            "${it.id}: ${it.name} | sources=0x${it.sources.toString(16)}\n" +
                it.motionRanges.joinToString { range ->
                    "${MotionEvent.axisToString(range.axis)} [${range.min}, ${range.max}]"
                }
        } + "\nEine aufgeführte Maus bestätigt noch keine SeaFrogs-Zuordnung."
    }

    override fun onInputDeviceAdded(deviceId: Int) = devices("added:$deviceId")
    override fun onInputDeviceChanged(deviceId: Int) = devices("changed:$deviceId")
    override fun onInputDeviceRemoved(deviceId: Int) = devices("removed:$deviceId")

    override fun onResume() {
        super.onResume()
        if (initialized) {
            inputManager.registerInputDeviceListener(this, handler)
            devices("resume")
        }
    }

    override fun onPause() {
        if (initialized) inputManager.unregisterInputDeviceListener(this)
        super.onPause()
    }

    private fun export() {
        setCapture(false)
        startActivityForResult(Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/zip"
            putExtra(Intent.EXTRA_TITLE, "seafrogs-hid-${System.currentTimeMillis()}.zip")
        }, 1)
    }

    @Deprecated("Platform result API keeps the diagnosis free of UI dependencies")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 1 && resultCode == RESULT_OK) data?.data?.let { recorder.export(it) }
    }

    override fun onDestroy() {
        handler.removeCallbacks(refresh)
        if (initialized) {
            inputManager.unregisterInputDeviceListener(this)
            recorder.close()
        }
        super.onDestroy()
    }
}
