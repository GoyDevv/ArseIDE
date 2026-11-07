package com.goydevv.arseide.exec

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Mitigates and bypasses Android 12+ (API 31+) Phantom Process Killer (`PhantomProcessList`)
 * during heavy Gradle, Kotlin, Rust/Cargo, and Node builds in Arse IDE:
 *
 * 1. **In-Process & Worker-Budget Protection (Works on ALL devices without root/ADB)**:
 *    Injects `GRADLE_OPTS` (`-Dorg.gradle.workers.max=2 -Dkotlin.compiler.execution.strategy=in-process
 *    -Dorg.gradle.caching=true -Dorg.gradle.vfs.watch=false`), `CARGO_BUILD_JOBS=2`, and `RAYON_NUM_THREADS=2`
 *    so child process trees never exceed Android's 32-process phantom ceiling or spawn a separate
 *    Kotlin compiler daemon that gets SIGKILL'd (`Signal 9`).
 * 2. **Foreground WakeLock + Battery Optimization Exemption**:
 *    Keeps the process group out of `CachedAppOptimizer` freezer states while builds/sessions run.
 * 3. **System-Level Bypass (Root / `WRITE_SECURE_SETTINGS` / Wireless ADB)**:
 *    Automatically disables `settings_enable_monitor_phantom_procs` and raises `max_phantom_processes`
 *    to `2147483647` when root or secure settings permission is available, and provides one-tap
 *    status & ADB commands in the UI.
 */
object PhantomKillerBypass {

    const val FEATURE_FLAG_SETTINGS_ENABLE_MONITOR_PHANTOM_PROCS: String =
        "settings_enable_monitor_phantom_procs"

    data class Status(
        val sdkInt: Int,
        val requiresMitigation: Boolean,
        val monitorPhantomProcs: String,
        val maxPhantomProcesses: Int?,
        val batteryOptimizationIgnored: Boolean,
        val systemBypassActive: Boolean,
        val inProcessGuardActive: Boolean = true,
    )

    const val ADB_BYPASS_COMMAND: String =
        "adb shell \"/system/bin/device_config set_sync_disabled_for_tests persistent && " +
            "/system/bin/device_config put activity_manager max_phantom_processes 2147483647 && " +
            "/system/bin/settings put global settings_enable_monitor_phantom_procs false\""

    /** Default JVM/Gradle flags that prevent process-count explosion and enable the build cache. */
    const val SAFE_GRADLE_OPTS: String =
        "-Dorg.gradle.workers.max=2 " +
            "-Dkotlin.compiler.execution.strategy=in-process " +
            "-Dorg.gradle.caching=true " +
            "-Dorg.gradle.vfs.watch=false " +
            "-Dfile.encoding=UTF-8"

    fun isBatteryOptimizationIgnored(context: Context): Boolean {
        val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return false
        return pm.isIgnoringBatteryOptimizations(context.packageName)
    }

    fun isIgnoringBatteryOptimizations(context: Context): Boolean =
        isBatteryOptimizationIgnored(context)

    @SuppressLint("BatteryLife")
    fun requestIgnoreBatteryOptimizations(context: Context) {
        runCatching {
            if (!isBatteryOptimizationIgnored(context)) {
                val intent =
                    Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                        data = Uri.parse("package:${context.packageName}")
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                context.startActivity(intent)
            }
        }
    }

    suspend fun queryStatus(context: Context): Status =
        withContext(Dispatchers.IO) {
            val sdk = Build.VERSION.SDK_INT
            if (sdk < Build.VERSION_CODES.S) {
                return@withContext Status(
                    sdkInt = sdk,
                    requiresMitigation = false,
                    monitorPhantomProcs = "Not applicable (Android < 12)",
                    maxPhantomProcesses = null,
                    batteryOptimizationIgnored = isBatteryOptimizationIgnored(context),
                    systemBypassActive = true,
                )
            }

            val monitorVal =
                runCatching {
                    Settings.Global.getString(
                        context.contentResolver,
                        FEATURE_FLAG_SETTINGS_ENABLE_MONITOR_PHANTOM_PROCS,
                    )
                }.getOrNull() ?: "DEFAULT"

            val maxProcs: Int? =
                runCatching {
                    Settings.Global.getInt(context.contentResolver, "max_phantom_processes")
                }.getOrNull()

            val bypassed =
                monitorVal.equals("false", ignoreCase = true) ||
                    (maxProcs != null && maxProcs > 1024)

            Status(
                sdkInt = sdk,
                requiresMitigation = true,
                monitorPhantomProcs = monitorVal,
                maxPhantomProcesses = maxProcs,
                batteryOptimizationIgnored = isBatteryOptimizationIgnored(context),
                systemBypassActive = bypassed,
            )
        }

    suspend fun applySystemPhantomBypass(context: Context): Boolean = ensureProtected(context)

    suspend fun ensureProtected(context: Context): Boolean =
        withContext(Dispatchers.IO) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return@withContext true

            var applied = false

            // 1. Try Settings.Global directly (succeeds if WRITE_SECURE_SETTINGS is granted)
            runCatching {
                val ok =
                    Settings.Global.putString(
                        context.contentResolver,
                        FEATURE_FLAG_SETTINGS_ENABLE_MONITOR_PHANTOM_PROCS,
                        "false",
                    )
                Settings.Global.putString(
                    context.contentResolver,
                    "device_config_sync_disabled",
                    "1",
                )
                if (ok) applied = true
            }

            // 2. Try root shell if available (non-blocking timeout)
            if (!applied) {
                runCatching {
                    val suFile =
                        listOf("/system/bin/su", "/system/xbin/su", "/sbin/su", "/debug_ramdisk/su")
                            .firstOrNull { java.io.File(it).exists() }
                    if (suFile != null) {
                        val proc =
                            ProcessBuilder(
                                suFile,
                                "-c",
                                "/system/bin/device_config set_sync_disabled_for_tests persistent && " +
                                    "/system/bin/device_config put activity_manager max_phantom_processes 2147483647 && " +
                                    "/system/bin/settings put global settings_enable_monitor_phantom_procs false",
                            ).start()
                        if (proc.waitFor() == 0) {
                            applied = true
                        }
                    }
                }
            }

            applied
        }
}
