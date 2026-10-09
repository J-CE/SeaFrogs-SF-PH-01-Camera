package de.jce.seafrogs

import android.Manifest
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.hardware.input.InputManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.view.Gravity
import android.view.InputDevice
import android.view.MotionEvent
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
import org.json.JSONObject

/** Touch and selected HID device invoke the same guarded camera commands. */
class CameraActivity : ComponentActivity(), InputManager.InputDeviceListener {
    private lateinit var cameraController: PhotoCameraController
    private lateinit var previewView: PreviewView
    private lateinit var cameraScreen: CameraScreenLayout
    private lateinit var cameraToolbar: CameraToolbarLayout
    private lateinit var noticeText: TextView
    private lateinit var setupDetails: TextView
    private lateinit var cameraStatusText: TextView
    private lateinit var lensButton: Button
    private lateinit var zoomButton: Button
    private lateinit var exposureButton: Button
    private lateinit var shutterButton: Button
    private lateinit var captureModeButton: Button
    private lateinit var setupButton: Button
    private val setupRows = mutableListOf<android.view.View>()
    private var setupVisible = false
    private var orientationBeforeRecording: Int? = null
    private val divePreferences by lazy { getSharedPreferences("dive", MODE_PRIVATE) }
    private val diveControls = mutableListOf<android.view.View>()
    private lateinit var buttonsView: android.widget.ScrollView
    private val motionDecoder = MouseMotionDecoder()
    private var pendingMouseMode: Boolean? = null
    private val applyMouseMode = Runnable {
        val mode = pendingMouseMode
        pendingMouseMode = null
        if (mode != null && uiInitialized && activityResumed && !isDestroyed)
            setMouseMode(mode, fromChord = true)
    }

    private fun queueMouseMode(capture: Boolean) {
        pendingMouseMode = capture
        mainHandler.removeCallbacks(applyMouseMode)
        mainHandler.post(applyMouseMode)
    }

    private val healthTick =
        object : Runnable {
            override fun run() {
                if (activityResumed) {
                    renderState()
                    mainHandler.postDelayed(this, 2000)
                }
            }
        }

    private fun applyDiveSettings() {
        window.attributes =
            window.attributes.apply {
                screenBrightness = divePreferences.getInt("brightness", 70).coerceIn(1, 100) / 100f
            }
        requestedOrientation =
            when (divePreferences.getInt("orientation", 0)) {
                1 -> android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                2 -> android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                3 -> android.content.pm.ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE
                else -> android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            }
    }

    private fun setMouseMode(capture: Boolean, fromChord: Boolean = false) {
        cameraControlRequested = capture
        divePreferences.edit().putBoolean("capture", capture).apply()
        resetInput("mode:$capture", preserveChord = fromChord)
        if (capture) {
            setupVisible = false
            setupRows.forEach { it.visibility = android.view.View.GONE }
            setupButton.text = "SETUP"
            previewView.post { capturePointer() }
        } else previewView.releasePointerCapture()
        hidStatus = if (capture) "STEUERUNG AKTIV" else "KLASSISCHE MAUS"
        renderState()
    }

    private fun diveSettings() {
        val panel =
            LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(16), dp(8), dp(16), dp(8))
            }
        val label = TextView(this)
        val brightness =
            android.widget.SeekBar(this).apply {
                max = 99
                progress = divePreferences.getInt("brightness", 70) - 1
            }
        label.text = "Displayhelligkeit: ${brightness.progress+1}%"
        brightness.setOnSeekBarChangeListener(
            object : android.widget.SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(
                    bar: android.widget.SeekBar?,
                    value: Int,
                    user: Boolean,
                ) {
                    label.text = "Displayhelligkeit: ${value+1}%"
                    window.attributes =
                        window.attributes.apply { screenBrightness = (value + 1) / 100f }
                }

                override fun onStartTrackingTouch(bar: android.widget.SeekBar?) {}

                override fun onStopTrackingTouch(bar: android.widget.SeekBar?) {}
            }
        )
        panel.addView(label)
        panel.addView(brightness)
        panel.addView(TextView(this).apply { text = "Ausrichtung" })
        val direction =
            android.widget.Spinner(this).apply {
                adapter =
                    android.widget.ArrayAdapter(
                        this@CameraActivity,
                        android.R.layout.simple_spinner_dropdown_item,
                        listOf("Automatisch", "Hochformat", "Querformat", "Querformat umgekehrt"),
                    )
                setSelection(divePreferences.getInt("orientation", 0))
            }
        panel.addView(direction)
        panel.addView(
            TextView(this).apply {
                text =
                    "Display bleibt an. Fotos: Pictures/SeaFrogs · Video: Movies/SeaFrogs. Links + Hoch: Steuerung. Rechts + Runter: klassische Maus."
            }
        )
        AlertDialog.Builder(this)
            .setTitle("Tauchprofil")
            .setView(panel)
            .setNegativeButton("Abbrechen") { _, _ -> applyDiveSettings() }
            .setOnCancelListener { applyDiveSettings() }
            .setPositiveButton("Speichern") { _, _ ->
                divePreferences
                    .edit()
                    .putInt("brightness", brightness.progress + 1)
                    .putInt("orientation", direction.selectedItemPosition)
                    .apply()
                applyDiveSettings()
            }
            .show()
    }

    private fun cycleSettings() {
        val panel =
            LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(16), dp(8), dp(16), dp(8))
            }
        fun field(title: String, values: List<Float>): android.widget.EditText {
            panel.addView(TextView(this).apply { text = title })
            return android.widget.EditText(this).apply {
                setText(values.joinToString(";"))
                isSingleLine = true
                panel.addView(this)
            }
        }
        val mainZoomField = field("Hauptkamera-Zoom", CameraControlCycles.zoomRatios)
        val ultrawideZoomField =
            field("UW-Zoom relativ zu 0,5×", CameraControlCycles.ultrawideZoomRatios)
        val exposureCycleField = field("EV-Zyklus", CameraControlCycles.exposureValues)
        val dialog =
            AlertDialog.Builder(this)
                .setTitle("Zyklen: Werte mit Semikolon trennen")
                .setView(panel)
                .setNegativeButton("Abbrechen", null)
                .setPositiveButton("Speichern", null)
                .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                fun parse(text: String, min: Float, max: Float): List<Float>? =
                    runCatching {
                            text
                                .split(';')
                                .map { it.trim().replace(',', '.').toFloat() }
                                .also {
                                    require(
                                        it.size in 2..8 &&
                                            it.all { v -> v.isFinite() && v in min..max } &&
                                            it.distinct().size == it.size
                                    )
                                }
                        }
                        .getOrNull()
                val mainZoomValues = parse(mainZoomField.text.toString(), 1f, 10f)
                val ultrawideZoomValues = parse(ultrawideZoomField.text.toString(), 1f, 10f)
                val exposureValues = parse(exposureCycleField.text.toString(), -5f, 5f)
                if (
                    mainZoomValues == null ||
                        ultrawideZoomValues == null ||
                        exposureValues == null ||
                        mainZoomValues.first() != 1f ||
                        ultrawideZoomValues.first() != 1f ||
                        exposureValues.first() != 0f
                ) {
                    android.widget.Toast.makeText(
                            this,
                            "2–8 eindeutige Werte; Zoom beginnt mit 1, EV mit 0",
                            android.widget.Toast.LENGTH_LONG,
                        )
                        .show()
                } else {
                    CameraControlCycles.zoomRatios = mainZoomValues
                    CameraControlCycles.ultrawideZoomRatios = ultrawideZoomValues
                    CameraControlCycles.exposureValues = exposureValues
                    divePreferences
                        .edit()
                        .putString("mainZoom", mainZoomValues.joinToString(";"))
                        .putString("uwZoom", ultrawideZoomValues.joinToString(";"))
                        .putString("ev", exposureValues.joinToString(";"))
                        .apply()
                    dialog.dismiss()
                }
            }
        }
        dialog.show()
    }

    private lateinit var diagnosisButton: Button
    private lateinit var restartButton: Button
    private lateinit var orientation: OrientationEventListener
    private lateinit var inputManager: InputManager
    private lateinit var recorder: EventRecorder
    private lateinit var automatedCameraTest: AutomatedCameraTest
    private lateinit var photoFormatButton: Button
    private lateinit var exposureSetup: Button
    private var exportGroupOnly: String? = null
    private var exporting = false
    private var exportStatus = ""
    private var autoStatus = ""
    private var lastNoticeMessage = ""
    private var noticeUntil = 0L
    private var lastCameraState = PhotoCameraState()
    private lateinit var mouseButton: Button
    private val capabilityWorker = java.util.concurrent.Executors.newSingleThreadExecutor()
    private var capabilityReport: String? = null
    private var capabilityQueryStarted = false
    private val mainHandler = Handler(Looper.getMainLooper())
    private val hidCommandGate = HidCommandGate()
    private var selectedMouseDescriptor: String? = null
    private var cameraControlRequested = false
    private var uiInitialized = false
    private var primaryButtonPressed = false
    private var hidStatus = "MAUS AUS"
    private var downCount = 0
    private val finishBurst = Runnable { finishInput() }
    private val exportRequest =
        registerForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri
            ->
            val groupOnly = exportGroupOnly
            exportGroupOnly = null
            if (uri != null)
                recorder.export(
                    uri,
                    automatedCameraTest.photos(groupOnly),
                    automatedCameraTest.report(groupOnly),
                    capabilityReport,
                ) { message, busy ->
                    mainHandler.post {
                        if (!isDestroyed) {
                            exportStatus = message
                            exporting = busy
                            renderState()
                        }
                    }
                }
        }
    private var activityResumed = false
    private var sessionStarted = false
    private val permissions: Array<String>
        get() =
            if (Build.VERSION.SDK_INT <= 28)
                arrayOf(Manifest.permission.CAMERA, Manifest.permission.WRITE_EXTERNAL_STORAGE)
            else arrayOf(Manifest.permission.CAMERA)

    private val permissionRequest =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            if (hasPermissions()) startCamera() else showPermissionRequired()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        CrashReport.install(applicationContext)
        super.onCreate(savedInstanceState)
        exportGroupOnly = savedInstanceState?.getString("exportGroupOnly")
        inputManager = getSystemService(InputManager::class.java)
        selectedMouseDescriptor = divePreferences.getString("mouse", null)
        cameraControlRequested = divePreferences.getBoolean("capture", false)
        fun restored(key: String, defaults: List<Float>) =
            runCatching {
                    divePreferences
                        .getString(key, null)
                        ?.split(';')
                        ?.map { it.toFloat() }
                        ?.takeIf { it.size in 2..8 && it.all(Float::isFinite) } ?: defaults
                }
                .getOrDefault(defaults)
        CameraControlCycles.zoomRatios = restored("mainZoom", listOf(1f, 1.5f, 3f, 5f))
        CameraControlCycles.ultrawideZoomRatios = restored("uwZoom", listOf(1f, 1.5f, 3f))
        CameraControlCycles.exposureValues = restored("ev", listOf(0f, 1f, 2f, -1f, -2f))
        applyDiveSettings()
        recorder =
            EventRecorder(applicationContext) { message ->
                mainHandler.post {
                    if (!isDestroyed)
                        android.widget.Toast.makeText(
                                this,
                                message,
                                android.widget.Toast.LENGTH_LONG,
                            )
                            .show()
                }
            }
        recorder.record(
            JSONObject()
                .put("kind", "session")
                .put("appVersion", BuildConfig.VERSION_NAME)
                .put("model", Build.MODEL)
                .put("androidBuild", Build.FINGERPRINT)
        )
        val landscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        cameraScreen =
            CameraScreenLayout(this).apply {
                setBackgroundColor(android.graphics.Color.BLACK)
                setOnApplyWindowInsetsListener { view, insets ->
                    view.setPadding(
                        insets.systemWindowInsetLeft,
                        insets.systemWindowInsetTop,
                        insets.systemWindowInsetRight,
                        insets.systemWindowInsetBottom,
                    )
                    insets
                }
            }
        val buttonsPanel = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        cameraStatusText =
            TextView(this).apply {
                textSize = 18f
                gravity = Gravity.CENTER
                setTextColor(android.graphics.Color.WHITE)
                setPadding(dp(8), dp(4), dp(8), dp(4))
                setBackgroundColor(0xB0000000.toInt())
                maxLines = 3
            }
        previewView =
            PreviewView(this).apply {
                scaleType = PreviewView.ScaleType.FIT_CENTER
                implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                isFocusable = true
                isFocusableInTouchMode = true
                setOnCapturedPointerListener { _, event ->
                    handleMouse(event, "captured")
                    true
                }
            }
        lensButton =
            Button(this).apply {
                text = "KAMERA: 1×"
                textSize = 24f
                minHeight = dp(64)
                isEnabled = false
                setOnClickListener { cameraController.cycleLens() }
            }
        buttonsPanel.addView(lensButton)
        val controls = LinearLayout(this)
        zoomButton =
            Button(this).apply {
                text = "ZOOM: 1×"
                textSize = 22f
                minHeight = dp(64)
                isEnabled = false
                setOnClickListener { cameraController.cycleZoom() }
            }
        exposureButton =
            Button(this).apply {
                text = "EV: 0"
                textSize = 22f
                minHeight = dp(64)
                isEnabled = false
                setOnClickListener { cameraController.cycleExposure() }
            }
        controls.addView(
            zoomButton,
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f),
        )
        controls.addView(
            exposureButton,
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f),
        )
        buttonsPanel.addView(controls)
        shutterButton =
            Button(this).apply {
                text = "FOTO"
                textSize = 28f
                minHeight = dp(88)
                isEnabled = false
                setOnClickListener { cameraController.triggerCapture() }
            }
        buttonsPanel.addView(shutterButton)
        captureModeButton =
            Button(this).apply {
                text = "MODUS: FOTO"
                minHeight = dp(56)
                setOnClickListener { cameraController.toggleCaptureMode() }
            }
        buttonsPanel.addView(captureModeButton)
        setupButton =
            Button(this).apply {
                text = "SETUP"
                minHeight = dp(48)
                setOnClickListener {
                    if (!lastCameraState.capturing && !lastCameraState.recording) {
                        setupVisible = !setupVisible
                        setupRows.forEach {
                            it.visibility =
                                if (setupVisible) android.view.View.VISIBLE
                                else android.view.View.GONE
                        }
                        text = if (setupVisible) "SETUP SCHLIESSEN" else "SETUP"
                        renderState()
                    }
                }
            }
        buttonsPanel.addView(setupButton)
        diveControls.addAll(listOf(lensButton, controls, shutterButton, captureModeButton))
        val profile =
            Button(this).apply {
                text = "TAUCHPROFIL"
                setOnClickListener { diveSettings() }
            }
        val cycles =
            Button(this).apply {
                text = "ZOOM / EV ZYKLEN"
                setOnClickListener { cycleSettings() }
            }
        val whiteBalanceButton =
            Button(this).apply {
                text = "WEISSABGLEICH"
                setOnClickListener {
                    if (lastCameraState.ready && !lastCameraState.recording)
                        AlertDialog.Builder(this@CameraActivity)
                            .setTitle("Weißabgleich")
                            .setItems(
                                cameraController
                                    .whiteBalanceChoices()
                                    .map { it.first }
                                    .toTypedArray()
                            ) { _, index ->
                                cameraController.setWhiteBalance(
                                    cameraController.whiteBalanceChoices()[index].second
                                )
                            }
                            .show()
                }
            }
        val strength =
            Button(this).apply {
                text = "UW-WB STÄRKE"
                setOnClickListener {
                    if (lastCameraState.ready && !lastCameraState.recording)
                        AlertDialog.Builder(this@CameraActivity)
                            .setTitle("Unterwasser-Korrektur")
                            .setSingleChoiceItems(
                                arrayOf("25%", "50%", "75%", "100%"),
                                listOf(25, 50, 75, 100)
                                    .indexOf(cameraController.whiteBalanceStrength()),
                            ) { dialog, index ->
                                cameraController.setWhiteBalanceStrength(
                                    listOf(25, 50, 75, 100)[index]
                                )
                                dialog.dismiss()
                            }
                            .setNegativeButton("Zurück", null)
                            .show()
                }
            }
        photoFormatButton =
            Button(this).apply {
                text = "FORMAT: JPEG"
                setOnClickListener {
                    AlertDialog.Builder(this@CameraActivity)
                        .setTitle("Fotoformat vor dem Tauchgang")
                        .setItems(arrayOf("JPEG only", "RAW + JPEG (DNG)")) { _, choice ->
                            cameraController.setRawEnabled(choice == 1)
                        }
                        .show()
                }
            }
        exposureSetup =
            Button(this).apply {
                text = "ISO: AUTO"
                setOnClickListener { chooseExposureLimits() }
            }
        listOf(profile, photoFormatButton, exposureSetup, cycles, whiteBalanceButton, strength)
            .forEach {
                buttonsPanel.addView(it)
                setupRows.add(it)
            }
        val videoSettings =
            Button(this).apply {
                text = "VIDEO: 4K30 / 4K60"
                setOnClickListener {
                    if (!lastCameraState.recording && lastCameraState.ready)
                        AlertDialog.Builder(this@CameraActivity)
                            .setTitle("Video vor dem Tauchgang")
                            .setSingleChoiceItems(
                                arrayOf("4K30 ohne Ton", "4K60 ohne Ton"),
                                if (cameraController.savedVideoFps() == 60) 1 else 0,
                            ) { dialog, choice ->
                                cameraController.setVideoFps(if (choice == 0) 30 else 60)
                                dialog.dismiss()
                            }
                            .setNegativeButton("Zurück", null)
                            .show()
                }
            }
        buttonsPanel.addView(videoSettings)
        setupRows.add(videoSettings)
        val footer = LinearLayout(this)
        restartButton =
            Button(this).apply {
                text = "Erneut starten"
                minHeight = dp(56)
                setOnClickListener {
                    if (hasPermissions()) {
                        sessionStarted = false
                        startCamera()
                    } else requestPermissionOrSettings()
                }
            }
        diagnosisButton =
            Button(this).apply {
                text = "HID-Diagnose"
                minHeight = dp(56)
                setOnClickListener {
                    startActivity(Intent(this@CameraActivity, DiagnosticActivity::class.java))
                }
            }
        footer.addView(
            restartButton,
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f),
        )
        footer.addView(
            diagnosisButton,
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f),
        )
        buttonsPanel.addView(footer)
        val crashDetails =
            Button(this).apply {
                text = "LETZTER ABSTURZ"
                setOnClickListener { CrashReport.show(this@CameraActivity) }
            }
        buttonsPanel.addView(crashDetails)
        setupRows.add(crashDetails)
        mouseButton =
            Button(this).apply {
                text = "MAUS AUS"
                setOnClickListener { chooseMouse() }
            }
        buttonsPanel.addView(mouseButton)
        setupRows.addAll(listOf(footer, mouseButton))
        setupRows.forEach { it.visibility = android.view.View.GONE }
        // Setup overlays the image; opening it never shrinks the photo viewport.
        val closeSetup =
            Button(this).apply {
                text = "SETUP SCHLIESSEN"
                setOnClickListener {
                    setupVisible = false
                    renderState()
                }
            }
        buttonsPanel.addView(closeSetup, 0)
        setupDetails =
            TextView(this).apply {
                textSize = 14f
                setTextColor(android.graphics.Color.WHITE)
                setPadding(dp(12), dp(8), dp(12), dp(8))
            }
        buttonsPanel.addView(setupDetails, 1)
        cameraToolbar = CameraToolbarLayout(this)
        listOf(
                lensButton,
                zoomButton,
                exposureButton,
                captureModeButton,
                shutterButton,
                setupButton,
            )
            .forEach { button ->
                (button.parent as? android.view.ViewGroup)?.removeView(button)
                button.textSize = 16f
                button.minHeight = dp(48)
                button.minimumHeight = dp(48)
                button.minWidth = 0
                button.minimumWidth = 0
                button.setPadding(dp(4), 0, dp(4), 0)
                cameraToolbar.addView(
                    button,
                    android.view.ViewGroup.LayoutParams(
                        android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                        dp(48),
                    ),
                )
            }
        diveControls.clear()
        diveControls.addAll(
            listOf(lensButton, zoomButton, exposureButton, captureModeButton, shutterButton)
        )
        buttonsView =
            android.widget.ScrollView(this).apply {
                setBackgroundColor(0xF0000000.toInt())
                addView(buttonsPanel)
            }
        noticeText =
            TextView(this).apply {
                textSize = 22f
                gravity = Gravity.CENTER
                setTextColor(android.graphics.Color.WHITE)
                setBackgroundColor(0xD0000000.toInt())
                setPadding(dp(8), dp(6), dp(8), dp(6))
                visibility = android.view.View.GONE
            }
        cameraScreen.attach(previewView, cameraStatusText, noticeText, cameraToolbar, buttonsView)
        setContentView(cameraScreen)
        cameraController =
            PhotoCameraController(this, { recorder.record(it) }) { state ->
                lastCameraState = state
                if (::automatedCameraTest.isInitialized) renderState()
            }
        automatedCameraTest =
            AutomatedCameraTest(this, cameraController, { recorder.record(it) }) { text, running ->
                autoStatus = text
                if (running) resetInput("autoTest")
                renderState()
            }
        uiInitialized = true
        orientation =
            object : OrientationEventListener(this) {
                override fun onOrientationChanged(angle: Int) {
                    if (
                        angle == ORIENTATION_UNKNOWN ||
                            divePreferences.getInt("orientation", 0) != 0
                    )
                        return
                    // JPEG orientation follows the handset even when Android
                    // rotation lock keeps the preview/UI in portrait.
                    cameraController.setRotation(
                        when (angle) {
                            in 45..134 -> Surface.ROTATION_270
                            in 135..224 -> Surface.ROTATION_180
                            in 225..314 -> Surface.ROTATION_90
                            else -> Surface.ROTATION_0
                        }
                    )
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
        activityResumed = true
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        orientation.enable()
        mainHandler.removeCallbacks(healthTick)
        mainHandler.post(healthTick)
        previewView.post { capturePointer() }
        if (hasPermissions()) startCamera() else showPermissionRequired()
    }

    override fun onResume() {
        super.onResume()
        inputManager.registerInputDeviceListener(this, mainHandler)
        // Also handles permission changes made in the Android app settings.
        if (hasPermissions()) startCamera() else showPermissionRequired()
    }

    private fun startCamera() {
        if (!activityResumed || sessionStarted) return
        readCapabilities()
        sessionStarted = true
        restartButton.text = "Erneut starten"
        cameraController.setRotation(
            when (divePreferences.getInt("orientation", 0)) {
                1 -> Surface.ROTATION_0
                2 -> Surface.ROTATION_90
                3 -> Surface.ROTATION_270
                else -> previewView.display?.rotation ?: Surface.ROTATION_0
            }
        )
        cameraController.start(this, previewView)
    }

    private fun readCapabilities() {
        if (capabilityQueryStarted) return
        capabilityQueryStarted = true
        capabilityWorker.execute {
            val snapshot = CameraCapabilityReport.collect(applicationContext)
            val encoded = snapshot.toString(2)
            mainHandler.post {
                if (isDestroyed) return@post
                capabilityReport = encoded
                recorder.record(
                    JSONObject()
                        .put("kind", "cameraCapabilityQueryFinished")
                        .put("cameraCount", snapshot.optJSONArray("cameras")?.length() ?: 0)
                        .put("errors", snapshot.optJSONArray("errors"))
                )
                renderState()
            }
        }
    }

    private fun hasPermissions() =
        permissions.all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }

    private fun showPermissionRequired() {
        zoomButton.isEnabled = false
        exposureButton.isEnabled = false
        lensButton.isEnabled = false
        shutterButton.isEnabled = false
        cameraStatusText.text =
            "KAMERAZUGRIFF FEHLT\nBerechtigung freigeben. HID-Diagnose bleibt verfügbar."
        restartButton.text = "Zugriff freigeben"
        setupVisible = true
        setupRows.forEach { it.visibility = android.view.View.VISIBLE }
        buttonsView.visibility = android.view.View.VISIBLE
        cameraToolbar.visibility = android.view.View.GONE
        cameraScreen.requestLayout()
    }

    private fun requestPermissionOrSettings() {
        val denied =
            permissions.filter {
                ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
            }
        if (denied.any { shouldShowRequestPermissionRationale(it) }) {
            permissionRequest.launch(permissions)
        } else {
            startActivity(
                Intent(
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:$packageName"),
                )
            )
        }
    }

    private fun renderState() {
        if (isDestroyed || !activityResumed) return
        if (!hasPermissions()) {
            showPermissionRequired()
            return
        }
        val state = lastCameraState
        val testing = automatedCameraTest.running || exporting
        if (state.recording && orientationBeforeRecording == null) {
            orientationBeforeRecording = requestedOrientation
            requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LOCKED
        } else if (!state.recording) {
            orientationBeforeRecording?.let { requestedOrientation = it }
            orientationBeforeRecording = null
        }
        if (state.recording && setupVisible) {
            setupVisible = false
            setupRows.forEach { it.visibility = android.view.View.GONE }
            setupButton.text = "SETUP"
        }
        val cameraMessage =
            if (state.message == "Bereit" && !state.ready) "Warte auf Vorschau/Fokus …"
            else state.message
        val free =
            android.os
                .StatFs(android.os.Environment.getExternalStorageDirectory().path)
                .availableBytes
        val battery =
            getSystemService(android.os.BatteryManager::class.java)
                .getIntProperty(android.os.BatteryManager.BATTERY_PROPERTY_CAPACITY)
        val hot =
            Build.VERSION.SDK_INT >= 29 &&
                getSystemService(android.os.PowerManager::class.java).currentThermalStatus >=
                    android.os.PowerManager.THERMAL_STATUS_SEVERE
        val warnings = buildList {
            if (battery in 0..10) add("AKKU KRITISCH")
            if (hot) add("ÜBERHITZUNG")
            if (free < 256L * 1024 * 1024) add("SPEICHER KNAPP")
            if (selectedMouseDescriptor != null && !mouseConnected()) add("GEHÄUSE GETRENNT")
        }
        val wbLabel = cameraController.whiteBalanceLabel()
        val wbShort =
            wbLabel.substringBefore(" · ").replace("Unterwasser ", "").replace("Videolicht ", "") +
                (if (wbLabel.startsWith("Unterwasser "))
                    " ${cameraController.whiteBalanceStrength()}%"
                else "")
        cameraStatusText.text =
            "${if(cameraControlRequested) "HID" else "MAUS"} | ${if(state.videoMode) "VIDEO 4K${state.videoFps}" else "PHOTO"} | ${state.lens.label} | ${state.zoomLabel} | EV ${state.exposureLabel}\n" +
                (if (state.videoMode) "OHNE TON" else state.photoFormat) +
                " · WB $wbShort · Akku ${if(battery in 0..100) "$battery%" else "?"} · ${String.format(java.util.Locale.GERMAN,"%.1f",free/1073741824.0)} GB"
        val notices = warnings.toMutableList()
        if (state.recording) notices.add(0, "● AUFNAHME")
        val now = SystemClock.uptimeMillis()
        if (cameraMessage != lastNoticeMessage) {
            lastNoticeMessage = cameraMessage
            noticeUntil = now + 3000L
        }
        val captureWarning =
            cameraMessage.substringAfter("\nWARNUNG:", "").takeIf { it.isNotBlank() }
        val cameraError =
            captureWarning == null &&
                listOf("fehl", "zu wenig", "nicht verfügbar", "nicht unterstützt").any {
                    cameraMessage.contains(it, true)
                }
        if (
            cameraMessage != "Bereit" &&
                cameraMessage.isNotBlank() &&
                (!state.ready ||
                    state.capturing ||
                    state.recording ||
                    cameraError ||
                    now < noticeUntil)
        )
            notices.add(cameraMessage.substringBefore('\n'))
        captureWarning?.let { notices.add("WARNUNG: $it") }
        if (wbLabel.contains("WB NICHT BESTÄTIGT")) notices.add("WB NICHT BESTÄTIGT")
        if (wbLabel.contains("WB ABWEICHEND") || wbLabel.contains("Profil nicht verfügbar"))
            notices.add("WB BEGRENZT / NICHT VERFÜGBAR")
        if (exportStatus.isNotBlank()) notices.add(exportStatus)
        if (autoStatus.isNotBlank()) notices.add(autoStatus)
        noticeText.text = notices.joinToString("\n")
        noticeText.visibility =
            if (notices.isEmpty()) android.view.View.GONE else android.view.View.VISIBLE
        noticeText.setTextColor(
            when {
                state.recording || warnings.isNotEmpty() || cameraError ->
                    android.graphics.Color.RED
                captureWarning != null -> android.graphics.Color.YELLOW
                else -> android.graphics.Color.WHITE
            }
        )
        setupDetails.text = "${state.resolution}\nWB $wbLabel\n${state.diagnostics}\n$hidStatus"
        val bars = androidx.core.view.WindowCompat.getInsetsController(window, window.decorView)
        bars.systemBarsBehavior =
            androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        bars.hide(androidx.core.view.WindowInsetsCompat.Type.systemBars())
        diveControls.forEach { it.visibility = android.view.View.VISIBLE }
        setupRows.forEach {
            it.visibility = if (setupVisible) android.view.View.VISIBLE else android.view.View.GONE
        }
        setupButton.text = "SETUP"
        cameraToolbar.visibility = android.view.View.VISIBLE
        // The toolbar remains visible in both mouse modes and while setup is open.
        buttonsView.visibility =
            if (setupVisible) android.view.View.VISIBLE else android.view.View.GONE
        if (cameraScreen.videoMode != state.videoMode) {
            cameraScreen.videoMode = state.videoMode
            cameraScreen.requestLayout()
        }
        photoFormatButton.text = "FORMAT: ${state.photoFormat}"
        photoFormatButton.isEnabled = state.ready && !state.videoMode && !testing
        exposureSetup.isEnabled = state.ready && !state.videoMode && !testing
        exposureSetup.text = if (state.exposureLimits.enabled) "ISO / DIGITAL" else "ISO: AUTO"
        mouseButton.text =
            if (cameraControlRequested) "STEUERUNG AUS / KLASSISCHE MAUS"
            else "SEAFROGS-MAUS AUSWÄHLEN"
        mouseButton.isEnabled = !state.capturing && !testing
        zoomButton.isEnabled = state.ready && !testing
        zoomButton.text = state.zoomLabel
        exposureButton.isEnabled = state.exposureAdjustable && state.exposureSupported && !testing
        exposureButton.text =
            if (state.exposureSupported) "EV: ${state.exposureLabel}" else "EV: N/V"
        lensButton.isEnabled = state.ready && !state.recording && !testing
        lensButton.text = state.lens.label
        shutterButton.isEnabled = (state.ready || (state.recording && !state.capturing)) && !testing
        shutterButton.text =
            if (state.capturing) "WARTE …"
            else if (state.recording) "VIDEO STOP"
            else if (!state.ready) "WARTE …" else if (state.videoMode) "VIDEO START" else "FOTO"
        captureModeButton.text = if (state.videoMode) "→ FOTO" else "→ VIDEO"
        captureModeButton.isEnabled = state.ready && !state.recording && !testing
        setupButton.isEnabled = !state.capturing && !state.recording && !testing
        cameraStatusText.setTextColor(
            if (state.recording || warnings.isNotEmpty() || cameraError) android.graphics.Color.RED
            else android.graphics.Color.WHITE
        )
        diagnosisButton.isEnabled = !state.capturing && !state.recording && !testing
        restartButton.isEnabled = !state.capturing && !state.recording && !testing
    }

    private fun beginAutomated(
        macro: Boolean,
        raw: Boolean = false,
        iso: Boolean = false,
        processing: Boolean = false,
        libraries: Boolean = false,
        fusion: Boolean = false,
    ) {
        if (
            libraries &&
                android.os
                    .StatFs(android.os.Environment.getExternalStorageDirectory().path)
                    .availableBytes < 1_000_000_000L
        ) {
            AlertDialog.Builder(this)
                .setTitle("Speicher reicht nicht")
                .setMessage("Für RAW-Serie und ZIP mindestens 1 GB freihalten.")
                .setPositiveButton("OK", null)
                .show()
            return
        }
        val message =
            if (fusion)
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
            else
                "Lege eine bedruckte Seite etwa 50 cm vor die Kameralinse. Stütze das Handy ab und halte das Licht konstant.\n\nDie App übernimmt Kamera, Zoom, EV, verfügbare Bildverarbeitung und Aufnahmen. Während des Tests nichts bewegen."
        AlertDialog.Builder(this)
            .setTitle(
                if (fusion) "Automatischer MIT-Mehrbildtest"
                else if (libraries) "Automatischer Bibliotheks-Datentest"
                else if (processing) "Automatischer Qualitätstest"
                else if (iso) "Automatischer ISO-Vergleich"
                else if (raw) "Automatischer RAW-Test"
                else if (macro) "Automatischer Macrotest" else "Automatischer Normaltest"
            )
            .setMessage(message)
            .setNegativeButton("Zurück", null)
            .setPositiveButton("Test starten") { _, _ ->
                previewView.releasePointerCapture()
                resetInput("autoTestStart")
                automatedCameraTest.start(macro, raw, iso, processing, libraries, fusion)
            }
            .show()
    }

    private fun chooseExposureLimits() {
        val settings = cameraController.savedExposureLimits()
        val panel =
            LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(20), dp(8), dp(20), dp(8))
            }
        panel.addView(TextView(this).apply { text = "Sensor-ISO-Obergrenze" })
        val iso =
            android.widget.Spinner(this).apply {
                adapter =
                    android.widget.ArrayAdapter(
                        this@CameraActivity,
                        android.R.layout.simple_spinner_dropdown_item,
                        listOf(
                            "Auto (unbegrenzt)",
                            "Maximal ISO 400",
                            "Maximal ISO 800",
                            "Maximal ISO 1600",
                        ),
                    )
                setSelection(ExposureLimits.isoChoices.indexOf(settings.isoCap))
            }
        panel.addView(iso)
        panel.addView(
            TextView(this).apply { text = "Längste Belichtungszeit bei aktiver ISO-Grenze" }
        )
        val time =
            android.widget.Spinner(this).apply {
                adapter =
                    android.widget.ArrayAdapter(
                        this@CameraActivity,
                        android.R.layout.simple_spinner_dropdown_item,
                        listOf("1/30 s", "1/60 s", "1/125 s"),
                    )
                setSelection(ExposureLimits.timeChoices.indexOf(settings.longestTimeNs))
            }
        panel.addView(time)
        panel.addView(TextView(this).apply { text = "Digitale Verstärkung nach RAW" })
        val boost =
            android.widget.Spinner(this).apply {
                adapter =
                    android.widget.ArrayAdapter(
                        this@CameraActivity,
                        android.R.layout.simple_spinner_dropdown_item,
                        listOf("Auto", "Maximal 1×", "Maximal 2×", "Maximal 4×"),
                    )
                setSelection(ExposureLimits.boostChoices.indexOf(settings.boostCap))
            }
        panel.addView(boost)
        panel.addView(
            TextView(this).apply {
                text =
                    "Sensor-Auto begrenzt weder ISO noch Zeit. Digital-Auto übernimmt die gemessene Verstärkung. Bei aktiver Grenze fotografiert die App ohne Extensions in einer dauerhaft offenen Fotositzung. Reichen ISO und Zeit nicht für die gemessene Helligkeit, bleibt das Foto dunkler. Die Grenze gilt für Sensor-ISO. Zusätzliche JPEG-Verstärkung kann einen höheren EXIF-ISO-Wert ergeben. Eine digitale Grenze kann das Bild zusätzlich abdunkeln. Niedrigere Sensor-ISO garantiert keine bessere Aufnahme bei Bewegung."
            }
        )
        AlertDialog.Builder(this)
            .setTitle("Belichtung vor dem Tauchgang")
            .setView(panel)
            .setNegativeButton("Zurück", null)
            .setPositiveButton("Speichern") { _, _ ->
                cameraController.setExposureLimits(
                    ExposureLimits(
                        ExposureLimits.isoChoices[iso.selectedItemPosition],
                        ExposureLimits.timeChoices[time.selectedItemPosition],
                        ExposureLimits.boostChoices[boost.selectedItemPosition],
                    )
                )
            }
            .show()
    }

    private fun chooseMouse() {
        if (cameraControlRequested) {
            setMouseMode(false)
            return
        }
        val devices =
            InputDevice.getDeviceIds()
                .toList()
                .mapNotNull { InputDevice.getDevice(it) }
                .filter { it.supportsSource(InputDevice.SOURCE_MOUSE) }
        if (devices.isEmpty()) {
            hidStatus = "KEINE MAUS VERBUNDEN"
            android.widget.Toast.makeText(this, hidStatus, android.widget.Toast.LENGTH_LONG).show()
            return
        }
        AlertDialog.Builder(this)
            .setTitle("SeaFrogs-Maus auswählen")
            .setItems(devices.map { "${it.name} (${it.id})" }.toTypedArray()) { _, position ->
                val device = devices[position]
                selectedMouseDescriptor = device.descriptor
                divePreferences.edit().putString("mouse", device.descriptor).apply()
                recorder.recordHidEvent { EventEncoder.device(device).put("kind", "selectedMouse") }
                setMouseMode(true)
                previewView.post { capturePointer() }
            }
            .show()
    }

    private fun capturePointer() {
        if (cameraControlRequested && mouseConnected() && activityResumed && hasWindowFocus()) {
            previewView.requestFocus()
            previewView.requestPointerCapture()
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (!uiInitialized) return
        if (hasFocus) previewView.post { capturePointer() } else resetInput("windowFocusLost")
    }

    override fun onPointerCaptureChanged(hasCapture: Boolean) {
        super.onPointerCaptureChanged(hasCapture)
        if (!uiInitialized) return
        resetInput("captureChanged:$hasCapture", preserveChord = true)
        hidStatus =
            if (hasCapture) "MAUS BEREIT"
            else if (cameraControlRequested) "MAUS-CAPTURE FEHLT" else "KLASSISCHE MAUS"
        recorder.recordHidEvent {
            JSONObject().put("kind", "captureChanged").put("captured", hasCapture)
        }
    }

    private fun resetInput(reason: String, preserveChord: Boolean = false) {
        mainHandler.removeCallbacks(finishBurst)
        hidCommandGate.reset(preserveChord)
        if (preserveChord) mainHandler.postDelayed(finishBurst, HidCommandGate.QUIET_MS)
        primaryButtonPressed = false
        motionDecoder.reset()
        if (uiInitialized)
            recorder.recordHidEvent { JSONObject().put("kind", "inputReset").put("reason", reason) }
    }

    private fun handleMouse(event: MotionEvent, route: String) {
        if (!uiInitialized) return
        recorder.recordHidEvent { EventEncoder.motion(event, route) }
        val selected = InputDevice.getDevice(event.deviceId)?.descriptor == selectedMouseDescriptor
        if (automatedCameraTest.running || exporting || !selected || pendingMouseMode != null)
            return
        if (cameraControlRequested && (route != "captured" || !previewView.hasPointerCapture()))
            return
        if (!cameraControlRequested && route == "captured") return
        val now = SystemClock.uptimeMillis()
        if (!cameraControlRequested && event.actionMasked == MotionEvent.ACTION_HOVER_ENTER)
            motionDecoder.position(event.rawX, event.rawY)
        // Button transitions unify DOWN/BUTTON_PRESS and UP/BUTTON_RELEASE.
        val down = event.buttonState and MotionEvent.BUTTON_PRIMARY != 0
        if (cameraControlRequested && down && !primaryButtonPressed)
            hidCommandGate.signal(HidCommandGate.Command.CLICK, now)
        primaryButtonPressed = down
        if (
            event.actionMasked == MotionEvent.ACTION_MOVE ||
                event.actionMasked == MotionEvent.ACTION_HOVER_MOVE
        ) {
            if (route == "captured") {
                for (i in 0 until event.historySize) hidCommandGate.movement(
                    event.getHistoricalX(i),
                    event.getHistoricalY(i),
                    now,
                )
                hidCommandGate.movement(event.x, event.y, now)
            } else {
                // Activity coordinates can differ from raw screen coordinates. Keep
                // the same raw basis across hover targets and include batched deltas.
                val rawOffsetX = event.rawX - event.x
                val rawOffsetY = event.rawY - event.y
                for (i in 0 until event.historySize) {
                    val delta =
                        motionDecoder.sample(
                            event.getHistoricalX(i) + rawOffsetX,
                            event.getHistoricalY(i) + rawOffsetY,
                            event.getHistoricalAxisValue(MotionEvent.AXIS_RELATIVE_X, i),
                            event.getHistoricalAxisValue(MotionEvent.AXIS_RELATIVE_Y, i),
                        )
                    hidCommandGate.movement(delta.first, delta.second, now)
                }
                val delta =
                    motionDecoder.sample(
                        event.rawX,
                        event.rawY,
                        event.getAxisValue(MotionEvent.AXIS_RELATIVE_X),
                        event.getAxisValue(MotionEvent.AXIS_RELATIVE_Y),
                    )
                hidCommandGate.movement(delta.first, delta.second, now)
            }
        }
        hidCommandGate.takeModeSwitch()?.let { mode ->
            queueMouseMode(mode == HidCommandGate.ModeSwitch.CAMERA)
        }
        mainHandler.removeCallbacks(finishBurst)
        mainHandler.postDelayed(finishBurst, HidCommandGate.QUIET_MS)
    }

    private fun finishInput() {
        val commands = hidCommandGate.finish(SystemClock.uptimeMillis()) ?: return
        hidCommandGate.modeSwitch(commands)?.let { mode ->
            queueMouseMode(mode == HidCommandGate.ModeSwitch.CAMERA)
            return
        }
        if (!cameraControlRequested) return
        val name = commands.joinToString("+") { it.name }
        val decision =
            when {
                automatedCameraTest.running -> "AUTO_TEST_RUNNING"
                exporting -> "EXPORT_RUNNING"
                !previewView.hasPointerCapture() ||
                    !hasWindowFocus() ||
                    !activityResumed ||
                    !InputDevice.getDeviceIds().any {
                        InputDevice.getDevice(it)?.descriptor == selectedMouseDescriptor
                    } -> "INACTIVE"
                commands.size != 1 -> "COMBINATION_BLOCKED"
                cameraController.isRecording() &&
                    commands.single() in
                        listOf(HidCommandGate.Command.LEFT, HidCommandGate.Command.DOWN) ->
                    "RECORDING"
                !cameraController.readyForCommand() &&
                    !(commands.single() == HidCommandGate.Command.CLICK &&
                        cameraController.isRecording() &&
                        !lastCameraState.capturing) -> "CAMERA_BUSY"
                else -> "EXECUTED"
            }
        hidStatus = "$name: $decision"
        recorder.recordHidEvent {
            JSONObject().put("kind", "hidCommand").put("command", name).put("decision", decision)
        }
        if (decision == "EXECUTED")
            when (commands.single()) {
                HidCommandGate.Command.LEFT -> cameraController.cycleLens()
                HidCommandGate.Command.UP -> cameraController.cycleZoom()
                HidCommandGate.Command.RIGHT -> cameraController.cycleExposure()
                HidCommandGate.Command.CLICK -> cameraController.triggerCapture()
                HidCommandGate.Command.DOWN -> cameraController.toggleCaptureMode()
            }
        renderState()
    }

    override fun dispatchGenericMotionEvent(event: MotionEvent): Boolean {
        if (event.isFromSource(InputDevice.SOURCE_MOUSE)) {
            handleMouse(event, "generic")
            if (cameraControlRequested) return true
        }
        return super.dispatchGenericMotionEvent(event)
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        if (event.isFromSource(InputDevice.SOURCE_MOUSE)) {
            handleMouse(event, "touch")
            if (cameraControlRequested) return true
        }
        val result = super.dispatchTouchEvent(event)
        if (uiInitialized && cameraControlRequested && event.actionMasked == MotionEvent.ACTION_UP)
            previewView.post { capturePointer() }
        return result
    }

    override fun onInputDeviceAdded(deviceId: Int) = deviceChanged(deviceId)

    override fun onInputDeviceChanged(deviceId: Int) = deviceChanged(deviceId)

    override fun onInputDeviceRemoved(deviceId: Int) = deviceChanged(deviceId)

    private fun mouseConnected() =
        InputDevice.getDeviceIds().any {
            InputDevice.getDevice(it)?.descriptor == selectedMouseDescriptor
        }

    private fun deviceChanged(deviceId: Int) {
        if (!uiInitialized) return
        recorder.recordHidEvent {
            JSONObject().put("kind", "inputDeviceChanged").put("deviceId", deviceId)
        }
        val connected =
            InputDevice.getDeviceIds().any {
                InputDevice.getDevice(it)?.descriptor == selectedMouseDescriptor
            }
        if (cameraControlRequested && !connected) {
            resetInput("mouseDisconnected")
            previewView.releasePointerCapture()
            hidStatus = "GEHÄUSE GETRENNT"
        } else if (cameraControlRequested && connected) previewView.post { capturePointer() }
        renderState()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString("exportGroupOnly", exportGroupOnly)
        super.onSaveInstanceState(outState)
    }

    override fun onPause() {
        mainHandler.removeCallbacks(applyMouseMode)
        pendingMouseMode = null
        inputManager.unregisterInputDeviceListener(this)
        resetInput("paused")
        super.onPause()
    }

    override fun onDestroy() {
        automatedCameraTest.cancel()
        uiInitialized = false
        inputManager.unregisterInputDeviceListener(this)
        mainHandler.removeCallbacksAndMessages(null)
        capabilityWorker.shutdownNow()
        recorder.close()
        super.onDestroy()
    }

    override fun onStop() {
        automatedCameraTest.cancel(
            "Test durch Verlassen der Kamera beendet. Gespeicherte Fotos bleiben erhalten."
        )
        activityResumed = false
        mainHandler.removeCallbacks(healthTick)
        resetInput("stopped")
        previewView.releasePointerCapture()
        sessionStarted = false
        zoomButton.isEnabled = false
        exposureButton.isEnabled = false
        lensButton.isEnabled = false
        shutterButton.isEnabled = false
        orientation.disable()
        cameraController.stop()
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        super.onStop()
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
}
