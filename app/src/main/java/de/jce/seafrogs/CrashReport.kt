package de.jce.seafrogs

import android.app.Activity
import android.app.ActivityManager
import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.widget.ScrollView
import android.widget.TextView
import java.io.File

/** Retain an uncaught exception locally and preserve Android's normal crash handling. */
object CrashReport {
    private var installed = false

    @Synchronized
    fun install(context: Context) {
        if (installed) return
        installed = true
        val applicationContext = context.applicationContext
        val previousExceptionHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching {
                val version =
                    applicationContext.packageManager
                        .getPackageInfo(applicationContext.packageName, 0)
                        .versionName
                File(applicationContext.filesDir, "last-crash.txt")
                    .writeText(
                        "SeaFrogs $version · ${Build.MODEL} · Android ${Build.VERSION.RELEASE}\n" +
                            "Thread ${thread.name}\n${error.stackTraceToString().take(24000)}\nHID-Protokollierung deaktiviert."
                    )
            }
            previousExceptionHandler?.uncaughtException(thread, error)
                ?: run {
                    android.os.Process.killProcess(android.os.Process.myPid())
                    kotlin.system.exitProcess(1)
                }
        }
    }

    fun text(context: Context): String {
        val localCrashReport =
            runCatching {
                    File(context.filesDir, "last-crash.txt").takeIf { it.exists() }?.readText()
                }
                .getOrNull()
        val systemCrashReport =
            if (Build.VERSION.SDK_INT >= 30)
                runCatching {
                        context
                            .getSystemService(ActivityManager::class.java)
                            .getHistoricalProcessExitReasons(context.packageName, 0, 10)
                            .firstOrNull {
                                it.reason == android.app.ApplicationExitInfo.REASON_CRASH ||
                                    it.reason == android.app.ApplicationExitInfo.REASON_CRASH_NATIVE
                            }
                            ?.let {
                                "Android-Absturz ${java.util.Date(it.timestamp)}:\n${it.description}"
                            }
                    }
                    .getOrNull()
            else null
        return listOfNotNull(localCrashReport, systemCrashReport).joinToString("\n\n").ifBlank {
            "Kein Absturzbericht vorhanden."
        }
    }

    fun show(activity: Activity) {
        val report = text(activity)
        val reportView =
            ScrollView(activity).apply {
                addView(
                    TextView(activity).apply {
                        text = report
                        textSize = 13f
                        setPadding(24, 16, 24, 16)
                        setTextIsSelectable(true)
                    }
                )
            }
        AlertDialog.Builder(activity)
            .setTitle("Letzter Absturz")
            .setView(reportView)
            .setPositiveButton("Kopieren") { _, _ ->
                activity
                    .getSystemService(ClipboardManager::class.java)
                    .setPrimaryClip(ClipData.newPlainText("SeaFrogs Absturz", report))
            }
            .setNegativeButton("Schließen", null)
            .show()
    }
}
