package com.goydevv.arseide.terminal

import android.app.Activity
import android.content.Context
import com.goydevv.arseide.BuildConfig
import com.goydevv.arseide.activities.main.MainActivity
import com.goydevv.arseide.exec.pendingCommand
import com.goydevv.arseide.file.FileWrapper
import com.goydevv.arseide.file.child
import com.goydevv.arseide.file.localBinDir
import com.goydevv.arseide.file.localDir
import com.goydevv.arseide.file.localLibDir
import com.goydevv.arseide.file.sandboxHomeDir
import com.goydevv.arseide.filetree.FileTreeTab
import com.goydevv.arseide.runner.ProjectRunner
import com.goydevv.arseide.settings.Settings
import com.goydevv.arseide.tabs.editor.EditorTab
import com.goydevv.arseide.utils.application
import com.goydevv.arseide.utils.getSourceDirOfPackage
import com.goydevv.arseide.utils.getTempDir
import com.termux.terminal.TerminalSession
import com.termux.terminal.TerminalSessionClient
import java.io.File
import kotlinx.coroutines.runBlocking

object MkSession {

    fun createSession(
        context: Context,
        sessionClient: TerminalSessionClient,
        sessionId: String,
        isExtraction: Boolean = false,
        explicitWorkingDir: String? = null,
    ): Pair<TerminalSession, SessionPwd> {
        val envVariables =
            mapOf(
                "ANDROID_ART_ROOT" to System.getenv("ANDROID_ART_ROOT"),
                "ANDROID_DATA" to System.getenv("ANDROID_DATA"),
                "ANDROID_I18N_ROOT" to System.getenv("ANDROID_I18N_ROOT"),
                "ANDROID_ROOT" to System.getenv("ANDROID_ROOT"),
                "ANDROID_RUNTIME_ROOT" to System.getenv("ANDROID_RUNTIME_ROOT"),
                "ANDROID_TZDATA_ROOT" to System.getenv("ANDROID_TZDATA_ROOT"),
                "BOOTCLASSPATH" to System.getenv("BOOTCLASSPATH"),
                "DEX2OATBOOTCLASSPATH" to System.getenv("DEX2OATBOOTCLASSPATH"),
                "EXTERNAL_STORAGE" to System.getenv("EXTERNAL_STORAGE"),
                "PATH" to "${System.getenv("PATH")}:${localBinDir(context).absolutePath}",
            )

        val workingDir = explicitWorkingDir?.takeIf { it.isNotBlank() } ?: runBlocking { getPwd(context) }

        val tmpDir = File(getTempDir(), "terminal/$sessionId")

        if (tmpDir.exists()) {
            tmpDir.deleteRecursively()
        }

        tmpDir.mkdirs()

        val env =
            mutableListOf(
                "PROOT=${application!!.applicationInfo.nativeLibraryDir}/libproot.so",
                "PROOT_LOADER=${application!!.applicationInfo.nativeLibraryDir}/libloader.so",
                "PROOT_TMP_DIR=${tmpDir.absolutePath}",
                "WKDIR=${workingDir}",
                "PUBLIC_HOME=${context.getExternalFilesDir(null)?.absolutePath}",
                "COLORTERM=truecolor",
                "TERM=xterm-256color",
                "LANG=C.UTF-8",
                "DEBUG=${BuildConfig.DEBUG}",
                "LOCAL=${localDir(context).absolutePath}",
                "PRIVATE_DIR=${context.filesDir.parentFile!!.absolutePath}",
                "LD_LIBRARY_PATH=${localLibDir(context).absolutePath}",
                "EXT_HOME=${sandboxHomeDir(context)}",
                "HOME=${if (Settings.sandbox) "/home" else sandboxHomeDir(context)}",
                "PROMPT_DIRTRIM=2",
                "LINKER=${if (File("/system/bin/linker64").exists()) "/system/bin/linker64" else "/system/bin/linker"}",
                "NATIVE_LIB_DIR=${context.applicationInfo.nativeLibraryDir}",
                "SANDBOX=${Settings.sandbox}",
                "TMP_DIR=${getTempDir()}",
                "TMPDIR=${getTempDir()}",
                "TZ=UTC",
                "DOTNET_GCHeapHardLimit=1C0000000",
                "SOURCE_DIR=${context.applicationInfo.sourceDir}",
                "TERMUX_X11_SOURCE_DIR=${getSourceDirOfPackage(application!!, "com.termux.x11")}",
                "DISPLAY=:0",
            )

        val loader32 = "${context.applicationInfo.nativeLibraryDir}/libloader32.so"
        if (File(loader32).exists()) {
            env.add("PROOT_LOADER_32=$loader32")
        }

        when (Settings.seccomp_mode) {
            "yes" -> env.add("SECCOMP=1")
            "no" -> env.add("PROOT_NO_SECCOMP=1")
        }

        env.addAll(envVariables.map { "${it.key}=${it.value}" })

        pendingCommand?.env?.let { env.addAll(it) }

        setupTerminalFiles()

        val sandboxSH = localBinDir(context).child("sandbox")
        val setupSH = localBinDir(context).child("setup")

        val args: Array<String>

        val shell =
            if (pendingCommand == null) {
                args =
                    if (Settings.sandbox) {
                        arrayOf(sandboxSH.absolutePath)
                    } else {
                        arrayOf()
                    }
                "/system/bin/sh"
            } else if (pendingCommand!!.sandbox.not()) {
                args = pendingCommand!!.args
                pendingCommand!!.exe
            } else {
                args =
                    mutableListOf(sandboxSH.absolutePath, pendingCommand!!.exe, *pendingCommand!!.args)
                        .toTypedArray()

                "/system/bin/sh"
            }

        val actualShell: String
        val actualArgs: Array<String> =
            if (isExtraction) {
                actualShell = "/system/bin/sh"
                mutableListOf("-c", setupSH.absolutePath, *args).toTypedArray()
            } else {
                actualShell = shell
                arrayOf("-c", *args)
            }

        pendingCommand = null

        return TerminalSession(
            actualShell,
            localDir(context).absolutePath,
            actualArgs,
            env.toTypedArray(),
            Settings.terminal_scrollback_buffer,
            sessionClient,
        ) to workingDir
    }
}

private fun normalizeToTerminalPath(context: Context, absolutePath: String): String {
    if (!Settings.sandbox) return absolutePath
    val sandboxHomePrefix = sandboxHomeDir(context).absolutePath
    if (absolutePath == sandboxHomePrefix) return "/home"
    if (absolutePath.startsWith("$sandboxHomePrefix/")) {
        return "/home" + absolutePath.removePrefix(sandboxHomePrefix)
    }
    val localPrefix = localDir(context).absolutePath
    if (absolutePath.startsWith(localPrefix)) {
        return absolutePath.removePrefix(localPrefix).ifEmpty { "/home" }
    }
    return ProjectRunner.toSandboxPath(absolutePath)
}

suspend fun getPwd(context: Context): String {
    val pendingWorkingDir = pendingCommand?.workingDir
    if (!pendingWorkingDir.isNullOrBlank()) {
        return pendingWorkingDir
    }

    if (context is Activity && context.intent.hasExtra("cwd")) {
        val explicit = context.intent.getStringExtra("cwd")
        if (!explicit.isNullOrBlank()) {
            return normalizeToTerminalPath(context, explicit)
        }
    }

    val mainActivity = MainActivity.instance
    val drawerRoot = (mainActivity?.drawerViewModel?.currentDrawerTab as? FileTreeTab)?.root
    if (drawerRoot is FileWrapper) {
        return normalizeToTerminalPath(context, drawerRoot.getAbsolutePath())
    }

    val currentTab = mainActivity?.viewModel?.tabManager?.currentTab
    if (currentTab is EditorTab && currentTab.file is FileWrapper) {
        val projectRoot = ProjectRunner.resolveProjectRootPath(currentTab.projectRoot, currentTab.file)
        if (!projectRoot.isNullOrBlank()) {
            return normalizeToTerminalPath(context, projectRoot)
        }
        val parent = currentTab.file.getParentFile()
        if (parent is FileWrapper) {
            return normalizeToTerminalPath(context, parent.getAbsolutePath())
        }
    }

    return if (Settings.sandbox) {
        "/home"
    } else {
        sandboxHomeDir(context).absolutePath
    }
}
