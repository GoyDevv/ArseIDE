package com.goydevv.arseide.activities.terminal

import android.Manifest
import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.goydevv.arseide.XedConstants
import com.goydevv.arseide.exec.isTerminalInstalled
import com.goydevv.arseide.file.child
import com.goydevv.arseide.file.localBinDir
import com.goydevv.arseide.file.sandboxDir
import com.goydevv.arseide.resources.drawables
import com.goydevv.arseide.resources.getString
import com.goydevv.arseide.resources.strings
import com.goydevv.arseide.settings.Settings
import com.goydevv.arseide.terminal.AutoSetup
import com.goydevv.arseide.terminal.AutoSetupOverlay
import com.goydevv.arseide.terminal.AutoSetupState
import com.goydevv.arseide.terminal.NEXT_STAGE
import com.goydevv.arseide.terminal.SessionService
import com.goydevv.arseide.terminal.TerminalBackEnd
import com.goydevv.arseide.terminal.TerminalScreen
import com.goydevv.arseide.terminal.changeSession
import com.goydevv.arseide.terminal.getNextStage
import com.goydevv.arseide.terminal.terminalView
import com.goydevv.arseide.theme.XedTheme
import com.goydevv.arseide.utils.getTempDir
import java.io.File
import java.lang.ref.WeakReference
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

class Terminal : AppCompatActivity() {
    var sessionBinder by mutableStateOf<WeakReference<SessionService.SessionBinder>?>(null)
    var isBound = false

    val serviceConnection =
        object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
                val binder = service as SessionService.SessionBinder
                sessionBinder = WeakReference(binder)
                isBound = true
                handleIntent(intent)
            }

            override fun onServiceDisconnected(name: ComponentName?) {
                isBound = false
                sessionBinder = null
            }
        }

    override fun onStart() {
        super.onStart()
        ContextCompat.startForegroundService(this, Intent(this, SessionService::class.java))

        Intent(this, SessionService::class.java).also { intent ->
            bindService(intent, serviceConnection, BIND_AUTO_CREATE)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        instance = this
    }

    fun handleIntent(intent: Intent) {
        this.intent = intent
        val binder = sessionBinder?.get() ?: return
        terminalView.get() ?: return

        val pwd = intent.getStringExtra("cwd") ?: return
        val sessionId = File(pwd).name

        lifecycleScope.launch(Dispatchers.Main) {
            val client = TerminalBackEnd()
            val info = binder.getSessionInfoByPwd(pwd) ?: binder.createSession(sessionId, client, this@Terminal)

            this@Terminal.changeSession(info.id)
            setIntent(Intent())
        }
    }

    override fun onStop() {
        super.onStop()
        if (isBound) {
            unbindService(serviceConnection)
            isBound = false
        }
    }

    private fun needsNotificationPermission(): Boolean {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
                PackageManager.PERMISSION_GRANTED
    }

    companion object {
        private var activityRef = WeakReference<Terminal?>(null)
        var instance: Terminal?
            get() = activityRef.get()
            private set(value) {
                activityRef = WeakReference(value)
            }
    }

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { _ -> }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        instance = this

        if (needsNotificationPermission()) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        setContent {
            XedTheme {
                Surface {
                    Box(modifier = Modifier.fillMaxSize()) {
                        if (sessionBinder != null) {
                            TerminalScreenHost(this@Terminal)
                        } else {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text("Connecting to terminal service…")
                            }
                        }

                        if (AutoSetupState.isActive) {
                            AutoSetupOverlay(activity = this@Terminal)
                        }
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        if (!isFinishing) {
            super.onDestroy()
            return
        }
        super.onDestroy()
    }

    var progressText by mutableStateOf(strings.installing.getString())
    var installNextStage by mutableStateOf<NEXT_STAGE?>(null)

    @OptIn(DelicateCoroutinesApi::class)
    @Composable
    fun TerminalScreenHost(context: Context) {
        var progress by remember { mutableFloatStateOf(0f) }
        var needsDownload by remember { mutableStateOf(false) }
        var downloadedBytes by remember { mutableLongStateOf(0L) }
        var totalBytes by remember { mutableLongStateOf(0L) }
        var setupError by remember { mutableStateOf<String?>(null) }
        var retryKey by remember { mutableIntStateOf(0) }
        var userChoseDownloadMode by remember {
            mutableStateOf(isTerminalInstalled() || AutoSetupState.isActive)
        }
        var showIncompleteBanner by remember {
            mutableStateOf(!Settings.auto_setup_completed && !AutoSetupState.isActive)
        }

        fun formatBytesToMB(bytes: Long): String = "%.2f".format(bytes / (1024.0 * 1024.0))

        // If the user skipped Auto-Setup on first launch and the sandbox is not installed yet,
        // offer them a clean one-tap prompt to either run Full Auto-Setup (rootfs + compilers)
        // or download the base Ubuntu sandbox.
        if (!userChoseDownloadMode && !isTerminalInstalled() && !AutoSetupState.isActive) {
            Box(
                modifier = Modifier.fillMaxSize().padding(24.dp),
                contentAlignment = Alignment.Center,
            ) {
                ElevatedCard(
                    shape = RoundedCornerShape(24.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(56.dp),
                        ) {
                            Icon(
                                painter = painterResource(drawables.terminal),
                                contentDescription = null,
                                modifier = Modifier.padding(14.dp),
                            )
                        }
                        Text(
                            text = "Set Up Linux Sandbox",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                        )
                        Text(
                            text = "Terminal setup was skipped on first launch. Choose Full Auto-Setup to install Ubuntu 24.04 plus Git, Python, Node.js, OpenJDK, and C/C++ build tools, or install the base Ubuntu shell only.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                        Spacer(Modifier.height(4.dp))
                        Button(
                            onClick = {
                                AutoSetup.startInCurrentTerminal()
                                userChoseDownloadMode = true
                                retryKey++
                            },
                            modifier = Modifier.fillMaxWidth().height(48.dp),
                        ) {
                            Text("Run Full Auto-Setup (Recommended)")
                        }
                        FilledTonalButton(
                            onClick = {
                                userChoseDownloadMode = true
                                retryKey++
                            },
                            modifier = Modifier.fillMaxWidth().height(44.dp),
                        ) {
                            Text("Install Base Ubuntu Shell Only")
                        }
                        TextButton(onClick = { finish() }) {
                            Text(stringResource(strings.cancel))
                        }
                    }
                }
            }
            return
        }

        LaunchedEffect(retryKey, userChoseDownloadMode) {
            if (!userChoseDownloadMode) return@LaunchedEffect
            setupError = null
            try {
                val abi = Build.SUPPORTED_ABIS
                val candidateUrls = XedConstants.rootfsUrlsForAbis(abi)
                val filesToDownload = mutableListOf<DownloadFile>()

                if (isTerminalInstalled().not()) {
                    filesToDownload.add(
                        DownloadFile(
                            urls = candidateUrls,
                            outputFile = getTempDir().child("sandbox.tar.gz"),
                        )
                    )
                }

                needsDownload = filesToDownload.any { file -> file.outputFile.exists().not() }

                setupEnvironment(
                    filesToDownload = filesToDownload,
                    onProgress = { fileName, downloaded, total ->
                        downloadedBytes = downloaded
                        totalBytes = total

                        if (total > 0) {
                            val downloadedMB = formatBytesToMB(downloaded)
                            val totalMB = formatBytesToMB(total)
                            progressText =
                                "${strings.downloading.getString()} ${fileName.removeSuffix(".so").removePrefix("lib")} ($downloadedMB/$totalMB MB)"
                        }

                        if (AutoSetupState.isActive) AutoSetupState.onDownload(downloaded, total)
                    },
                    onComplete = {
                        installNextStage = it
                        if (AutoSetupState.isActive) AutoSetupState.onExtracting()
                    },
                    onError = { error, file ->
                        error.printStackTrace()
                        GlobalScope.launch(Dispatchers.IO) {
                            if (file?.absolutePath?.contains(localBinDir().absolutePath) == true) {
                                localBinDir().deleteRecursively()
                            }
                            if (file?.name == "sandbox.tar.gz") {
                                sandboxDir().deleteRecursively()
                                File(getTempDir(), "sandbox.tar.gz").delete()
                            }
                        }
                        setupError = error.message ?: "Failed to download Ubuntu rootfs."
                    },
                )
            } catch (e: Exception) {
                e.printStackTrace()
                setupError = e.message ?: "Unexpected terminal setup error."
            }
        }

        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            val activity = LocalContext.current as? Activity

            DisposableEffect(Unit) {
                activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                onDispose { activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
            }

            when {
                setupError != null -> {
                    ElevatedCard(
                        shape = RoundedCornerShape(24.dp),
                        modifier = Modifier.fillMaxWidth().padding(24.dp),
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Text(
                                text = "Sandbox Download Failed",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.error,
                            )
                            Text(
                                text = setupError ?: "",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                OutlinedButton(
                                    onClick = { finish() },
                                    modifier = Modifier.weight(1f),
                                ) {
                                    Text("Close")
                                }
                                Button(
                                    onClick = {
                                        setupError = null
                                        retryKey++
                                    },
                                    modifier = Modifier.weight(1f),
                                ) {
                                    Text("Retry Setup")
                                }
                            }
                        }
                    }
                }

                installNextStage == null -> {
                    if (needsDownload) {
                        Box(modifier = Modifier.fillMaxSize().padding(24.dp)) {
                            Column(
                                modifier = Modifier.align(Alignment.Center),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Text(
                                    text = progressText,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                )

                                Spacer(modifier = Modifier.height(16.dp))

                                LinearProgressIndicator(
                                    progress = { progress },
                                    modifier = Modifier.fillMaxWidth(0.85f),
                                )

                                if (totalBytes > 0) {
                                    val percent = (downloadedBytes.toFloat() / totalBytes * 100).toInt()
                                    progress = downloadedBytes.toFloat() / totalBytes

                                    Text(
                                        text = "$percent%",
                                        style = MaterialTheme.typography.bodyMedium,
                                        modifier = Modifier.padding(top = 8.dp),
                                    )
                                }
                            }

                            Text(
                                text = stringResource(strings.warn_dont_leave_setup),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 8.dp),
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                }

                else -> {
                    Column(modifier = Modifier.fillMaxSize()) {
                        if (showIncompleteBanner && !Settings.auto_setup_completed && !AutoSetupState.isActive) {
                            Surface(
                                color = MaterialTheme.colorScheme.secondaryContainer,
                                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                ) {
                                    Text(
                                        text = "Toolchain packages not installed yet",
                                        style = MaterialTheme.typography.labelMedium,
                                        modifier = Modifier.weight(1f),
                                    )
                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        TextButton(
                                            onClick = {
                                                showIncompleteBanner = false
                                                AutoSetup.launch(this@Terminal)
                                            }
                                        ) {
                                            Text("Auto-Setup", fontWeight = FontWeight.Bold)
                                        }
                                        TextButton(onClick = { showIncompleteBanner = false }) {
                                            Text("Dismiss")
                                        }
                                    }
                                }
                            }
                        }
                        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                            TerminalScreen(terminalActivity = this@Terminal)
                        }
                    }
                }
            }
        }
    }

    data class DownloadFile(val urls: List<String>, val outputFile: File)

    private suspend fun setupEnvironment(
        filesToDownload: List<DownloadFile>,
        onProgress: (fileName: String, downloadedBytes: Long, totalBytes: Long) -> Unit,
        onComplete: (NEXT_STAGE) -> Unit,
        onError: (Exception, File?) -> Unit,
    ) {
        var currentFile: File? = null

        withContext(Dispatchers.IO) {
            try {
                filesToDownload.forEach { file ->
                    val outputFile = file.outputFile
                    currentFile = outputFile
                    outputFile.parentFile?.mkdirs()

                    if (!outputFile.exists() || outputFile.length() == 0L) {
                        downloadFileWithFallback(
                            urls = file.urls,
                            outputFile = outputFile,
                            onProgress = { downloaded, total -> onProgress(file.outputFile.name, downloaded, total) },
                        )
                    } else {
                        onProgress(file.outputFile.name, outputFile.length(), outputFile.length())
                    }

                    runCatching { outputFile.setExecutable(true) }.onFailure { it.printStackTrace() }
                }

                val stage = getNextStage(this@Terminal)
                onComplete(stage)
            } catch (e: Exception) {
                e.printStackTrace()
                if (currentFile?.exists() == true) {
                    currentFile.delete()
                }
                withContext(Dispatchers.Main) { onError(e, currentFile) }
            }
        }
    }

    private suspend fun downloadFileWithFallback(
        urls: List<String>,
        outputFile: File,
        onProgress: (downloadedBytes: Long, totalBytes: Long) -> Unit,
    ) {
        withContext(Dispatchers.IO) {
            val client =
                OkHttpClient.Builder()
                    .connectTimeout(45, TimeUnit.SECONDS)
                    .readTimeout(1, TimeUnit.MINUTES)
                    .writeTimeout(1, TimeUnit.MINUTES)
                    .callTimeout(10, TimeUnit.MINUTES)
                    .followRedirects(true)
                    .followSslRedirects(true)
                    .build()

            var lastError: Exception? = null
            for (url in urls) {
                try {
                    val request = Request.Builder().url(url).build()
                    client.newCall(request).execute().use { response ->
                        if (!response.isSuccessful) {
                            throw Exception("HTTP ${response.code} from $url")
                        }

                        val body = response.body
                        val totalBytes = body.contentLength()
                        var downloadedBytes = 0L

                        outputFile.outputStream().use { output ->
                            body.byteStream().use { input ->
                                val buffer = ByteArray(16 * 1024)
                                var bytesRead: Int
                                while (input.read(buffer).also { bytesRead = it } != -1) {
                                    output.write(buffer, 0, bytesRead)
                                    downloadedBytes += bytesRead
                                    withContext(Dispatchers.Main) { onProgress(downloadedBytes, totalBytes) }
                                }
                            }
                        }
                    }
                    if (outputFile.exists() && outputFile.length() > 1024L) {
                        return@withContext
                    }
                } catch (e: Exception) {
                    lastError = e
                    if (outputFile.exists()) outputFile.delete()
                }
            }
            throw lastError ?: Exception("Failed to download sandbox rootfs from all mirrors.")
        }
    }
}
