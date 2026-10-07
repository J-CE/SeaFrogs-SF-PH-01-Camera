package de.jce.seafrogs

import android.Manifest
import android.app.AlertDialog
import android.view.InputDevice
import android.view.MotionEvent
import android.hardware.input.InputManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import org.json.JSONObject
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.OrientationEventListener
import android.view.Surface
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat

/** Touch and selected HID device invoke the same guarded camera commands. */
class CameraActivity : ComponentActivity(), InputManager.InputDeviceListener {
    private lateinit var controller: PhotoCameraController
    private lateinit var preview: PreviewView
    private lateinit var status: TextView
    private lateinit var lensSwitch: Button
    private lateinit var zoom: Button
    private lateinit var exposure: Button
    private lateinit var shutter: Button
    private lateinit var diagnosis: Button
    private lateinit var restart: Button
    private lateinit var orientation: OrientationEventListener
    private lateinit var inputManager: InputManager
    private lateinit var recorder: EventRecorder
    private lateinit var testCase: Button
    private lateinit var quality: Button
    private lateinit var mouse: Button
    private lateinit var export: Button
    private val handler = Handler(Looper.getMainLooper())
    private val gate = HidCommandGate()
    private var mouseDescriptor: String? = null
    private var wantsCapture = false
    private var initialized = false
    private var primaryDown = false
    private var hidStatus = "MAUS AUS"
    private var downCount = 0
    private val finishBurst = Runnable { finishInput() }
    private val exportRequest = registerForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri != null) recorder.export(uri)
    }
    private var active = false
    private var sessionStarted = false
    private val permissions: Array<String>
        get() = if (Build.VERSION.SDK_INT <= 28) arrayOf(
            Manifest.permission.CAMERA, Manifest.permission.WRITE_EXTERNAL_STORAGE
        ) else arrayOf(Manifest.permission.CAMERA)
    private val permissionRequest = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        if (hasPermissions()) startCamera()
        else showPermissionRequired()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        inputManager = getSystemService(InputManager::class.java)
        recorder = EventRecorder(applicationContext) { message ->
            handler.post { if (!isDestroyed) android.widget.Toast.makeText(this, message, android.widget.Toast.LENGTH_LONG).show() }
        }
        recorder.record(JSONObject().put("kind", "session").put("appVersion", "0.5.0-quality-hid")
            .put("model", Build.MODEL).put("androidBuild", Build.FINGERPRINT))
        val landscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        val root = LinearLayout(this).apply {
            orientation = if (landscape) LinearLayout.HORIZONTAL else LinearLayout.VERTICAL
            setBackgroundColor(android.graphics.Color.BLACK)
            setOnApplyWindowInsetsListener { view, insets ->
                view.setPadding(insets.systemWindowInsetLeft, insets.systemWindowInsetTop,
                    insets.systemWindowInsetRight, insets.systemWindowInsetBottom)
                insets
            }
        }
        val cameraPanel = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val buttonsPanel = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        status = TextView(this).apply {
            textSize = 17f
            gravity = Gravity.CENTER
            setTextColor(android.graphics.Color.WHITE)
            setPadding(dp(8), dp(8), dp(8), dp(8))
        }
        cameraPanel.addView(status)
        preview = PreviewView(this).apply {
            scaleType = PreviewView.ScaleType.FIT_CENTER
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            isFocusable = true
            isFocusableInTouchMode = true
            setOnCapturedPointerListener { _, event -> handleMouse(event, "captured"); true }
        }
        cameraPanel.addView(preview, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        lensSwitch = Button(this).apply {
            text = "KAMERA: 1×"
            textSize = 24f
            minHeight = dp(64)
            isEnabled = false
            setOnClickListener { controller.cycleLens() }
        }
        buttonsPanel.addView(lensSwitch)
        val controls = LinearLayout(this)
        zoom = Button(this).apply {
            text = "ZOOM: 1×"
            textSize = 22f
            minHeight = dp(64)
            isEnabled = false
            setOnClickListener { controller.cycleZoom() }
        }
        exposure = Button(this).apply {
            text = "EV: 0"
            textSize = 22f
            minHeight = dp(64)
            isEnabled = false
            setOnClickListener { controller.cycleExposure() }
        }
        controls.addView(zoom, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        controls.addView(exposure, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        buttonsPanel.addView(controls)
        shutter = Button(this).apply {
            text = "FOTO"
            textSize = 28f
            minHeight = dp(88)
            isEnabled = false
            setOnClickListener { controller.capturePhoto() }
        }
        buttonsPanel.addView(shutter)
        val footer = LinearLayout(this)
        restart = Button(this).apply {
            text = "Erneut starten"
            minHeight = dp(56)
            setOnClickListener {
                if (hasPermissions()) {
                    sessionStarted = false
                    startCamera()
                } else requestPermissionOrSettings()
            }
        }
        diagnosis = Button(this).apply {
            text = "HID-Diagnose"
            minHeight = dp(56)
            setOnClickListener { startActivity(Intent(this@CameraActivity, DiagnosticActivity::class.java)) }
        }
        footer.addView(restart, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        footer.addView(diagnosis, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        buttonsPanel.addView(footer)
        val testControls = LinearLayout(this)
        quality = Button(this).apply { text = "STANDARD"; setOnClickListener { controller.cycleQuality() } }
        mouse = Button(this).apply { text = "MAUS AUS"; setOnClickListener { chooseMouse() } }
        export = Button(this).apply {
            text = "TEST ZIP"
            setOnClickListener {
                preview.releasePointerCapture()
                resetInput("export")
                exportRequest.launch("seafrogs-test-${System.currentTimeMillis()}.zip")
            }
        }
        listOf(quality, mouse, export).forEach {
            testControls.addView(it, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        }
        buttonsPanel.addView(testControls)
        testCase = Button(this).apply {
            text = "TESTFALL: FREI"
            setOnClickListener {
                val cases = arrayOf("FREI", "K01 MAIN", "K02 UW", "K03 MACRO NAH", "K04 MACRO FERN", "K05 CROP1", "K06 CROP2", "K07 EXT MAIN", "K08 EXT UW", "K09 EXT MACRO", "K10 EV", "M01 EINZEL", "M02 HALTEN", "M03 DOPPEL", "M04 KOMBINATION", "M05 LEBENSZYKLUS")
                AlertDialog.Builder(this@CameraActivity).setTitle("Testfall markieren")
                    .setItems(cases) { _, index ->
                        text = "TESTFALL: ${cases[index]}"
                        controller.setTestCase(cases[index])
                    }.show()
            }
        }
        buttonsPanel.addView(testCase)
        root.addView(cameraPanel, if (landscape)
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f)
        else LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        val buttonsView = if (landscape) android.widget.ScrollView(this).apply {
            addView(buttonsPanel)
        } else buttonsPanel
        root.addView(buttonsView, LinearLayout.LayoutParams(
            if (landscape) dp(244) else LinearLayout.LayoutParams.MATCH_PARENT,
            if (landscape) LinearLayout.LayoutParams.MATCH_PARENT else LinearLayout.LayoutParams.WRAP_CONTENT))
        setContentView(root)
        controller = PhotoCameraController(this, { recorder.record(it) }) { state ->
            if (!isDestroyed && active) {
                status.text = "PHOTO | ${state.lens.label} | ${state.zoomLabel} | EV ${state.exposureLabel} | JPEG\n" +
                    state.resolution + " | " + state.quality + "\n" + state.message +
                    "\n" + state.diagnostics + "\n" + hidStatus
                quality.text = state.quality
                quality.isEnabled = state.ready
                testCase.isEnabled = !state.capturing
                export.isEnabled = !state.capturing
                mouse.isEnabled = !state.capturing
                zoom.isEnabled = state.ready
                zoom.text = "ZOOM: ${state.zoomLabel}"
                exposure.isEnabled = state.ready && state.exposureSupported
                exposure.text = if (state.exposureSupported) "EV: ${state.exposureLabel}" else "EV: N/V"
                lensSwitch.isEnabled = state.ready
                lensSwitch.text = "KAMERA: ${state.lens.label}"
                shutter.isEnabled = state.ready
                shutter.text = if (state.capturing) "AUFNAHME …" else "FOTO"
                diagnosis.isEnabled = !state.capturing
                restart.isEnabled = !state.capturing
            }
        }
        initialized = true
        orientation = object : OrientationEventListener(this) {
            override fun onOrientationChanged(angle: Int) {
                if (angle == ORIENTATION_UNKNOWN) return
                // JPEG orientation follows the handset even when Android
                // rotation lock keeps the preview/UI in portrait.
                controller.setRotation(when (angle) {
                    in 45..134 -> Surface.ROTATION_270
                    in 135..224 -> Surface.ROTATION_180
                    in 225..314 -> Surface.ROTATION_90
                    else -> Surface.ROTATION_0
                })
            }
        }
        if (!hasPermissions()) {
            showPermissionRequired()
            val preferences = getPreferences(MODE_PRIVATE)
            if (!preferences.getBoolean("permissionAsked", false)) {
                preferences.edit().putBoolean("permissionAsked", true).apply()
                permissionRequest.launch(permissions)
            }
        }
    }

    override fun onStart() {
        super.onStart()
        active = true
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        orientation.enable()
        if (hasPermissions()) startCamera() else showPermissionRequired()
    }

    override fun onResume() {
        super.onResume()
        inputManager.registerInputDeviceListener(this, handler)
        // Also handles permission changes made in the Android app settings.
        if (hasPermissions()) startCamera() else showPermissionRequired()
    }

    private fun startCamera() {
        if (!active || sessionStarted) return
        sessionStarted = true
        restart.text = "Erneut starten"
        controller.setRotation(preview.display?.rotation ?: Surface.ROTATION_0)
        controller.start(this, preview)
    }

    private fun hasPermissions() = permissions.all {
        ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
    }

    private fun showPermissionRequired() {
        zoom.isEnabled = false
        exposure.isEnabled = false
        lensSwitch.isEnabled = false
        shutter.isEnabled = false
        status.text = "KAMERAZUGRIFF FEHLT\nBerechtigung freigeben. HID-Diagnose bleibt verfügbar."
        restart.text = "Zugriff freigeben"
    }

    private fun requestPermissionOrSettings() {
        val denied = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (denied.any { shouldShowRequestPermissionRationale(it) }) {
            permissionRequest.launch(permissions)
        } else {
            startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.parse("package:$packageName")))
        }
    }

    private fun chooseMouse() {
        if (wantsCapture) {
            wantsCapture = false
            preview.releasePointerCapture()
            resetInput("disabled")
            mouse.text = "MAUS AUS"
            hidStatus = "MAUS AUS"
            return
        }
        val devices = InputDevice.getDeviceIds().toList().mapNotNull { InputDevice.getDevice(it) }
            .filter { it.supportsSource(InputDevice.SOURCE_MOUSE) }
        if (devices.isEmpty()) {
            hidStatus = "KEINE MAUS VERBUNDEN"
            android.widget.Toast.makeText(this, hidStatus, android.widget.Toast.LENGTH_LONG).show()
            return
        }
        AlertDialog.Builder(this).setTitle("SeaFrogs-Maus auswählen")
            .setItems(devices.map { "${it.name} (${it.id})" }.toTypedArray()) { _, position ->
                val device = devices[position]
                mouseDescriptor = device.descriptor
                recorder.record(EventEncoder.device(device).put("kind", "selectedMouse"))
                wantsCapture = true
                mouse.text = "MAUS AN"
                preview.post { capturePointer() }
            }.show()
    }

    private fun capturePointer() {
        if (wantsCapture && active && hasWindowFocus()) {
            preview.requestFocus()
            preview.requestPointerCapture()
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (!initialized) return
        if (hasFocus) preview.post { capturePointer() } else resetInput("windowFocusLost")
    }

    override fun onPointerCaptureChanged(hasCapture: Boolean) {
        super.onPointerCaptureChanged(hasCapture)
        if (!initialized) return
        resetInput("captureChanged:$hasCapture")
        hidStatus = if (hasCapture) "MAUS BEREIT" else if (wantsCapture) "MAUS-CAPTURE FEHLT" else "MAUS AUS"
        recorder.record(JSONObject().put("kind", "captureChanged").put("captured", hasCapture))
    }

    private fun resetInput(reason: String) {
        handler.removeCallbacks(finishBurst)
        gate.reset()
        primaryDown = false
        if (initialized) recorder.record(JSONObject().put("kind", "inputReset").put("reason", reason))
    }

    private fun handleMouse(event: MotionEvent, route: String) {
        if (!initialized) return
        recorder.record(EventEncoder.motion(event, route))
        val selected = InputDevice.getDevice(event.deviceId)?.descriptor == mouseDescriptor
        if (!wantsCapture || !selected || route != "captured" || !preview.hasPointerCapture()) return
        val now = SystemClock.uptimeMillis()
        // Button transitions unify DOWN/BUTTON_PRESS and UP/BUTTON_RELEASE.
        val down = event.buttonState and MotionEvent.BUTTON_PRIMARY != 0
        if (down && !primaryDown) gate.signal(HidCommandGate.Command.CLICK, now)
        primaryDown = down
        if (event.actionMasked == MotionEvent.ACTION_MOVE || event.actionMasked == MotionEvent.ACTION_HOVER_MOVE) {
            for (i in 0 until event.historySize) gate.movement(event.getHistoricalX(i), event.getHistoricalY(i), now)
            gate.movement(event.x, event.y, now)
        }
        handler.removeCallbacks(finishBurst)
        handler.postDelayed(finishBurst, HidCommandGate.QUIET_MS)
    }

    private fun finishInput() {
        val commands = gate.finish(SystemClock.uptimeMillis()) ?: return
        val name = commands.joinToString("+") { it.name }
        val decision = when {
            !preview.hasPointerCapture() || !hasWindowFocus() || !active ||
                !InputDevice.getDeviceIds().any { InputDevice.getDevice(it)?.descriptor == mouseDescriptor } -> "INACTIVE"
            commands.size != 1 -> "COMBINATION_BLOCKED"
            commands.single() == HidCommandGate.Command.DOWN -> "VIDEO_NOT_IMPLEMENTED"
            !controller.readyForCommand() -> "CAMERA_BUSY"
            else -> "EXECUTED"
        }
        hidStatus = "$name: $decision"
        if (commands.singleOrNull() == HidCommandGate.Command.DOWN) {
            downCount++
            hidStatus = "RUNTER $downCount: Video noch nicht implementiert"
        }
        recorder.record(JSONObject().put("kind", "hidCommand").put("command", name).put("decision", decision))
        if (decision == "EXECUTED") when (commands.single()) {
            HidCommandGate.Command.LEFT -> controller.cycleLens()
            HidCommandGate.Command.UP -> controller.cycleZoom()
            HidCommandGate.Command.RIGHT -> controller.cycleExposure()
            HidCommandGate.Command.CLICK -> controller.capturePhoto()
            HidCommandGate.Command.DOWN -> Unit
        }
        android.widget.Toast.makeText(this, hidStatus, android.widget.Toast.LENGTH_SHORT).show()
    }

    override fun dispatchGenericMotionEvent(event: MotionEvent): Boolean {
        if (event.isFromSource(InputDevice.SOURCE_MOUSE)) { handleMouse(event, "generic"); return true }
        return super.dispatchGenericMotionEvent(event)
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        if (event.isFromSource(InputDevice.SOURCE_MOUSE)) { handleMouse(event, "touch"); return true }
        val result = super.dispatchTouchEvent(event)
        if (initialized && wantsCapture && event.actionMasked == MotionEvent.ACTION_UP) preview.post { capturePointer() }
        return result
    }

    override fun onInputDeviceAdded(deviceId: Int) = deviceChanged(deviceId)
    override fun onInputDeviceChanged(deviceId: Int) = deviceChanged(deviceId)
    override fun onInputDeviceRemoved(deviceId: Int) = deviceChanged(deviceId)

    private fun deviceChanged(deviceId: Int) {
        if (!initialized) return
        recorder.record(JSONObject().put("kind", "inputDeviceChanged").put("deviceId", deviceId))
        val connected = InputDevice.getDeviceIds().any { InputDevice.getDevice(it)?.descriptor == mouseDescriptor }
        if (wantsCapture && !connected) {
            wantsCapture = false
            resetInput("mouseDisconnected")
            preview.releasePointerCapture()
            mouse.text = "MAUS AUS"
            hidStatus = "MAUS GETRENNT: erneut auswählen"
            android.widget.Toast.makeText(this, hidStatus, android.widget.Toast.LENGTH_LONG).show()
        }
    }

    override fun onPause() {
        inputManager.unregisterInputDeviceListener(this)
        resetInput("paused")
        super.onPause()
    }

    override fun onDestroy() {
        initialized = false
        inputManager.unregisterInputDeviceListener(this)
        handler.removeCallbacksAndMessages(null)
        recorder.close()
        super.onDestroy()
    }

    override fun onStop() {
        active = false
        resetInput("stopped")
        preview.releasePointerCapture()
        sessionStarted = false
        zoom.isEnabled = false
        exposure.isEnabled = false
        lensSwitch.isEnabled = false
        shutter.isEnabled = false
        orientation.disable()
        controller.stop()
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        super.onStop()
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
}
