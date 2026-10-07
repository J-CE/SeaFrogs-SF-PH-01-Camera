package de.jce.seafrogs

import android.Manifest
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

/** Photo UI and lens commands, independent of the future HID adapter. */
class CameraActivity : ComponentActivity() {
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
            textSize = 22f
            gravity = Gravity.CENTER
            setTextColor(android.graphics.Color.WHITE)
            setPadding(dp(8), dp(8), dp(8), dp(8))
        }
        cameraPanel.addView(status)
        preview = PreviewView(this).apply {
            scaleType = PreviewView.ScaleType.FIT_CENTER
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
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
        root.addView(cameraPanel, if (landscape)
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f)
        else LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        root.addView(buttonsPanel, LinearLayout.LayoutParams(
            if (landscape) dp(244) else LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT))
        setContentView(root)
        controller = PhotoCameraController(this) { state ->
            if (!isDestroyed && active) {
                status.text = "PHOTO | ${state.lens.label} | ${state.zoomLabel} | EV ${state.exposureLabel} | JPEG\n" +
                    state.resolution + "\n" + state.message
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

    override fun onStop() {
        active = false
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
