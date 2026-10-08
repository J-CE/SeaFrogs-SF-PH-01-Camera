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
    private lateinit var screen: CameraScreenLayout
    private lateinit var toolbar: android.widget.GridLayout
    private lateinit var notice: TextView
    private lateinit var setupDetails: TextView
    private lateinit var status: TextView
    private lateinit var lensSwitch: Button
    private lateinit var zoom: Button
    private lateinit var exposure: Button
    private lateinit var shutter: Button
    private lateinit var modeSwitch: Button
    private lateinit var setupToggle: Button
    private val setupRows = mutableListOf<android.view.View>()
    private var setupVisible = false
    private var orientationBeforeRecording: Int? = null
    private val divePreferences by lazy { getSharedPreferences("dive", MODE_PRIVATE) }
    private val diveControls = mutableListOf<android.view.View>()
    private lateinit var buttonsView: android.widget.ScrollView
    private var normalX: Float? = null
    private var normalY: Float? = null
    private val healthTick = object : Runnable {
        override fun run() { if (active) { renderState(); handler.postDelayed(this, 2000) } }
    }

    private fun applyDiveSettings() {
        window.attributes = window.attributes.apply { screenBrightness = divePreferences.getInt("brightness", 70).coerceIn(1,100) / 100f }
        requestedOrientation = when (divePreferences.getInt("orientation", 0)) {
            1 -> android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            2 -> android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            3 -> android.content.pm.ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE
            else -> android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    private fun setMouseMode(capture: Boolean) {
        wantsCapture = capture
        divePreferences.edit().putBoolean("capture", capture).apply()
        resetInput("mode:$capture")
        if (capture) { setupVisible=false; setupRows.forEach { it.visibility=android.view.View.GONE }; setupToggle.text="SETUP"; preview.post { capturePointer() } }
        else preview.releasePointerCapture()
        hidStatus = if (capture) "STEUERUNG AKTIV" else "KLASSISCHE MAUS"
        renderState()
    }

    private fun diveSettings() {
        val panel = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16),dp(8),dp(16),dp(8)) }
        val label = TextView(this)
        val brightness = android.widget.SeekBar(this).apply { max = 99; progress = divePreferences.getInt("brightness",70)-1 }
        label.text = "Displayhelligkeit: ${brightness.progress+1}%"
        brightness.setOnSeekBarChangeListener(object : android.widget.SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(bar: android.widget.SeekBar?, value: Int, user: Boolean) {
                label.text = "Displayhelligkeit: ${value+1}%"
                window.attributes = window.attributes.apply { screenBrightness = (value+1)/100f }
            }
            override fun onStartTrackingTouch(bar: android.widget.SeekBar?) {}
            override fun onStopTrackingTouch(bar: android.widget.SeekBar?) {}
        })
        panel.addView(label); panel.addView(brightness)
        panel.addView(TextView(this).apply { text = "Ausrichtung" })
        val direction = android.widget.Spinner(this).apply {
            adapter = android.widget.ArrayAdapter(this@CameraActivity, android.R.layout.simple_spinner_dropdown_item,
                listOf("Automatisch", "Hochformat", "Querformat", "Querformat umgekehrt"))
            setSelection(divePreferences.getInt("orientation",0))
        }
        panel.addView(direction)
        panel.addView(TextView(this).apply { text = "Display bleibt an. Fotos: Pictures/SeaFrogs · Video: Movies/SeaFrogs. Links + Hoch: Steuerung. Rechts + Runter: klassische Maus." })
        AlertDialog.Builder(this).setTitle("Tauchprofil").setView(panel)
            .setNegativeButton("Abbrechen") { _,_ -> applyDiveSettings() }
            .setOnCancelListener { applyDiveSettings() }
            .setPositiveButton("Speichern") { _,_ ->
                divePreferences.edit().putInt("brightness",brightness.progress+1).putInt("orientation",direction.selectedItemPosition).apply()
                applyDiveSettings()
            }.show()
    }

    private fun cycleSettings() {
        val panel = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16),dp(8),dp(16),dp(8)) }
        fun field(title: String, values: List<Float>): android.widget.EditText {
            panel.addView(TextView(this).apply { text = title })
            return android.widget.EditText(this).apply { setText(values.joinToString(";")); isSingleLine = true; panel.addView(this) }
        }
        val main = field("Hauptkamera-Zoom", CameraControlCycles.zoomRatios)
        val uw = field("UW-Zoom relativ zu 0,5×", CameraControlCycles.ultrawideZoomRatios)
        val ev = field("EV-Zyklus", CameraControlCycles.exposureValues)
        val dialog = AlertDialog.Builder(this).setTitle("Zyklen: Werte mit Semikolon trennen").setView(panel)
            .setNegativeButton("Abbrechen",null).setPositiveButton("Speichern",null).create()
        dialog.setOnShowListener { dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            fun parse(text: String, min: Float, max: Float): List<Float>? = runCatching {
                text.split(';').map { it.trim().replace(',','.').toFloat() }.also {
                    require(it.size in 2..8 && it.all { v -> v.isFinite() && v in min..max } && it.distinct().size == it.size)
                }
            }.getOrNull()
            val a = parse(main.text.toString(),1f,10f); val b = parse(uw.text.toString(),1f,10f); val c = parse(ev.text.toString(),-5f,5f)
            if (a == null || b == null || c == null || a.first()!=1f || b.first()!=1f || c.first()!=0f) {
                android.widget.Toast.makeText(this,"2–8 eindeutige Werte; Zoom beginnt mit 1, EV mit 0",android.widget.Toast.LENGTH_LONG).show()
            } else {
                CameraControlCycles.zoomRatios=a; CameraControlCycles.ultrawideZoomRatios=b; CameraControlCycles.exposureValues=c
                divePreferences.edit().putString("mainZoom",a.joinToString(";")).putString("uwZoom",b.joinToString(";")).putString("ev",c.joinToString(";")).apply()
                dialog.dismiss()
            }
        } }; dialog.show()
    }
    private lateinit var diagnosis: Button
    private lateinit var restart: Button
    private lateinit var orientation: OrientationEventListener
    private lateinit var inputManager: InputManager
    private lateinit var recorder: EventRecorder
    private lateinit var automated: AutomatedCameraTest
    private lateinit var cancelTest: Button
    private lateinit var macroTest: Button
    private lateinit var format: Button
    private lateinit var exposureSetup: Button
    private var exportGroupOnly: String? = null
    private var exporting = false
    private var exportStatus = ""
    private lateinit var libraryTest: Button
    private lateinit var libraryExport: Button
    private var autoStatus = ""
    private var lastNoticeMessage = ""
    private var noticeUntil = 0L
    private var lastCameraState = PhotoCameraState()
    private lateinit var quality: Button
    private lateinit var mouse: Button
    private lateinit var export: Button
    private val capabilityWorker = java.util.concurrent.Executors.newSingleThreadExecutor()
    private var capabilityReport: String? = null
    private var capabilityQueryStarted = false
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
        val groupOnly = exportGroupOnly
        exportGroupOnly = null
        if (uri != null) recorder.export(uri, automated.photos(groupOnly), automated.report(groupOnly), capabilityReport) { message, busy ->
            handler.post {
                if (!isDestroyed) {
                    exportStatus = message
                    exporting = busy
                    renderState()
                }
            }
        }
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
        exportGroupOnly = savedInstanceState?.getString("exportGroupOnly")
        inputManager = getSystemService(InputManager::class.java)
        mouseDescriptor = divePreferences.getString("mouse",null)
        wantsCapture = divePreferences.getBoolean("capture",false)
        fun restored(key: String, defaults: List<Float>) = runCatching {
            divePreferences.getString(key,null)?.split(';')?.map { it.toFloat() }?.takeIf { it.size in 2..8 && it.all(Float::isFinite) } ?: defaults
        }.getOrDefault(defaults)
        CameraControlCycles.zoomRatios = restored("mainZoom", listOf(1f,1.5f,3f,5f))
        CameraControlCycles.ultrawideZoomRatios = restored("uwZoom", listOf(1f,1.5f,3f))
        CameraControlCycles.exposureValues = restored("ev", listOf(0f,1f,2f,-1f,-2f))
        applyDiveSettings()
        recorder = EventRecorder(applicationContext) { message ->
            handler.post { if (!isDestroyed) android.widget.Toast.makeText(this, message, android.widget.Toast.LENGTH_LONG).show() }
        }
        recorder.record(JSONObject().put("kind", "session").put("appVersion", "0.8.5-preview-fix")
            .put("model", Build.MODEL).put("androidBuild", Build.FINGERPRINT))
        val landscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        screen = CameraScreenLayout(this).apply {
            setBackgroundColor(android.graphics.Color.BLACK)
            setOnApplyWindowInsetsListener { view, insets ->
                view.setPadding(insets.systemWindowInsetLeft, insets.systemWindowInsetTop,
                    insets.systemWindowInsetRight, insets.systemWindowInsetBottom)
                insets
            }
        }
        val buttonsPanel = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        status = TextView(this).apply {
            textSize = 18f
            gravity = Gravity.CENTER
            setTextColor(android.graphics.Color.WHITE)
            setPadding(dp(8), dp(4), dp(8), dp(4))
            setBackgroundColor(0xB0000000.toInt())
            maxLines = 3
        }
        preview = PreviewView(this).apply {
            scaleType = PreviewView.ScaleType.FIT_CENTER
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            isFocusable = true
            isFocusableInTouchMode = true
            setOnCapturedPointerListener { _, event -> handleMouse(event, "captured"); true }
        }
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
            setOnClickListener { controller.triggerCapture() }
        }
        buttonsPanel.addView(shutter)
        modeSwitch = Button(this).apply {
            text = "MODUS: FOTO"; minHeight = dp(56)
            setOnClickListener { controller.toggleCaptureMode() }
        }
        buttonsPanel.addView(modeSwitch)
        setupToggle = Button(this).apply {
            text = "SETUP"; minHeight = dp(48)
            setOnClickListener {
                if (!lastCameraState.capturing && !lastCameraState.recording) {
                    setupVisible = !setupVisible
                    setupRows.forEach { it.visibility = if (setupVisible) android.view.View.VISIBLE else android.view.View.GONE }
                    text = if (setupVisible) "SETUP SCHLIESSEN" else "SETUP"
                    renderState()
                }
            }
        }
        buttonsPanel.addView(setupToggle)
        diveControls.addAll(listOf(lensSwitch, controls, shutter, modeSwitch))
        val profile = Button(this).apply { text = "TAUCHPROFIL"; setOnClickListener { diveSettings() } }
        val cycles = Button(this).apply { text = "ZOOM / EV ZYKLEN"; setOnClickListener { cycleSettings() } }
        val wb = Button(this).apply { text = "WEISSABGLEICH"; setOnClickListener {
            if (lastCameraState.ready && !lastCameraState.recording)
                AlertDialog.Builder(this@CameraActivity).setTitle("Weißabgleich")
                    .setItems(controller.whiteBalanceChoices().map { it.first }.toTypedArray()) { _,index ->
                        controller.setWhiteBalance(controller.whiteBalanceChoices()[index].second)
                    }.show()
        } }
        val strength = Button(this).apply { text="UW-WB STÄRKE"; setOnClickListener {
            if(lastCameraState.ready && !lastCameraState.recording) AlertDialog.Builder(this@CameraActivity)
                .setTitle("Unterwasser-Korrektur").setSingleChoiceItems(arrayOf("25%", "50%", "75%", "100%"),listOf(25,50,75,100).indexOf(controller.whiteBalanceStrength())) { dialog,index ->
                    controller.setWhiteBalanceStrength(listOf(25,50,75,100)[index]); dialog.dismiss()
                }.setNegativeButton("Zurück",null).show()
        } }
        listOf(profile,cycles,wb,strength).forEach { buttonsPanel.addView(it); setupRows.add(it) }
        val videoSettings = Button(this).apply {
            text = "VIDEO: 4K30 / 4K60"
            setOnClickListener {
                if (!lastCameraState.recording && lastCameraState.ready)
                    AlertDialog.Builder(this@CameraActivity).setTitle("Video vor dem Tauchgang")
                        .setSingleChoiceItems(arrayOf("4K30 ohne Ton", "4K60 ohne Ton"), if (controller.savedVideoFps()==60) 1 else 0) { dialog, choice ->
                            controller.setVideoFps(if (choice==0) 30 else 60); dialog.dismiss()
                        }.setNegativeButton("Zurück",null).show()
            }
        }
        buttonsPanel.addView(videoSettings); setupRows.add(videoSettings)
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
            text = "KAMERADATEN …"
            isEnabled = false
            setOnClickListener {
                preview.releasePointerCapture()
                resetInput("export")
                exportGroupOnly = null
                exportRequest.launch("seafrogs-test-${System.currentTimeMillis()}.zip")
            }
        }
        listOf(quality, mouse, export).forEach {
            testControls.addView(it, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        }
        buttonsPanel.addView(testControls)
        val rawControls = LinearLayout(this)
        format = Button(this).apply {
            text = "FORMAT: JPEG"
            setOnClickListener {
                AlertDialog.Builder(this@CameraActivity).setTitle("Fotoformat vor dem Tauchgang")
                    .setItems(arrayOf("JPEG only", "RAW + JPEG (DNG)")) { _, choice ->
                        controller.setRawEnabled(choice == 1)
                    }.show()
            }
        }
        exposureSetup = Button(this).apply {
            text = "ISO: AUTO"
            setOnClickListener { chooseExposureLimits() }
        }
        listOf(format, exposureSetup).forEach {
            rawControls.addView(it, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        }
        buttonsPanel.addView(rawControls)
        val libraryControls = LinearLayout(this)
        libraryTest = Button(this).apply {
            text = "MEHRBILD TEST"
            setOnClickListener { beginAutomated(false, fusion = true) }
        }
        libraryExport = Button(this).apply {
            text = "TEST ZIP"
            setOnClickListener {
                preview.releasePointerCapture()
                resetInput("libraryExport")
                exportGroupOnly = "FUSION"
                exportRequest.launch("seafrogs-mehrbild-${System.currentTimeMillis()}.zip")
            }
        }
        listOf(libraryTest, libraryExport).forEach {
            libraryControls.addView(it, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        }
        // Mehrbild test controls are frozen and no longer exposed.
        val autoControls = LinearLayout(this)
        cancelTest = Button(this).apply {
            text = "ABBRECHEN"
            minHeight = dp(64)
            setOnClickListener { automated.cancel() }
        }
        macroTest = Button(this).apply {
            text = "MACROTEST"
            minHeight = dp(64)
            setOnClickListener { beginAutomated(true) }
        }
        autoControls.addView(cancelTest, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        autoControls.addView(macroTest, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        buttonsPanel.addView(autoControls)
        setupRows.addAll(listOf(footer, testControls, rawControls, autoControls))
        setupRows.forEach { it.visibility = android.view.View.GONE }
        // Setup overlays the image; opening it never shrinks the photo viewport.
        val closeSetup = Button(this).apply {
            text = "SETUP SCHLIESSEN"
            setOnClickListener { setupVisible = false; renderState() }
        }
        buttonsPanel.addView(closeSetup, 0)
        setupDetails = TextView(this).apply {
            textSize = 14f; setTextColor(android.graphics.Color.WHITE)
            setPadding(dp(12), dp(8), dp(12), dp(8))
        }
        buttonsPanel.addView(setupDetails, 1)
        toolbar = android.widget.GridLayout(this).apply { columnCount = if (landscape) 6 else 3 }
        listOf(lensSwitch, zoom, exposure, modeSwitch, shutter, setupToggle).forEach { button ->
            (button.parent as? android.view.ViewGroup)?.removeView(button)
            button.textSize = 16f
            button.minHeight = dp(48); button.minimumHeight = dp(48)
            button.minWidth = 0; button.minimumWidth = 0
            button.setPadding(dp(4), 0, dp(4), 0)
            toolbar.addView(button, android.widget.GridLayout.LayoutParams().apply {
                width = 0; height = dp(48)
                columnSpec = android.widget.GridLayout.spec(android.widget.GridLayout.UNDEFINED, 1f)
            })
        }
        diveControls.clear(); diveControls.addAll(listOf(lensSwitch, zoom, exposure, modeSwitch, shutter))
        buttonsView = android.widget.ScrollView(this).apply {
            setBackgroundColor(0xF0000000.toInt()); addView(buttonsPanel)
        }
        notice = TextView(this).apply {
            textSize = 22f; gravity = Gravity.CENTER
            setTextColor(android.graphics.Color.WHITE)
            setBackgroundColor(0xD0000000.toInt())
            setPadding(dp(8), dp(6), dp(8), dp(6))
            visibility = android.view.View.GONE
        }
        screen.attach(preview, status, notice, toolbar, buttonsView)
        setContentView(screen)
        controller = PhotoCameraController(this, { recorder.record(it) }) { state ->
            lastCameraState = state
            if (::automated.isInitialized) renderState()
        }
        automated = AutomatedCameraTest(this, controller, { recorder.record(it) }) { text, running ->
            autoStatus = text
            if (running) resetInput("autoTest")
            renderState()
        }
        initialized = true
        orientation = object : OrientationEventListener(this) {
            override fun onOrientationChanged(angle: Int) {
                if (angle == ORIENTATION_UNKNOWN || divePreferences.getInt("orientation",0) != 0) return
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
        handler.removeCallbacks(healthTick); handler.post(healthTick)
        preview.post { capturePointer() }
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
        readCapabilities()
        sessionStarted = true
        restart.text = "Erneut starten"
        controller.setRotation(when(divePreferences.getInt("orientation",0)) { 1 -> Surface.ROTATION_0; 2 -> Surface.ROTATION_90; 3 -> Surface.ROTATION_270; else -> preview.display?.rotation ?: Surface.ROTATION_0 })
        controller.start(this, preview)
    }

    private fun readCapabilities() {
        if (capabilityQueryStarted) return
        capabilityQueryStarted = true
        capabilityWorker.execute {
            val snapshot = CameraCapabilityReport.collect(applicationContext)
            val encoded = snapshot.toString(2)
            handler.post {
                if (isDestroyed) return@post
                capabilityReport = encoded
                recorder.record(JSONObject().put("kind", "cameraCapabilityQueryFinished")
                    .put("cameraCount", snapshot.optJSONArray("cameras")?.length() ?: 0)
                    .put("errors", snapshot.optJSONArray("errors")))
                renderState()
            }
        }
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
        setupVisible = true
        setupRows.forEach { it.visibility = android.view.View.VISIBLE }
        buttonsView.visibility = android.view.View.VISIBLE
        toolbar.visibility = android.view.View.GONE
        screen.requestLayout()
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

    private fun renderState() {
        if (isDestroyed || !active) return
        if (!hasPermissions()) { showPermissionRequired(); return }
        val state = lastCameraState
        val testing = automated.running || exporting
        if (state.recording && orientationBeforeRecording == null) {
            orientationBeforeRecording = requestedOrientation
            requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LOCKED
        } else if (!state.recording) {
            orientationBeforeRecording?.let { requestedOrientation = it }
            orientationBeforeRecording = null
        }
        if (state.recording && setupVisible) {
            setupVisible = false; setupRows.forEach { it.visibility = android.view.View.GONE }
            setupToggle.text = "SETUP"
        }
        val cameraMessage = if (state.message == "Bereit" && !state.ready) "Warte auf Vorschau/Fokus …" else state.message
        val free = android.os.StatFs(android.os.Environment.getExternalStorageDirectory().path).availableBytes
        val battery = getSystemService(android.os.BatteryManager::class.java).getIntProperty(android.os.BatteryManager.BATTERY_PROPERTY_CAPACITY)
        val hot = Build.VERSION.SDK_INT >= 29 && getSystemService(android.os.PowerManager::class.java).currentThermalStatus >= android.os.PowerManager.THERMAL_STATUS_SEVERE
        val warnings = buildList {
            if (battery in 0..10) add("AKKU KRITISCH")
            if (hot) add("ÜBERHITZUNG")
            if (free < 256L*1024*1024) add("SPEICHER KNAPP")
            if (mouseDescriptor != null && !mouseConnected()) add("GEHÄUSE GETRENNT")
        }
        val wbLabel = controller.whiteBalanceLabel()
        val wbShort = wbLabel.substringBefore(" · ").replace("Unterwasser ", "").replace("Videolicht ", "") +
            (if(wbLabel.startsWith("Unterwasser ")) " ${controller.whiteBalanceStrength()}%" else "")
        status.text = "${if(state.videoMode) "VIDEO 4K${state.videoFps}" else "PHOTO"} | ${state.lens.label} | ${state.zoomLabel} | EV ${state.exposureLabel}\n" +
            (if(state.videoMode) "OHNE TON" else state.photoFormat) + " · WB $wbShort · Akku ${if(battery in 0..100) "$battery%" else "?"} · ${String.format(java.util.Locale.GERMAN,"%.1f",free/1073741824.0)} GB"
        val notices = warnings.toMutableList()
        if (state.recording) notices.add(0, "● AUFNAHME")
        val now = SystemClock.uptimeMillis()
        if (cameraMessage != lastNoticeMessage) {
            lastNoticeMessage = cameraMessage
            noticeUntil = now + 3000L
        }
        val captureWarning = cameraMessage.substringAfter("\nWARNUNG:", "").takeIf { it.isNotBlank() }
        val cameraError = captureWarning == null && listOf("fehl", "zu wenig", "nicht verfügbar", "nicht unterstützt").any { cameraMessage.contains(it, true) }
        if (cameraMessage != "Bereit" && cameraMessage.isNotBlank() &&
            (!state.ready || state.capturing || state.recording || cameraError || now < noticeUntil))
            notices.add(cameraMessage.substringBefore('\n'))
        captureWarning?.let { notices.add("WARNUNG: $it") }
        if (wbLabel.contains("WB NICHT BESTÄTIGT")) notices.add("WB NICHT BESTÄTIGT")
        if (wbLabel.contains("WB ABWEICHEND") || wbLabel.contains("Profil nicht verfügbar")) notices.add("WB BEGRENZT / NICHT VERFÜGBAR")
        if (exportStatus.isNotBlank()) notices.add(exportStatus)
        if (autoStatus.isNotBlank()) notices.add(autoStatus)
        notice.text = notices.joinToString("\n")
        notice.visibility = if (notices.isEmpty()) android.view.View.GONE else android.view.View.VISIBLE
        notice.setTextColor(when {
            state.recording || warnings.isNotEmpty() || cameraError -> android.graphics.Color.RED
            captureWarning != null -> android.graphics.Color.YELLOW
            else -> android.graphics.Color.WHITE
        })
        setupDetails.text = "${state.resolution}\nWB $wbLabel\n${state.diagnostics}\n$hidStatus"
        val bars = androidx.core.view.WindowCompat.getInsetsController(window,window.decorView)
        bars.systemBarsBehavior=androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        bars.hide(androidx.core.view.WindowInsetsCompat.Type.systemBars())
        diveControls.forEach { it.visibility = if(wantsCapture || setupVisible) android.view.View.GONE else android.view.View.VISIBLE }
        setupRows.forEach { it.visibility = if(setupVisible) android.view.View.VISIBLE else android.view.View.GONE }
        setupToggle.text = "SETUP"
        toolbar.visibility = if(setupVisible) android.view.View.GONE else android.view.View.VISIBLE
        toolbar.columnCount = if(wantsCapture) 1 else if(resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) 6 else 3
        buttonsView.visibility = if(setupVisible) android.view.View.VISIBLE else android.view.View.GONE
        if (screen.videoMode != state.videoMode || screen.housingControl != wantsCapture) {
            screen.videoMode = state.videoMode
            screen.housingControl = wantsCapture
            screen.requestLayout()
        }
        quality.text = state.quality
        quality.isEnabled = state.ready && !state.videoMode && !testing
        format.text = "FORMAT: ${state.photoFormat}"
        format.isEnabled = state.ready && !state.videoMode && !testing
        exposureSetup.isEnabled = state.ready && !state.videoMode && !testing
        exposureSetup.text = if (state.exposureLimits.enabled) "ISO / DIGITAL" else "ISO: AUTO"
        libraryTest.isEnabled = state.ready && !testing
        libraryExport.isEnabled = capabilityReport != null && !testing && !state.capturing
        export.text = if (capabilityReport == null) "KAMERADATEN …" else "DIAGNOSE ZIP"
        export.isEnabled = capabilityReport != null && !state.capturing && !testing
        mouse.isEnabled = !state.capturing && !testing
        zoom.isEnabled = state.ready && !testing
        zoom.text = state.zoomLabel
        exposure.isEnabled = state.ready && state.exposureSupported && !testing
        exposure.text = if (state.exposureSupported) "EV: ${state.exposureLabel}" else "EV: N/V"
        lensSwitch.isEnabled = state.ready && !state.recording && !testing
        lensSwitch.text = state.lens.label
        shutter.isEnabled = (state.ready || (state.recording && !state.capturing)) && !testing
        shutter.text = if (state.capturing) "WARTE …" else if (state.recording) "VIDEO STOP" else if (!state.ready) "WARTE …" else if (state.videoMode) "VIDEO START" else "FOTO"
        modeSwitch.text = if (state.videoMode) "→ FOTO" else "→ VIDEO"
        modeSwitch.isEnabled = state.ready && !state.recording && !testing
        setupToggle.isEnabled = !state.capturing && !state.recording && !testing
        status.setTextColor(if (state.recording || warnings.isNotEmpty() || cameraError) android.graphics.Color.RED else android.graphics.Color.WHITE)
        diagnosis.isEnabled = !state.capturing && !state.recording && !testing
        restart.isEnabled = !state.capturing && !state.recording && !testing
        cancelTest.isEnabled = automated.running
        macroTest.isEnabled = state.ready && !state.videoMode && !testing
    }

    private fun beginAutomated(macro: Boolean, raw: Boolean = false, iso: Boolean = false, processing: Boolean = false, libraries: Boolean = false, fusion: Boolean = false) {
        if (libraries && android.os.StatFs(android.os.Environment.getExternalStorageDirectory().path).availableBytes < 1_000_000_000L) {
            AlertDialog.Builder(this).setTitle("Speicher reicht nicht")
                .setMessage("Für RAW-Serie und ZIP mindestens 1 GB freihalten.").setPositiveButton("OK", null).show()
            return
        }
        val message = if (fusion)
            "Handy fest abstützen. Bedrucktes Motiv mit feiner Schrift etwa 50 cm entfernt, gleichbleibendes Licht. Dann START drücken und warten.\n\nDie App prüft automatisch Hauptkamera, Ultraweitwinkel und Sensor-ISO 400 mit digitaler Verstärkung 1×. Das dritte Bild darf dabei dunkler werden. Pro Schritt entstehen ein STANDARD-JPEG und ein verarbeitetes MEHRBILD-JPEG aus fünf RAW-Aufnahmen. Keine DNGs im Export. Während des Tests keine Tasten drücken. Danach TEST ZIP exportieren und hochladen. Der Test bestätigt noch keinen Macro-Nahfokus."
        else if (libraries)
            "Handy fest abstützen. Bedrucktes Motiv mit feinen Details etwa 50 cm entfernt, gleichbleibendes Licht. Nicht bewegen und während des Tests keine Tasten drücken.\n\nDie App nimmt je fünf RAW+JPEG-Paare mit Hauptkamera und UW auf, in derselben Kamerasitzung mit festgelegter Belichtung, Weißabgleich und Fokus. Danach folgen AUTO-, HDR- und NIGHT-Referenzen, sofern verfügbar. ISO-Grenzen sind aus.\n\nDiese RAW-Serien dienen als identische Eingaben für den Bibliotheksvergleich. Diese APK enthält noch keine MotionCam-/HDR+-Verarbeitung. Benötigt mindestens 1 GB freien Speicher. Danach BIB ZIP exportieren und nach Export gespeichert hochladen. ZIP etwa 250 MB."
        else if (processing)
            "Stütze das Handy fest ab und stelle ein bedrucktes Motiv mit feinen Details etwa 50 cm vor die Linse. Motiv und Licht konstant halten, während des Tests nichts berühren.\n\nDie App vergleicht je Kamera Standard-Verarbeitung, HQ-Entrauschen und HQ-Entrauschen + HQ-Schärfung. Belichtung, Weißabgleich und Fokus bleiben je Kamera fest. ISO-Grenzen sind für diesen Test aus. Danach folgt ein separates NIGHT-Referenzbild der Hauptkamera, sofern verfügbar.\n\nNicht unterstützte Varianten überspringt die App. Bis zu sieben JPEGs, Vorschau pausiert bei normalen Vergleichsaufnahmen. Danach QUALITÄT ZIP exportieren."
        else if (iso)
            "Stütze das Handy ab. Stelle ein bedrucktes Motiv etwa 50 cm vor die Linse und halte das Licht konstant.\n\nDie App fotografiert automatisch mit Hauptkamera und UW: jeweils Auto, maximal ISO 800 und maximal ISO 400. Die Zeitgrenze aus dem ISO-Setup gilt bei den beiden begrenzten Aufnahmen. Auto bleibt unbeschränkt. Alle sechs Fotos nutzen denselben Aufnahmeweg; die Vorschau pausiert während der Aufnahme.\n\nDanach ISO ZIP exportieren."
        else if (raw)
            "Lege eine bedruckte Seite etwa 50 cm vor die Kameralinse. Stütze das Handy ab, halte Motiv und Licht konstant.\n\nDie App nimmt je ein RAW+JPEG-Paar mit Hauptkamera, UW und Macro auf. Dieser Test prüft Dateiformat und Sensorzuordnung. Während jeder RAW-Aufnahme pausiert die Vorschau."
        else if (macro)
            "Lege eine bedruckte Seite etwa 5 cm vor die Kameralinse. Stütze das Handy ab und halte das Licht konstant.\n\nDie App übernimmt Kamera, Fokusversuch, Crop-Stufen und Aufnahmen. Während des Tests nichts bewegen."
        else "Lege eine bedruckte Seite etwa 50 cm vor die Kameralinse. Stütze das Handy ab und halte das Licht konstant.\n\nDie App übernimmt Kamera, Zoom, EV, verfügbare Bildverarbeitung und Aufnahmen. Während des Tests nichts bewegen."
        AlertDialog.Builder(this).setTitle(if (fusion) "Automatischer MIT-Mehrbildtest" else if (libraries) "Automatischer Bibliotheks-Datentest" else if (processing) "Automatischer Qualitätstest" else if (iso) "Automatischer ISO-Vergleich" else if (raw) "Automatischer RAW-Test" else if (macro) "Automatischer Macrotest" else "Automatischer Normaltest")
            .setMessage(message).setNegativeButton("Zurück", null)
            .setPositiveButton("Test starten") { _, _ ->
                preview.releasePointerCapture()
                resetInput("autoTestStart")
                automated.start(macro, raw, iso, processing, libraries, fusion)
            }.show()
    }

    private fun chooseExposureLimits() {
        val settings = controller.savedExposureLimits()
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(8), dp(20), dp(8))
        }
        panel.addView(TextView(this).apply { text = "Sensor-ISO-Obergrenze" })
        val iso = android.widget.Spinner(this).apply {
            adapter = android.widget.ArrayAdapter(this@CameraActivity,
                android.R.layout.simple_spinner_dropdown_item,
                listOf("Auto (unbegrenzt)", "Maximal ISO 400", "Maximal ISO 800", "Maximal ISO 1600"))
            setSelection(ExposureLimits.isoChoices.indexOf(settings.isoCap))
        }
        panel.addView(iso)
        panel.addView(TextView(this).apply { text = "Längste Belichtungszeit bei aktiver ISO-Grenze" })
        val time = android.widget.Spinner(this).apply {
            adapter = android.widget.ArrayAdapter(this@CameraActivity,
                android.R.layout.simple_spinner_dropdown_item, listOf("1/30 s", "1/60 s", "1/125 s"))
            setSelection(ExposureLimits.timeChoices.indexOf(settings.longestTimeNs))
        }
        panel.addView(time)
        panel.addView(TextView(this).apply { text = "Digitale Verstärkung nach RAW" })
        val boost = android.widget.Spinner(this).apply {
            adapter = android.widget.ArrayAdapter(this@CameraActivity,
                android.R.layout.simple_spinner_dropdown_item, listOf("Auto", "Maximal 1×", "Maximal 2×", "Maximal 4×"))
            setSelection(ExposureLimits.boostChoices.indexOf(settings.boostCap))
        }
        panel.addView(boost)
        panel.addView(TextView(this).apply {
            text = "Sensor-Auto begrenzt weder ISO noch Zeit. Digital-Auto übernimmt die gemessene Verstärkung. Bei aktiver Grenze fotografiert die App ohne Extensions in einer dauerhaft offenen Fotositzung. Reichen ISO und Zeit nicht für die gemessene Helligkeit, bleibt das Foto dunkler. Die Grenze gilt für Sensor-ISO. Zusätzliche JPEG-Verstärkung kann einen höheren EXIF-ISO-Wert ergeben. Eine digitale Grenze kann das Bild zusätzlich abdunkeln. Niedrigere Sensor-ISO garantiert keine bessere Aufnahme bei Bewegung."
        })
        AlertDialog.Builder(this).setTitle("Belichtung vor dem Tauchgang").setView(panel)
            .setNegativeButton("Zurück", null).setPositiveButton("Speichern") { _, _ ->
                controller.setExposureLimits(ExposureLimits(ExposureLimits.isoChoices[iso.selectedItemPosition],
                    ExposureLimits.timeChoices[time.selectedItemPosition], ExposureLimits.boostChoices[boost.selectedItemPosition]))
            }.show()
    }

    private fun chooseMouse() {
        if (wantsCapture) {
            setMouseMode(false)
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
                divePreferences.edit().putString("mouse", device.descriptor).apply()
                recorder.record(EventEncoder.device(device).put("kind", "selectedMouse"))
                setMouseMode(true)
                mouse.text = "MAUS AN"
                preview.post { capturePointer() }
            }.show()
    }

    private fun capturePointer() {
        if (wantsCapture && mouseConnected() && active && hasWindowFocus()) {
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
        normalX = null; normalY = null
        if (initialized) recorder.record(JSONObject().put("kind", "inputReset").put("reason", reason))
    }

    private fun handleMouse(event: MotionEvent, route: String) {
        if (!initialized) return
        recorder.record(EventEncoder.motion(event, route))
        val selected = InputDevice.getDevice(event.deviceId)?.descriptor == mouseDescriptor
        if (automated.running || exporting || !selected) return
        if (wantsCapture && (route != "captured" || !preview.hasPointerCapture())) return
        if (!wantsCapture && route == "captured") return
        val now = SystemClock.uptimeMillis()
        if (!wantsCapture && event.actionMasked == MotionEvent.ACTION_HOVER_ENTER) { normalX=event.x; normalY=event.y }
        // Button transitions unify DOWN/BUTTON_PRESS and UP/BUTTON_RELEASE.
        val down = event.buttonState and MotionEvent.BUTTON_PRIMARY != 0
        if (wantsCapture && down && !primaryDown) gate.signal(HidCommandGate.Command.CLICK, now)
        primaryDown = down
        if (event.actionMasked == MotionEvent.ACTION_MOVE || event.actionMasked == MotionEvent.ACTION_HOVER_MOVE) {
            if (route == "captured") {
                for (i in 0 until event.historySize) gate.movement(event.getHistoricalX(i), event.getHistoricalY(i), now)
                gate.movement(event.x, event.y, now)
            } else {
                fun sample(x: Float, y: Float) {
                    val dx = normalX?.let { x-it } ?: 0f; val dy = normalY?.let { y-it } ?: 0f
                    normalX=x; normalY=y
                    gate.movement(dx,dy,now)
                }
                val relativeX=event.getAxisValue(MotionEvent.AXIS_RELATIVE_X)
                val relativeY=event.getAxisValue(MotionEvent.AXIS_RELATIVE_Y)
                if(relativeX != 0f || relativeY != 0f) { gate.movement(relativeX,relativeY,now); normalX=event.x; normalY=event.y }
                else { for(i in 0 until event.historySize) sample(event.getHistoricalX(i),event.getHistoricalY(i)); sample(event.x,event.y) }
            }
        }
        handler.removeCallbacks(finishBurst)
        handler.postDelayed(finishBurst, HidCommandGate.QUIET_MS)
    }

    private fun finishInput() {
        val commands = gate.finish(SystemClock.uptimeMillis()) ?: return
        gate.modeSwitch(commands)?.let { mode -> setMouseMode(mode == HidCommandGate.ModeSwitch.CAMERA); return }
        if (!wantsCapture) return
        val name = commands.joinToString("+") { it.name }
        val decision = when {
            automated.running -> "AUTO_TEST_RUNNING"
            exporting -> "EXPORT_RUNNING"
            !preview.hasPointerCapture() || !hasWindowFocus() || !active ||
                !InputDevice.getDeviceIds().any { InputDevice.getDevice(it)?.descriptor == mouseDescriptor } -> "INACTIVE"
            commands.size != 1 -> "COMBINATION_BLOCKED"
            controller.isRecording() && commands.single() in listOf(HidCommandGate.Command.LEFT, HidCommandGate.Command.DOWN) -> "RECORDING"
            !controller.readyForCommand() && !(commands.single() == HidCommandGate.Command.CLICK && controller.isRecording() && !lastCameraState.capturing) -> "CAMERA_BUSY"
            else -> "EXECUTED"
        }
        hidStatus = "$name: $decision"
        recorder.record(JSONObject().put("kind", "hidCommand").put("command", name).put("decision", decision))
        if (decision == "EXECUTED") when (commands.single()) {
            HidCommandGate.Command.LEFT -> controller.cycleLens()
            HidCommandGate.Command.UP -> controller.cycleZoom()
            HidCommandGate.Command.RIGHT -> controller.cycleExposure()
            HidCommandGate.Command.CLICK -> controller.triggerCapture()
            HidCommandGate.Command.DOWN -> controller.toggleCaptureMode()
        }
        renderState()
    }

    override fun dispatchGenericMotionEvent(event: MotionEvent): Boolean {
        if (event.isFromSource(InputDevice.SOURCE_MOUSE)) { handleMouse(event, "generic"); if(wantsCapture) return true }
        return super.dispatchGenericMotionEvent(event)
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        if (event.isFromSource(InputDevice.SOURCE_MOUSE)) { handleMouse(event, "touch"); if(wantsCapture) return true }
        val result = super.dispatchTouchEvent(event)
        if (initialized && wantsCapture && event.actionMasked == MotionEvent.ACTION_UP) preview.post { capturePointer() }
        return result
    }

    override fun onInputDeviceAdded(deviceId: Int) = deviceChanged(deviceId)
    override fun onInputDeviceChanged(deviceId: Int) = deviceChanged(deviceId)
    override fun onInputDeviceRemoved(deviceId: Int) = deviceChanged(deviceId)

    private fun mouseConnected() = InputDevice.getDeviceIds().any { InputDevice.getDevice(it)?.descriptor == mouseDescriptor }

    private fun deviceChanged(deviceId: Int) {
        if (!initialized) return
        recorder.record(JSONObject().put("kind", "inputDeviceChanged").put("deviceId", deviceId))
        val connected = InputDevice.getDeviceIds().any { InputDevice.getDevice(it)?.descriptor == mouseDescriptor }
        if (wantsCapture && !connected) {
            resetInput("mouseDisconnected"); preview.releasePointerCapture()
            hidStatus = "GEHÄUSE GETRENNT"
        } else if (wantsCapture && connected) preview.post { capturePointer() }
        renderState()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString("exportGroupOnly", exportGroupOnly)
        super.onSaveInstanceState(outState)
    }

    override fun onPause() {
        inputManager.unregisterInputDeviceListener(this)
        resetInput("paused")
        super.onPause()
    }

    override fun onDestroy() {
        automated.cancel()
        initialized = false
        inputManager.unregisterInputDeviceListener(this)
        handler.removeCallbacksAndMessages(null)
        capabilityWorker.shutdownNow()
        recorder.close()
        super.onDestroy()
    }

    override fun onStop() {
        automated.cancel("Test durch Verlassen der Kamera beendet. Gespeicherte Fotos bleiben erhalten.")
        active = false
        handler.removeCallbacks(healthTick)
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
