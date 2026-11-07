package com.goydevv.arseide.runner.runners

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.os.Environment
import com.goydevv.arseide.DefaultScope
import com.goydevv.arseide.exec.TerminalCommand
import com.goydevv.arseide.exec.launchTerminal
import com.goydevv.arseide.file.FileObject
import com.goydevv.arseide.file.FileWrapper
import com.goydevv.arseide.file.child
import com.goydevv.arseide.file.localBinDir
import com.goydevv.arseide.icons.Icon
import com.goydevv.arseide.resources.drawables
import com.goydevv.arseide.resources.getString
import com.goydevv.arseide.resources.strings
import com.goydevv.arseide.runner.ProjectRunner
import com.goydevv.arseide.runner.Runner
import com.goydevv.arseide.terminal.setupAssetFile
import com.goydevv.arseide.utils.dialogRes
import kotlinx.coroutines.launch

object UniversalRunner : Runner() {

    override val id = "universal"
    override val label = strings.universal_runner.getString()
    override val description = strings.universal_runner_desc.getString()

    override fun matcher(fileObject: FileObject): Boolean {
        return Regex(
                ".*\\.(py|js|ts|java|kt|rs|rb|php|c|cpp|cc|cxx|cs|sh|bash|zsh|fish|pl|lua|r|R|hs|f90|f95|f03|f08|pas|tcl|elm|fsx|fs)$"
            )
            .matches(fileObject.getName())
    }

    @SuppressLint("SdCardPath")
    override suspend fun run(activity: Activity, fileObject: FileObject) {
        setupAssetFile("universal_runner")

        if (fileObject !is FileWrapper) {
            dialogRes(title = strings.attention.getString(), msg = strings.non_native_filetype.getString(), onOk = {})
            return
        }

        val path = fileObject.getAbsolutePath()
        if (
            path.startsWith("/sdcard") ||
                path.startsWith("/storage/") ||
                path.startsWith(Environment.getExternalStorageDirectory().absolutePath)
        ) {
            dialogRes(
                title = strings.attention.getString(),
                msg = strings.sdcard_filetype.getString(),
                okRes = strings.continue_action,
                onCancel = {},
                onOk = { DefaultScope.launch { launchUniversalRunner(activity, fileObject) } },
            )
            return
        }

        launchUniversalRunner(activity, fileObject)
    }

    suspend fun launchUniversalRunner(activity: Activity, fileObject: FileObject) {
        val sandboxFile = ProjectRunner.toSandboxPath(fileObject.getAbsolutePath())
        val sandboxDir = fileObject.getParentFile()?.getAbsolutePath()?.let { ProjectRunner.toSandboxPath(it) } ?: "/"
        launchTerminal(
            activity = activity,
            terminalCommand =
                TerminalCommand(
                    sandbox = true,
                    exe = "/bin/bash",
                    args = arrayOf(localBinDir().child("universal_runner").absolutePath, sandboxFile),
                    id = strings.universal_runner.getString(),
                    terminatePreviousSession = true,
                    workingDir = sandboxDir,
                ),
        )
    }

    override fun getIcon(context: Context): Icon {
        return Icon.ResourceIcon(drawables.run)
    }

    override suspend fun isRunning(): Boolean {
        return false
    }

    override suspend fun stop() {}
}
