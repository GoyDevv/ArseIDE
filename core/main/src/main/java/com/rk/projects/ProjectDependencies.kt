package com.rk.projects

import com.rk.exec.ShellUtils
import com.rk.exec.TerminalCommand

/**
 * Detects and installs the toolchain a freshly created project needs.
 *
 * Detection runs headlessly in the Ubuntu sandbox via [ShellUtils.runUbuntu].
 * Installation is done in the visible terminal (apt-get) so the user can watch progress.
 */
object ProjectDependencies {

    /**
     * A required tool.
     *
     * @param name human readable name shown in the prompt.
     * @param checkCmd shell command used to detect presence in the sandbox (exit 0 = installed).
     * @param aptPackages apt packages that provide it.
     */
    data class Tool(val name: String, val checkCmd: String, val aptPackages: List<String>)

    /** Tools required by the given project, or empty for templates that need nothing. */
    fun requiredTools(config: ProjectConfig): List<Tool> =
        when (config.template) {
            ProjectTemplate.NONE -> emptyList()
            ProjectTemplate.WEB -> emptyList()
            ProjectTemplate.PYTHON3 ->
                listOf(
                    Tool(
                        "Python 3",
                        "command -v python3 >/dev/null 2>&1 && python3 -m pip --version >/dev/null 2>&1",
                        listOf("python3", "python3-pip", "python3-venv"),
                    )
                )
            ProjectTemplate.PYTHON ->
                listOf(
                    Tool(
                        "Python",
                        "command -v python3 >/dev/null 2>&1 && python3 -m pip --version >/dev/null 2>&1",
                        listOf("python3", "python3-pip", "python3-venv"),
                    )
                )
            ProjectTemplate.NODEJS ->
                listOf(
                    Tool(
                        "Node.js & npm",
                        "command -v node >/dev/null 2>&1 && command -v npm >/dev/null 2>&1",
                        listOf("nodejs", "npm"),
                    )
                )
            ProjectTemplate.MINECRAFT_MOD -> listOf(jdkTool(config.jdkVersion))
            ProjectTemplate.ANDROID_COMPOSE ->
                listOf(
                    jdkTool(config.jdkVersion.takeIf { it in listOf("17", "21") } ?: "21"),
                    Tool(
                        name = "Android SDK",
                        checkCmd =
                            "test -x \"\$HOME/android-sdk/cmdline-tools/latest/bin/sdkmanager\" && ls -d \"\$HOME/android-sdk/platforms/android-\"* >/dev/null 2>&1",
                        aptPackages = listOf("wget", "unzip", "aapt", "openjdk-17-jdk", "openjdk-21-jdk"),
                    ),
                )
        }

    private fun jdkTool(jdkVersion: String): Tool {
        val v = jdkVersion.ifBlank { "21" }
        return Tool(
            name = "JDK $v",
            checkCmd = "ls -d /usr/lib/jvm/java-${v}-openjdk* >/dev/null 2>&1",
            aptPackages = listOf("openjdk-$v-jdk"),
        )
    }

    /**
     * Returns the subset of [tools] that are not currently available in the sandbox. Safe to call
     * even if a check fails (a failing check is treated as "missing").
     */
    suspend fun missingTools(tools: List<Tool>): List<Tool> =
        tools.filter { tool ->
            val result =
                ShellUtils.runUbuntu(
                    command = arrayOf("bash", "-lc", tool.checkCmd),
                    timeoutSeconds = 15L,
                )
            result.timedOut || result.exitCode != 0
        }

    /** Builds a single terminal command that installs every missing package via apt. */
    fun installCommand(missing: List<Tool>): TerminalCommand {
        val packages = missing.flatMap { it.aptPackages }.distinct().joinToString(" ")
        val needsSdk = missing.any { it.name == "Android SDK" }
        val script =
            buildString {
                append("set -e; export DEBIAN_FRONTEND=noninteractive; ")
                if (packages.isNotBlank()) {
                    append("echo '==> Installing required tools: $packages'; ")
                    append("apt-get update -y; ")
                    append("apt-get install -y $packages; ")
                }
                if (needsSdk) {
                    append("$ANDROID_SDK_INSTALL; ")
                }
                append("echo; echo '==> All set. You can close this terminal.'")
            }
        return TerminalCommand(
            sandbox = true,
            exe = "/bin/bash",
            args = arrayOf("-lc", script),
            id = "install_project_deps",
            terminatePreviousSession = false,
        )
    }
}
