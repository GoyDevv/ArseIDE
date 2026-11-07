package com.goydevv.arseide.projects

import com.goydevv.arseide.settings.Preference
import java.io.File

/**
 * Per-project Gradle build options, edited in the IDE Configuration view and applied by the Run
 * button (see [com.goydevv.arseide.runner.ProjectRunner] → `project_runner.sh`).
 */
object GradleConfig {

    data class Flag(val arg: String, val label: String, val description: String)

    val ADDITIONAL_FLAGS: List<Flag> =
        listOf(
            Flag("--build-cache", "Build cache", "Reuse cached task outputs for faster builds (enabled by default)."),
            Flag("--stacktrace", "Stacktrace", "Print a full stacktrace when the build fails."),
            Flag("--offline", "Offline", "Build using only cached dependencies (no network access)."),
            Flag("--refresh-dependencies", "Refresh dependencies", "Re-download and re-validate every dependency."),
            Flag("--rerun-tasks", "Rerun tasks", "Ignore up-to-date checks and rerun every task."),
            Flag("--no-build-cache", "No build cache", "Do not read from or write to the build cache."),
            Flag("--parallel", "Parallel", "Build independent modules in parallel."),
            Flag("--continue", "Continue on failure", "Keep building other tasks after one fails."),
            Flag("--no-daemon", "No daemon", "Run the build without the long-lived Gradle daemon."),
            Flag("--warning-mode=all", "All warnings", "Show every warning, including deprecations."),
        )

    enum class LogLevel(val arg: String, val label: String, val description: String) {
        QUIET("--quiet", "Quiet", "Errors only."),
        WARN("--warn", "Warn", "Warnings and errors."),
        LIFECYCLE("", "Lifecycle (Recommended)", "Standard Gradle build progress without verbose cache warnings."),
        INFO("--info", "Info", "Verbose output."),
        DEBUG("--debug", "Debug", "Everything — for deep troubleshooting."),
    }

    enum class BuildType(val id: String, val label: String, val description: String) {
        DEBUG("debug", "Debug", "Fast, debuggable build (Android: assembleDebug)."),
        RELEASE("release", "Release", "Optimized, shippable build (Android: assembleRelease)."),
    }

    /**
     * Default to LIFECYCLE instead of INFO so Gradle never spams "Build cache is disabled" on every task,
     * and enable `--build-cache` by default unless the user explicitly selects `--no-build-cache`.
     */
    val DEFAULT_LOG_LEVEL = LogLevel.LIFECYCLE
    val DEFAULT_BUILD_TYPE = BuildType.DEBUG

    private fun keyFlags(root: File) = "gradle_flags::${root.absolutePath}"
    private fun keyLog(root: File) = "gradle_loglevel::${root.absolutePath}"
    private fun keyBuild(root: File) = "gradle_buildtype::${root.absolutePath}"
    private fun keyFirstBuild(root: File) = "gradle_first_build_shown::${root.absolutePath}"

    fun firstBuildShown(root: File): Boolean = Preference.getBoolean(keyFirstBuild(root), false)

    fun setFirstBuildShown(root: File) = Preference.setBoolean(keyFirstBuild(root), true)

    fun selectedFlags(root: File): Set<String> {
        val raw = Preference.getString(keyFlags(root), "--build-cache")
        if (raw.isBlank()) return emptySet()
        return raw.split('\n').map { it.trim() }.filter { it.isNotEmpty() }.toSet()
    }

    fun isFlagEnabled(root: File, arg: String): Boolean = selectedFlags(root).contains(arg)

    fun setFlag(root: File, arg: String, enabled: Boolean) {
        val set = selectedFlags(root).toMutableSet()
        if (enabled) {
            set.add(arg)
            if (arg == "--no-build-cache") set.remove("--build-cache")
            if (arg == "--build-cache") set.remove("--no-build-cache")
        } else {
            set.remove(arg)
        }
        Preference.setString(keyFlags(root), set.joinToString("\n"))
    }

    fun logLevel(root: File): LogLevel =
        runCatching { LogLevel.valueOf(Preference.getString(keyLog(root), DEFAULT_LOG_LEVEL.name)) }
            .getOrDefault(DEFAULT_LOG_LEVEL)

    fun setLogLevel(root: File, level: LogLevel) = Preference.setString(keyLog(root), level.name)

    fun buildType(root: File): BuildType =
        runCatching { BuildType.valueOf(Preference.getString(keyBuild(root), DEFAULT_BUILD_TYPE.name)) }
            .getOrDefault(DEFAULT_BUILD_TYPE)

    fun setBuildType(root: File, type: BuildType) = Preference.setString(keyBuild(root), type.name)

    fun gradleArgs(root: File): String {
        val parts = mutableListOf<String>()
        logLevel(root).arg.takeIf { it.isNotEmpty() }?.let { parts.add(it) }
        val enabled = selectedFlags(root)
        ADDITIONAL_FLAGS.forEach { if (enabled.contains(it.arg)) parts.add(it.arg) }
        if (!parts.contains("--no-build-cache") && !parts.contains("--build-cache")) {
            parts.add("--build-cache")
        }
        return parts.joinToString(" ")
    }
}
