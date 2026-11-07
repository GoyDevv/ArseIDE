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
import com.goydevv.arseide.horizon.HorizonModPorter
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

/**
 * Project-aware "Run / Build" orchestrator.
 *
 * - **Horizon Native Mod (`.hnmod`)**: Runs `cargo check` / `rustc` on the companion crate in the
 *   background sandbox via `project_runner.sh`, validates the bundle against `Bundle::read` rules,
 *   and packages `<id>.hnmod` without blocking the UI.
 * - **Fabric / Forge / Gradle**: Runs `./gradlew build --build-cache`.
 * - **Android**: Runs `./gradlew assembleDebug --build-cache` and prompts to install the APK.
 * - **Python / Node / Rust / Go**: Runs the project from its root directory in the sandbox.
 * - **Static Web**: Launches the in-app HTML preview.
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
                title = "First build for \"${rootFile.name}\"",
                msg =
                    "The initial Gradle build downloads SDK and project dependencies in the background. " +
                        "Subsequent builds use the Gradle build cache and finish much faster.",
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

        if (type == DetectedProjectType.WEB) {
            HtmlRunner.run(activity, webEntry(rootFile, file))
            return
        }

        // For .hnmod projects, ensure validate_and_pack_hnmod.py is present in tools/
        if (type == DetectedProjectType.HORIZON_MIXIN_MOD) {
            withContext(Dispatchers.IO) {
                HorizonModPorter.copyBundledTools(File(rootFile, "tools"))
                // Also run an immediate pre-package pass so <id>.hnmod is always up-to-date
                HorizonModPackager.packageProject(rootFile)
            }
            if (!isTerminalInstalled()) {
                val label = "Build .hnmod · ${rootFile.name}"
                if (!RunOutputState.begin(label = label)) return
                val pkgRes = HorizonModPackager.packageProject(rootFile)
                val transcript = buildString {
                    appendLine("==> Horizon .hnmod Validator & Packager")
                    appendLine("Project  : ${rootFile.absolutePath}")
                    appendLine("Mod ID   : ${pkgRes.report.modId} (v${pkgRes.report.version}, game ${pkgRes.report.game})")
                    appendLine("Revisions: horizon=${pkgRes.report.horizonRev.take(10)}… mixins=${pkgRes.report.mixinsRev.take(10)}…")
                    appendLine("Hooks API: ${pkgRes.report.hooksApi ?: 1}")
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
        val label =
            if (type == DetectedProjectType.HORIZON_MIXIN_MOD) "Build .hnmod · ${rootFile.name}"
            else "Run · ${rootFile.name}"
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
            RunOutputState.onOutput("⚠ Linux sandbox is not installed yet. Open Terminal once to set it up.")
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
            appendLine("⚠ Gradle sync required for \"$name\"")
            appendLine()
            appendLine("Tap the Sync button in the top bar once before building.")
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
