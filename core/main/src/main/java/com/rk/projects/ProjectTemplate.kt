package com.rk.projects

import com.rk.resources.drawables

/**
 * Available project templates exposed by the "Create Project" flow.
 *
 * [recommendsSandbox] is true for templates whose toolchain (gradle, native npm modules, python
 * venvs, etc.) must run from an exec-capable Linux filesystem. Android shared storage
 * (Documents/Downloads) is mounted noexec and ignores Unix permission bits, so build tooling
 * cannot run there. For those templates we default the location to the terminal sandbox home.
 *
 * [iconRes] and [description] drive the icon-based template chooser in the Create Project sheet.
 */
enum class ProjectTemplate(
    val displayName: String,
    val recommendsSandbox: Boolean,
    val iconRes: Int,
    val description: String,
    val showsPackageName: Boolean = false,
) {
    NONE(
        "None",
        recommendsSandbox = false,
        iconRes = drawables.folder,
        description = "Empty project — just a clean folder and README to start from.",
    ),
    PYTHON3(
        "Python 3",
        recommendsSandbox = false,
        iconRes = drawables.python,
        description = "A Python 3 project with a ready-to-run main.py and requirements.txt.",
    ),
    PYTHON(
        "Python",
        recommendsSandbox = false,
        iconRes = drawables.python,
        description = "A minimal Python project ready to run in the sandbox.",
    ),
    NODEJS(
        "Node.js",
        recommendsSandbox = true,
        iconRes = drawables.javascript,
        description = "A Node.js app with package.json and an entry script.",
    ),
    WEB(
        "Static Web (HTML/CSS/JS)",
        recommendsSandbox = false,
        iconRes = drawables.html,
        description = "A modern responsive website — HTML5, Glassmorphic CSS, and JavaScript.",
    ),
    MINECRAFT_MOD(
        "Minecraft Java Mod",
        recommendsSandbox = true,
        iconRes = drawables.java,
        description = "A Fabric or Forge mod, configured with real resolved Gradle versions.",
        showsPackageName = true,
    ),
    ANDROID_COMPOSE(
        "Android (Jetpack Compose)",
        recommendsSandbox = true,
        iconRes = drawables.android,
        description = "An Android app built with Jetpack Compose and Material 3.",
        showsPackageName = true,
    );

    val showsMinecraftOptions: Boolean
        get() = this == MINECRAFT_MOD
}

/** Mod loaders supported by the Minecraft Java Mod template. */
enum class ModLoader(val displayName: String) {
    FABRIC("Fabric"),
    FORGE("Forge"),
}

/**
 * A fully resolved request to create a project. The dialog produces one of these and hands it to
 * [ProjectScaffolder].
 */
data class ProjectConfig(
    val name: String,
    val template: ProjectTemplate,
    val parentDir: java.io.File,
    val packageName: String = "",
    val author: String = "",
    val modLoader: ModLoader? = null,
    val modId: String = "",
    val modDescription: String = "",
    val modVersion: String = "1.0.0",
    val minecraftVersion: String = "",
    val jdkVersion: String = "21",
    val sdkVersion: String = "",
    val initGit: Boolean = false,
) {
    /** Sanitised mod id derived from [modId] (falls back to the project name). Must match ^[a-z][a-z0-9_]{1,63}$. */
    fun resolvedModId(): String {
        val base = modId.ifBlank { name }
        val cleaned = base.lowercase().replace(Regex("[^a-z0-9_]"), "_").trim('_').ifBlank { "modid" }
        val prefixed = if (cleaned.first().isLetter()) cleaned else "mod_$cleaned"
        return prefixed.take(63).let { if (it.length < 2) "${it}mod" else it }
    }

    private fun sanitizePkgSegment(raw: String, fallback: String): String {
        val cleaned = raw.lowercase().replace(Regex("[^a-z0-9_]"), "").trim('_').ifBlank { fallback }
        return if (cleaned.first().isDigit()) "_$cleaned" else cleaned
    }

    /** Lowercase alphanumeric slug of the author, used to build a package name. */
    fun authorSlug(): String = sanitizePkgSegment(author, "example")

    /** Lowercase alphanumeric slug of the project name. */
    fun nameSlug(): String = sanitizePkgSegment(name, "app")

    /**
     * Intelligently resolved package / applicationId. Uses the user-provided [packageName] when set,
     * otherwise derives `com.<author>.<name>` from the project name and author.
     */
    fun resolvedPackageName(): String =
        packageName
            .trim()
            .ifBlank { "com.${authorSlug()}.${nameSlug()}" }
            .lowercase()
            .split(".")
            .filter { it.isNotBlank() }
            .map { sanitizePkgSegment(it, "app") }
            .let { if (it.size < 2) listOf("com", "example") + it else it }
            .joinToString(".")

    /** Package as a relative path (e.g. com.example -> com/example). */
    fun packagePath(): String = resolvedPackageName().replace('.', '/')

    /** Android compile/target SDK API level; defaults to 35 when unset or invalid. */
    fun resolvedCompileSdk(): Int = sdkVersion.trim().toIntOrNull()?.takeIf { it in 21..99 } ?: 35

    /** Android minSdk — 24 by default, but never above the chosen compile SDK. */
    fun resolvedMinSdk(): Int = minOf(24, resolvedCompileSdk())
}
