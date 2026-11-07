package com.goydevv.arseide.runner

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Shared, observable state for the floating build/run view shown in the editor.
 */
object RunOutputState {

    var isActive by mutableStateOf(false)
        private set

    var isRunning by mutableStateOf(false)
        private set

    var label by mutableStateOf("")
        private set

    var latestLine by mutableStateOf("")
        private set

    var output by mutableStateOf("")
        private set

    var exitCode by mutableStateOf<Int?>(null)
        private set

    var expanded by mutableStateOf(false)

    var autoScroll by mutableStateOf(true)

    private var stopper: (() -> Unit)? = null

    private val ansiRegex = Regex("\u001B\\[[0-9;?]*[ -/]*[@-~]")

    private fun stripAnsi(text: String): String =
        ansiRegex
            .replace(text, "")
            .lineSequence()
            .filterNot { line ->
                line.contains("Caching disabled for task") ||
                    line.contains("Build cache is disabled")
            }
            .joinToString("\n")

    @Synchronized
    fun begin(label: String): Boolean {
        if (isRunning) return false
        this.label = label
        latestLine = ""
        output = ""
        exitCode = null
        isRunning = true
        isActive = true
        stopper = null
        expanded = false
        return true
    }

    fun setStopper(fn: () -> Unit) {
        stopper = fn
    }

    fun onOutput(fullText: String) {
        val clean = stripAnsi(fullText)
        output = clean
        clean.lineSequence().lastOrNull { it.isNotBlank() }?.let { latestLine = it.trim() }
    }

    fun onFinished(exit: Int?) {
        exitCode = exit
        isRunning = false
    }

    fun stop() {
        runCatching { stopper?.invoke() }
        isRunning = false
    }

    fun dismiss() {
        if (isRunning) runCatching { stopper?.invoke() }
        isActive = false
        isRunning = false
        label = ""
        latestLine = ""
        output = ""
        exitCode = null
        stopper = null
        expanded = false
    }
}
