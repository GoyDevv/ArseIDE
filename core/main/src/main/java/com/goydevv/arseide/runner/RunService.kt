package com.goydevv.arseide.runner

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.goydevv.arseide.activities.main.MainActivity
import com.goydevv.arseide.exec.PhantomKillerBypass
import com.goydevv.arseide.exec.ubuntuProcess
import com.goydevv.arseide.resources.drawables
import java.io.InputStream
import kotlin.concurrent.thread
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Foreground service that runs a project build/run **headlessly** inside the sandbox (via
 * [ubuntuProcess]) — no terminal screen is shown. Output is streamed into [RunOutputState] for the
 * editor's floating view, and a progress notification (with a Stop action) lets the user follow and
 * cancel the build from anywhere. Holds a partial WakeLock + WifiLock and runs
 * [PhantomKillerBypass.ensureProtected] so Android 12+ Phantom Process Killer never kills the build.
 */
class RunService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val notificationManager by lazy { getSystemService(NotificationManager::class.java) }

    @Volatile private var proc: Process? = null
    @Volatile private var lastNotifyAt = 0L
    @Volatile private var stopped = false

    private var wakeLock: PowerManager.WakeLock? = null
    private var wifiLock: WifiManager.WifiLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
        startForegroundCompat(buildNotification(RunOutputState.label.ifBlank { "Build" }, "Starting…", ongoing = true))
        acquireLocks()
    }

    @SuppressLint("WakelockTimeout")
    private fun acquireLocks() {
        runCatching {
            if (wakeLock == null) {
                val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
                wakeLock =
                    pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "ArseIDE::BuildWakeLock").apply {
                        setReferenceCounted(false)
                    }
            }
            if (wakeLock?.isHeld == false) {
                wakeLock?.acquire(60 * 60 * 1000L) // up to 60 min max build safety
            }
            if (wifiLock == null) {
                val wm = applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
                @Suppress("DEPRECATION")
                val mode =
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) WifiManager.WIFI_MODE_FULL_LOW_LATENCY
                    else WifiManager.WIFI_MODE_FULL_HIGH_PERF
                wifiLock =
                    wm?.createWifiLock(mode, "ArseIDE::BuildWifiLock")?.apply {
                        setReferenceCounted(false)
                    }
            }
            if (wifiLock?.isHeld == false) {
                wifiLock?.acquire()
            }
        }
    }

    private fun releaseLocks() {
        runCatching { if (wakeLock?.isHeld == true) wakeLock?.release() }
        runCatching { if (wifiLock?.isHeld == true) wifiLock?.release() }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            performStop()
            return START_NOT_STICKY
        }

        val label = intent?.getStringExtra(EXTRA_LABEL) ?: "Build"
        val workingDir = intent?.getStringExtra(EXTRA_WORKDIR)
        val args = intent?.getStringArrayListExtra(EXTRA_ARGS) ?: arrayListOf()
        val apkProjectDir = intent?.getStringExtra(EXTRA_APK_DIR)
        val syncProjectDir = intent?.getStringExtra(EXTRA_SYNC_DIR)

        startForegroundCompat(buildNotification(label, "Starting…", ongoing = true))
        acquireLocks()
        RunOutputState.setStopper { performStop() }
        stopped = false

        scope.launch {
            val sb = StringBuilder()
            val lock = Any()
            try {
                // Apply Phantom Process Killer bypass / protection before spawning sandbox build
                PhantomKillerBypass.ensureProtected(applicationContext)

                val p = ubuntuProcess(workingDir = workingDir, command = args.toList())
                proc = p

                fun pump(stream: InputStream) =
                    thread {
                        runCatching {
                            stream.bufferedReader().forEachLine { line ->
                                // Filter out noisy Gradle --info build-cache disabled lines
                                if (
                                    line.trim() == "Build cache is disabled" ||
                                        line.trim().startsWith("Caching disabled for task ")
                                ) {
                                    return@forEachLine
                                }
                                synchronized(lock) { sb.appendLine(line) }
                                RunOutputState.onOutput(sb.toString())
                                maybeUpdateNotification(label)
                            }
                        }
                    }

                val t1 = pump(p.inputStream)
                val t2 = pump(p.errorStream)
                val exit = p.waitFor()
                t1.join()
                t2.join()
                RunOutputState.onOutput(sb.toString())
                if (stopped) {
                    RunOutputState.onFinished(-1)
                    runCatching { notificationManager.cancel(NOTIFICATION_ID) }
                } else {
                    RunOutputState.onFinished(exit)
                    if (exit == 0) {
                        if (syncProjectDir != null) ProjectRunner.markSynced(syncProjectDir)
                        if (apkProjectDir != null) ApkInstaller.install(applicationContext, apkProjectDir)
                    }
                    showResultNotification(label, exit)
                }
            } catch (e: Exception) {
                synchronized(lock) { sb.appendLine("Error: ${e.message}") }
                RunOutputState.onOutput(sb.toString())
                RunOutputState.onFinished(-1)
                if (stopped) runCatching { notificationManager.cancel(NOTIFICATION_ID) }
                else showResultNotification(label, -1)
            } finally {
                proc = null
                releaseLocks()
                stopForegroundCompat(remove = false)
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        releaseLocks()
        scope.cancel()
        super.onDestroy()
    }

    private fun stopProcess() {
        stopped = true
        runCatching { proc?.destroyForcibly() }
        proc = null
    }

    private fun performStop() {
        stopProcess()
        releaseLocks()
        RunOutputState.onFinished(-1)
        runCatching { notificationManager.cancel(NOTIFICATION_ID) }
        stopForegroundCompat(remove = true)
        stopSelf()
    }

    private fun maybeUpdateNotification(label: String) {
        val now = System.currentTimeMillis()
        if (now - lastNotifyAt < 700) return
        lastNotifyAt = now
        runCatching {
            notificationManager.notify(
                NOTIFICATION_ID,
                buildNotification(label, RunOutputState.latestLine.ifBlank { "Running…" }, ongoing = true),
            )
        }
    }

    private fun showResultNotification(label: String, exit: Int) {
        val text = if (exit == 0) "Finished successfully" else "Failed (exit $exit)"
        runCatching {
            notificationManager.notify(
                NOTIFICATION_ID,
                NotificationCompat.Builder(this, CHANNEL_ID)
                    .setContentTitle(label)
                    .setContentText(text)
                    .setSmallIcon(drawables.run)
                    .setContentIntent(contentIntent())
                    .setOnlyAlertOnce(true)
                    .setOngoing(false)
                    .setAutoCancel(true)
                    .build(),
            )
        }
    }

    private fun contentIntent(): PendingIntent =
        PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    private fun buildNotification(label: String, text: String, ongoing: Boolean): Notification {
        val stopIntent =
            PendingIntent.getService(
                this,
                1,
                Intent(this, RunService::class.java).apply { action = ACTION_STOP },
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(label)
            .setContentText(text)
            .setSmallIcon(drawables.run)
            .setContentIntent(contentIntent())
            .setOnlyAlertOnce(true)
            .setOngoing(ongoing)
            .apply { if (ongoing) setProgress(0, 0, true) }
            .addAction(NotificationCompat.Action.Builder(null, "Stop", stopIntent).build())
            .build()
    }

    private fun startForegroundCompat(notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun stopForegroundCompat(remove: Boolean) {
        runCatching { stopForeground(if (remove) STOP_FOREGROUND_REMOVE else STOP_FOREGROUND_DETACH) }
    }

    private fun createChannel() {
        val channel =
            NotificationChannel(CHANNEL_ID, "Build / Run", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Progress of background builds started by the Run button"
            }
        notificationManager.createNotificationChannel(channel)
    }

    companion object {
        private const val NOTIFICATION_ID = 7422
        private const val CHANNEL_ID = "run_build_channel"
        private const val ACTION_STOP = "com.goydevv.arseide.runner.action.STOP"
        private const val EXTRA_LABEL = "label"
        private const val EXTRA_WORKDIR = "workingDir"
        private const val EXTRA_ARGS = "args"
        private const val EXTRA_APK_DIR = "apkProjectDir"
        private const val EXTRA_SYNC_DIR = "syncProjectDir"

        fun start(
            context: Context,
            label: String,
            workingDir: String?,
            args: ArrayList<String>,
            androidApkProjectDir: String? = null,
            syncProjectDir: String? = null,
        ) {
            val intent =
                Intent(context, RunService::class.java).apply {
                    putExtra(EXTRA_LABEL, label)
                    putExtra(EXTRA_WORKDIR, workingDir)
                    putStringArrayListExtra(EXTRA_ARGS, args)
                    putExtra(EXTRA_APK_DIR, androidApkProjectDir)
                    putExtra(EXTRA_SYNC_DIR, syncProjectDir)
                }
            ContextCompat.startForegroundService(context, intent)
        }
    }
}
