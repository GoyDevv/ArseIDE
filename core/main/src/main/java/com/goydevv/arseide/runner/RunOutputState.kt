package com.goydevv.arseide.runner

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

object RunOutputState {

    private const val MAX_LINES = 600
    private const val MAX_CHARS = 96_000

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
    private val lineBuffer = ArrayDeque<String>(MAX_LINES + 16)
    private var totalChars = 0
    private var dirty = false
    private var lastFlushMs = 0L

    private fun cleanLine(raw: String): String = ansiRegex.replace(raw, "").trimEnd('\r')

    private fun shouldIgnoreLine(line: String): Boolean {
        val trimmed = line.trim()
        return trimmed == "Build cache is disabled" ||
            trimmed.startsWith("Caching disabled for task ")
    }

    @Synchronized
    fun begin(label: String): Boolean {
        if (isRunning) return false
        lineBuffer.clear()
        totalChars = 0
        dirty = false
        lastFlushMs = 0L
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

    @Synchronized
    fun appendLine(rawLine: String) {
        val cleaned = cleanLine(rawLine)
        if (shouldIgnoreLine(cleaned)) return
        lineBuffer.addLast(cleaned)
        totalChars += cleaned.length + 1
        while (lineBuffer.size > MAX_LINES || totalChars > MAX_CHARS) {
            val removed = lineBuffer.removeFirstOrNull() ?: break
            totalChars = (totalChars - removed.length - 1).coerceAtLeast(0)
        }
        if (cleaned.isNotBlank()) {
            latestLine = cleaned.trim()
        }
        dirty = true
        val now = System.currentTimeMillis()
        if (now - lastFlushMs >= 120L) {
            flushLocked(now)
        }
    }

    @Synchronized
    fun flush() {
        if (dirty) {
            flushLocked(System.currentTimeMillis())
        }
    }

    private fun flushLocked(now: Long) {
        lastFlushMs = now
        dirty = false
        val sb = StringBuilder(totalChars.coerceAtLeast(64))
        for ((index, line) in lineBuffer.withIndex()) {
            if (index > 0) sb.append('\n')
            sb.append(line)
        }
        output = sb.toString()
    }

    @Synchronized
    fun onOutput(fullText: String) {
        lineBuffer.clear()
        totalChars = 0
        fullText.lineSequence().forEach { line ->
            val cleaned = cleanLine(line)
            if (!shouldIgnoreLine(cleaned)) {
                lineBuffer.addLast(cleaned)
                totalChars += cleaned.length + 1
                while (lineBuffer.size > MAX_LINES || totalChars > MAX_CHARS) {
                    val removed = lineBuffer.removeFirstOrNull() ?: break
                    totalChars = (totalChars - removed.length - 1).coerceAtLeast(0)
                }
                if (cleaned.isNotBlank()) {
                    latestLine = cleaned.trim()
                }
            }
        }
        dirty = true
        flushLocked(System.currentTimeMillis())
    }

    @Synchronized
    fun onFinished(exit: Int?) {
        if (dirty) {
            flushLocked(System.currentTimeMillis())
        }
        exitCode = exit
        isRunning = false
    }

    fun stop() {
        runCatching { stopper?.invoke() }
        isRunning = false
    }

    @Synchronized
    fun dismiss() {
        if (isRunning) runCatching { stopper?.invoke() }
        lineBuffer.clear()
        totalChars = 0
        dirty = false
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
