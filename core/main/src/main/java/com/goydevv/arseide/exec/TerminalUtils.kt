package com.goydevv.arseide.exec

import android.app.Activity
import android.content.Intent
import com.goydevv.arseide.activities.terminal.Terminal
import com.goydevv.arseide.file.child
import com.goydevv.arseide.file.localDir
import com.goydevv.arseide.file.sandboxDir
import com.goydevv.arseide.file.sandboxHomeDir
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

fun isTerminalInstalled(): Boolean {
    val rootfs =
        sandboxDir().listFiles()?.filter {
            it.absolutePath != sandboxHomeDir().absolutePath &&
                it.absolutePath != sandboxDir().child("tmp").absolutePath
        } ?: emptyList()

    return localDir().child(".terminal_setup_ok_DO_NOT_REMOVE").exists() && rootfs.isNotEmpty()
}

suspend fun isTerminalWorking(): Boolean =
    withContext(Dispatchers.IO) {
        val process = ubuntuProcess(command = arrayOf("true"))
        return@withContext process.waitFor() == 0
    }

fun launchTerminal(activity: Activity, terminalCommand: TerminalCommand) {
    pendingCommand = terminalCommand
    activity.startActivity(Intent(activity, Terminal::class.java))
}
