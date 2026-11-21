package com.goydevv.arseide.terminal

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
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.core.app.NotificationCompat
import com.goydevv.arseide.activities.terminal.Terminal
import com.goydevv.arseide.exec.PhantomKillerBypass
import com.goydevv.arseide.resources.drawables
import com.goydevv.arseide.resources.getString
import com.goydevv.arseide.resources.strings
import com.goydevv.arseide.settings.Settings
import com.termux.terminal.TerminalSession
import com.termux.terminal.TerminalSessionClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class SessionService : Service() {
    private val sessions = hashMapOf<SessionId, TerminalSession>()
    private val sessionWorkDirs = mutableMapOf<SessionId, SessionPwd>()
    val sessionList = mutableStateListOf<String>()
    var currentSession = mutableStateOf("main")
    private var deamonRunning = false
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var keepAliveJob: Job? = null

    var wakeLock: PowerManager.WakeLock? = null
    private var wifiLock: WifiManager.WifiLock? = null

    @SuppressLint("WakelockTimeout")
    inner class SessionBinder : Binder() {
        fun getService(): SessionService {
            return this@SessionService
        }

        fun createSession(
            id: SessionId,
            client: TerminalSessionClient,
            activity: Terminal,
            explicitWorkingDir: String? = null,
        ): SessionInfo {
            acquireBackgroundLocks()
            startKeepAliveLoop()
            val uniqueId = resolveUniqueSessionId(id)
            return MkSession.createSession(
                    activity,
                    client,
                    uniqueId,
                    activity.installNextStage != null && activity.installNextStage == NEXT_STAGE.EXTRACTION,
                    explicitWorkingDir = explicitWorkingDir,
                )
                .let {
                    val (session, pwd) = it
                    sessions[uniqueId] = session
                    sessionWorkDirs[uniqueId] = pwd
                    if (!sessionList.contains(uniqueId)) {
                        sessionList.add(uniqueId)
                    }
                    updateNotification()
                    SessionInfo(uniqueId, pwd, session)
                }
        }

        fun getSession(id: SessionId): TerminalSession? {
            return sessions[id]
        }

        fun getSessionInfoByPwd(pwd: SessionPwd): SessionInfo? {
            val normalized = pwd.trimEnd('/')
            return sessionWorkDirs.entries
                .firstOrNull { (_, dir) -> dir.trimEnd('/') == normalized }
                ?.let { (id, dir) -> sessions[id]?.let { SessionInfo(id, dir, it) } }
        }

        fun terminateSession(id: SessionId) {
            sessions[id]?.apply {
                if (emulator != null) {
                    finishIfRunning()
                }
            }
            sessions.remove(id)
            sessionList.remove(id)
            sessionWorkDirs.remove(id)

            if (sessions.isEmpty()) {
                stopKeepAliveLoop()
                releaseBackgroundLocks()
                stopSelf()
                deamonRunning = false
            } else {
                updateNotification()
            }
        }
    }

    private fun resolveUniqueSessionId(requested: String): String {
        val base = requested.ifBlank { "session" }
        if (!sessions.containsKey(base)) return base
        var idx = 2
        while (sessions.containsKey("$base ($idx)")) {
            idx++
        }
        return "$base ($idx)"
    }

    private val binder = SessionBinder()
    private val notificationManager by lazy { getSystemService(NotificationManager::class.java) }

    override fun onBind(intent: Intent?): IBinder {
        return binder
    }

    override fun onDestroy() {
        sessions.forEach { s -> s.value.finishIfRunning() }
        deamonRunning = false
        stopKeepAliveLoop()
        releaseBackgroundLocks()
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        if (Settings.terminate_sessions_on_exit || AutoSetupState.isActive) {
            actionExit()
        } else if (sessions.isNotEmpty()) {
            acquireBackgroundLocks()
            updateNotification()
        }
    }

    @SuppressLint("WakelockTimeout")
    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        val notification = createNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        if (!deamonRunning) {
            serviceScope.launch {
                deamonRunning = true
                PhantomKillerBypass.ensureProtected(applicationContext)
            }
        }

        acquireBackgroundLocks()
        startKeepAliveLoop()
    }

    @SuppressLint("WakelockTimeout")
    private fun acquireBackgroundLocks() {
        runCatching {
            if (wakeLock == null) {
                wakeLock =
                    (getSystemService(POWER_SERVICE) as PowerManager)
                        .newWakeLock(
                            PowerManager.PARTIAL_WAKE_LOCK,
                            "ArseIDE::SessionWakeLock",
                        )
                        .apply { setReferenceCounted(false) }
            }
            if (wakeLock?.isHeld == false) {
                wakeLock?.acquire()
            }
        }
        runCatching {
            if (wifiLock == null) {
                val wm = applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
                @Suppress("DEPRECATION")
                val mode =
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        WifiManager.WIFI_MODE_FULL_LOW_LATENCY
                    } else {
                        WifiManager.WIFI_MODE_FULL_HIGH_PERF
                    }
                wifiLock =
                    wm?.createWifiLock(mode, "ArseIDE::SessionWifiLock")?.apply {
                        setReferenceCounted(false)
                    }
            }
            if (wifiLock?.isHeld == false) {
                wifiLock?.acquire()
            }
        }
    }

    private fun releaseBackgroundLocks() {
        runCatching {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
            }
        }
        runCatching {
            if (wifiLock?.isHeld == true) {
                wifiLock?.release()
            }
        }
    }

    private fun startKeepAliveLoop() {
        if (keepAliveJob?.isActive == true) return
        keepAliveJob =
            serviceScope.launch {
                while (isActive) {
                    delay(25_000L)
                    if (sessions.isNotEmpty()) {
                        if (wakeLock?.isHeld == false || wifiLock?.isHeld == false) {
                            acquireBackgroundLocks()
                        }
                    }
                }
            }
    }

    private fun stopKeepAliveLoop() {
        keepAliveJob?.cancel()
        keepAliveJob = null
    }

    fun actionExit() {
        sessions.forEach { s -> s.value.finishIfRunning() }
        sessions.clear()
        sessionList.clear()
        sessionWorkDirs.clear()
        deamonRunning = false
        stopKeepAliveLoop()
        releaseBackgroundLocks()
        stopSelf()
    }

    @SuppressLint("WakelockTimeout", "Wakelock")
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            "ACTION_EXIT" -> {
                actionExit()
                return START_NOT_STICKY
            }

            "ACTION_WAKE_LOCK" -> {
                if (wakeLock?.isHeld == true) {
                    releaseBackgroundLocks()
                } else {
                    acquireBackgroundLocks()
                }
                updateNotification()
            }

            else -> {
                acquireBackgroundLocks()
                startKeepAliveLoop()
            }
        }
        return START_STICKY
    }

    private fun createNotification(): Notification {
        val intent = Intent(this, Terminal::class.java)
        val pendingIntent =
            PendingIntent.getActivity(
                this,
                0,
                intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
        val exitIntent = Intent(this, SessionService::class.java).apply { action = "ACTION_EXIT" }
        val wakeLockIntent = Intent(this, SessionService::class.java).apply { action = "ACTION_WAKE_LOCK" }

        val exitPendingIntent =
            PendingIntent.getService(
                this,
                1,
                exitIntent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
        val wakelockPendingIntent =
            PendingIntent.getService(
                this,
                2,
                wakeLockIntent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )

        val locksActive = wakeLock?.isHeld == true

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("${strings.app_name.getString()} ${strings.terminal.getString()}")
            .setContentText(getNotificationContentText(locksActive))
            .setSmallIcon(drawables.terminal)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setContentIntent(pendingIntent)
            .addAction(NotificationCompat.Action.Builder(null, strings.exit.getString(), exitPendingIntent).build())
            .addAction(
                NotificationCompat.Action.Builder(
                        null,
                        if (locksActive) {
                            strings.release_wakelock.getString()
                        } else {
                            strings.acquire_wakelock.getString()
                        },
                        wakelockPendingIntent,
                    )
                    .build()
            )
            .setOngoing(true)
            .build()
    }

    private fun createNotificationChannel() {
        val channel =
            NotificationChannel(CHANNEL_ID, "Terminal Background Sessions", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Keeps terminal sessions and AI agents active in the background"
            }
        notificationManager.createNotificationChannel(channel)
    }

    private fun updateNotification() {
        runCatching {
            val notification = createNotification()
            notificationManager.notify(NOTIFICATION_ID, notification)
        }
    }

    private fun getNotificationContentText(wakelock: Boolean): String {
        val count = sessions.size
        val suffix = if (count == 1) "session" else "sessions"
        return if (wakelock) {
            "$count $suffix active · Background keep-alive on"
        } else {
            "$count $suffix active"
        }
    }

    companion object {
        private const val NOTIFICATION_ID = 1
        private const val CHANNEL_ID = "session_service_channel"
    }
}

typealias SessionId = String

typealias SessionPwd = String

data class SessionInfo(val id: SessionId, val pwd: SessionPwd, val session: TerminalSession)
