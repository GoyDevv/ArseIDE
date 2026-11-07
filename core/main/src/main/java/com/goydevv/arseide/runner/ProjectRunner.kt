package com.goydevv.arseide.runner

import android.app.Activity
import com.goydevv.arseide.exec.PhantomKillerBypass
import com.goydevv.arseide.exec.TerminalCommand
import com.goydevv.arseide.exec.isTerminalInstalled
import com.goydevv.arseide.exec.launchTerminal
import com.goydevv.arseide.file.FileObject
import com.goydevv.arseide.file.FileWrapper
import com.goydevv.arseide.file.child
import com.goydevv.arseide.file.localBinDir
import com.goydevv.arseide.horizon.HorizonModPackager
import com.goydevv.arseide.projects.DetectedProjectType
import com.goydevv.arseide.projects.GradleConfig
import com.goydevv.arseide.projects.ProjectTypeDetector
import com.goydevv.arseide.resources.strings
import com.goydevv.arseide.runner.runners.web.html.HtmlRunner
import com.goydevv.arseide.terminal.setupAssetFile
import com.goydevv.arseide.terminal.setupTerminalFiles
import com.goydevv.arseide.utils.dialogRes
import java.io.File
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * Project-aware "Run" entry point used by the editor's play button.
 *
 * Supports:
 *  - **Horizon Mod (`.hmod` / `.hnmod`)** – validates `horizon-mod.toml`, verifies 26.3 client item
 *    model dispatches, builds `mod.wasm` via `cargo` if `Cargo.toml` is present, and packages
 *    `<id>.hmod` / `<id>.hnmod`. Content-only `.hmod` mods package directly in-process even without
 *    the Linux sandbox installed!
 *  - **Python / Node / Rust / Go** – runs the project from its own folder in the terminal sandbox.
 *  - **Fabric / Forge / generic Gradle** – checks the JDK and runs `./gradlew build --build-cache`.
 *  - **Android** – exports ANDROID_HOME, runs `./gradlew assembleDebug --build-cache`, then installs
 *    the built APK.
 *  - **Static web** – opens the in-app HTML preview.
 */
object ProjectRunner {

    private const val CACHE_TTL_MS = 4_000L
    private val cache = HashMap<String, Pair<Long, DetectedProjectType>>()

    private val syncedProjects = java.util.Collections.synchronizedSet(HashSet<String>())

    fun isSynced(projectRootPath: String): Boolean = syncedProjects.contains(projectRootPath)

    fun markSynced(projectRootPath: String) {
        syncedProjects.add(projectRootPath)
    }

    fun isRunnable(type: DetectedProjectType): Boolean = type != DetectedProjectType.UNKNOWN

    fun isGradleType(type: DetectedProjectType): Boolean =
        type == DetectedProjectType.ANDROID ||
            type == DetectedProjectType.GRADLE ||
            type == DetectedProjectType.FABRIC_MOD ||
            type == DetectedProjectType.FORGE_MOD

    private suspend fun awaitFirstBuildAck(rootFile: File): Boolean {
        if (GradleConfig.firstBuildShown(rootFile)) return true
        return suspendCancellableCoroutine { cont ->
            dialogRes(
                title = "Setting up \"${rootFile.name}\"",
                msg =
                    "The first Gradle build/sync can take around 10–20 minutes.\n\n" +
                        "It downloads the Gradle dependencies (and, for Android projects, the SDK and build-tools) " +
                        "over your connection. Later builds use the enabled Gradle build cache and are much faster.",
                okRes = strings.ok,
                cancelRes = strings.cancel,
                onOk = {
                    GradleConfig.setFirstBuildShown(rootFile)
                    if (cont.isActive) cont.resume(true)
                },
                onCancel = { if (cont.isActive) cont.resume(false) },
                cancelable = false,
            )
        }
    }

    @Synchronized
    fun detect(projectRootPath: String): DetectedProjectType {
        val now = System.currentTimeMillis()
        cache[projectRootPath]?.let { (ts, type) -> if (now - ts < CACHE_TTL_MS) return type }
        val type = ProjectTypeDetector.detect(File(projectRootPath))
        cache[projectRootPath] = now to type
        return type
    }

    fun canRun(projectRootPath: String?): Boolean {
        if (projectRootPath.isNullOrBlank()) return false
        if (!File(projectRootPath).isDirectory) return false
        return isRunnable(detect(projectRootPath))
    }

    fun resolveProjectRootPath(projectRoot: FileObject?, file: FileObject): String? {
        if (projectRoot is FileWrapper) return projectRoot.getAbsolutePath()
        if (file is FileWrapper) return File(file.getAbsolutePath()).parent
        return null
    }

    suspend fun run(activity: Activity, projectRoot: FileObject?, file: FileObject) {
        val rootPath = resolveProjectRootPath(projectRoot, file) ?: return
        val rootFile = File(rootPath)
        val type = detect(rootPath)
        if (!isRunnable(type)) return

        PhantomKillerBypass.ensureProtected(activity)

        // Static web is previewed in-app via the existing HTML runner.
        if (type == DetectedProjectType.WEB) {
            HtmlRunner.run(activity, webEntry(rootFile, file))
            return
        }

        // Horizon Mod (.hmod / .hnmod): if there's no Cargo.toml (or if the Linux sandbox isn't
        // installed yet), validate and package directly in-process via HorizonModPackager!
        if (type == DetectedProjectType.HORIZON_MOD || type == DetectedProjectType.HORIZON_MIXIN_MOD) {
            val hasCargo = File(rootFile, "Cargo.toml").isFile
            if (!hasCargo || !isTerminalInstalled()) {
                val label = "Package · ${rootFile.name}"
                if (!RunOutputState.begin(label = label)) return
                val pkgRes = HorizonModPackager.packageProject(rootFile)
                val transcript = buildString {
                    appendLine("==> Horizon Mod Validator & Packager")
                    appendLine("Project : ${rootFile.absolutePath}")
                    appendLine("Mod ID  : ${pkgRes.report.modId} (v${pkgRes.report.version}, API ${pkgRes.report.api})")
                    appendLine("Content : ${pkgRes.report.declaredBlocks} block(s), ${pkgRes.report.declaredItems} item(s)")
                    if (pkgRes.report.warnings.isNotEmpty()) {
                        appendLine()
                        pkgRes.report.warnings.forEach { appendLine("WARN: $it") }
                    }
                    appendLine()
                    appendLine(pkgRes.message)
                }
                RunOutputState.onOutput(transcript)
                RunOutputState.onFinished(if (pkgRes.success) 0 else 1)
                return
            }
        }

        setupAssetFile("project_runner")
        setupTerminalFiles()
        val sandboxRoot = toSandboxPath(rootPath)
        val sandboxFile = toSandboxPath(file.getAbsolutePath())
        val label = "Run · ${rootFile.name}"
        val scriptPath = localBinDir().child("project_runner").absolutePath

        val gradleArgs = if (isGradleType(type)) GradleConfig.gradleArgs(rootFile) else ""
        val buildType = GradleConfig.buildType(rootFile).id

        if (type == DetectedProjectType.ANDROID && !isSynced(rootPath)) {
            if (!RunOutputState.begin(label = label)) return
            RunOutputState.onOutput(syncRequiredMessage(rootFile.name))
            RunOutputState.onFinished(1)
            return
        }

        if (isGradleType(type) && !awaitFirstBuildAck(rootFile)) return

        if (isTerminalInstalled()) {
            if (!RunOutputState.begin(label = label)) return
            RunService.start(
                context = activity,
                label = label,
                workingDir = sandboxRoot,
                args = arrayListOf("/bin/bash", "-l", scriptPath, type.name, sandboxRoot, sandboxFile, gradleArgs, buildType),
                androidApkProjectDir = if (type == DetectedProjectType.ANDROID) rootPath else null,
                syncProjectDir = if (type == DetectedProjectType.ANDROID) rootPath else null,
            )
        } else {
            launchTerminal(
                activity = activity,
                terminalCommand =
                    TerminalCommand(
                        sandbox = true,
                        exe = "/bin/bash",
                        args = arrayOf(scriptPath, type.name, sandboxRoot, sandboxFile, gradleArgs, buildType),
                        id = label,
                        terminatePreviousSession = true,
                        workingDir = sandboxRoot,
                    ),
            )
        }
    }

    suspend fun sync(activity: Activity, projectRoot: FileObject?, file: FileObject) {
        val rootPath = resolveProjectRootPath(projectRoot, file) ?: return
        if (!File(rootPath).isDirectory) return
        val label = "Sync · ${File(rootPath).name}"

        PhantomKillerBypass.ensureProtected(activity)

        if (!isTerminalInstalled()) {
            if (!RunOutputState.begin(label = label)) return
            RunOutputState.onOutput(
                "⚠  The Linux sandbox isn't set up yet.\n\nOpen the terminal once (or run Auto Setup) to install it, then try Sync again."
            )
            RunOutputState.onFinished(1)
            return
        }

        setupAssetFile("project_runner")
        setupTerminalFiles()
        val sandboxRoot = toSandboxPath(rootPath)
        val scriptPath = localBinDir().child("project_runner").absolutePath

        val rootFile = File(rootPath)
        val gradleArgs = GradleConfig.gradleArgs(rootFile)
        val buildType = GradleConfig.buildType(rootFile).id

        if (!awaitFirstBuildAck(rootFile)) return

        if (!RunOutputState.begin(label = label)) return
        RunService.start(
            context = activity,
            label = label,
            workingDir = sandboxRoot,
            args = arrayListOf("/bin/bash", "-l", scriptPath, "SYNC", sandboxRoot, "", gradleArgs, buildType),
            androidApkProjectDir = null,
            syncProjectDir = rootPath,
        )
    }

    private fun syncRequiredMessage(name: String): String =
        buildString {
            appendLine("⚠  Gradle sync required for \"$name\"")
            appendLine()
            appendLine("Android projects must be Gradle-synced once per session before you can Run,")
            appendLine("just like Android Studio.")
            appendLine()
            appendLine("→ Tap the Sync button (next to Run), wait for it to finish successfully,")
            appendLine("  then press Run again.")
            appendLine()
            appendLine("If Sync fails, make sure the Android SDK (and NDK) are installed from the")
            appendLine("Dependencies dialog (the download icon in the toolbar).")
        }

    fun toSandboxPath(path: String): String =
        when {
            path == "/storage/emulated/0" || path.startsWith("/storage/emulated/0/") ->
                "/sdcard" + path.removePrefix("/storage/emulated/0")
            path == "/storage/self/primary" || path.startsWith("/storage/self/primary/") ->
                "/sdcard" + path.removePrefix("/storage/self/primary")
            else -> path
        }

    private fun webEntry(root: File, file: FileObject): FileObject {
        if (file is FileWrapper && file.getName().endsWith(".html", ignoreCase = true)) return file
        File(root, "index.html").takeIf { it.exists() }?.let { return FileWrapper(it) }
        root.listFiles()?.firstOrNull { it.isFile && it.extension.equals("html", ignoreCase = true) }?.let {
            return FileWrapper(it)
        }
        return file
    }
}
