package com.goydevv.arseide.projects

import com.goydevv.arseide.resources.drawables

/**
 * Available project templates exposed by the "Create Project" flow.
 *
 * [recommendsSandbox] is true for templates whose toolchain (gradle, cargo wasm32, native npm modules,
 * python venvs, etc.) must run from an exec-capable Linux filesystem.
 */
enum class ProjectTemplate(
    val displayName: String,
    val recommendsSandbox: Boolean,
    val iconRes: Int,
    val description: String,
    val showsPackageName: Boolean = false,
) {
    ANDROID_COMPOSE(
        "Android (Material 3)",
        recommendsSandbox = true,
        iconRes = drawables.android,
        description = "Clean Jetpack Compose & Material You Android app (no test bloat, build cache enabled).",
        showsPackageName = true,
    ),
    HORIZON_CONTENT_MOD(
        "Horizon Content Mod (.hmod)",
        recommendsSandbox = false,
        iconRes = drawables.extension,
        description = "Declarative Horizon API 0.2 content mod: blocks, items, creative tabs, effects, menus & 26.3 assets/data.",
    ),
    HORIZON_WASM_CLIENT_MOD(
        "Horizon Client WASM Mod (.hmod)",
        recommendsSandbox = true,
        iconRes = drawables.rust,
        description = "Rust cdylib (wasm32-unknown-unknown) client/hybrid mod linked to horizon-mod-sdk (HUD, UI, keybinds, raycast, storage).",
    ),
    HORIZON_WASM_SERVER_MOD(
        "Horizon Server WASM Mod (.hmod)",
        recommendsSandbox = true,
        iconRes = drawables.server,
        description = "Server-side Rust WASM mod (API 0.3, export_server_mod!) with block_break, block_use, player_join, world edits & commands.",
    ),
    HORIZON_NATIVE_MIXIN_MOD(
        "Horizon Native Mixin (.hnmod)",
        recommendsSandbox = true,
        iconRes = drawables.bolt,
        description = "Native Horizon Mixin mod (.hnmod) with horizon-mixin.toml and Rust hooks targeting horizon_client::game::native_hooks::Game.",
    ),
    MINECRAFT_MOD(
        "Minecraft Java Mod",
        recommendsSandbox = true,
        iconRes = drawables.java,
        description = "A Fabric or Forge Java mod, configured with resolved Gradle versions.",
        showsPackageName = true,
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
        description = "A modern responsive website — HTML5, CSS, and JavaScript.",
    ),
    NONE(
        "Empty Project",
        recommendsSandbox = false,
        iconRes = drawables.folder,
        description = "Empty project — just a clean folder and README to start from.",
    );

    val showsMinecraftOptions: Boolean
        get() = this == MINECRAFT_MOD

    val isHorizonTemplate: Boolean
        get() =
            this == HORIZON_CONTENT_MOD ||
                this == HORIZON_WASM_CLIENT_MOD ||
                this == HORIZON_WASM_SERVER_MOD ||
                this == HORIZON_NATIVE_MIXIN_MOD
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
    val modVersion: String = "0.1.0",
    val minecraftVersion: String = "",
    val jdkVersion: String = "21",
    val sdkVersion: String = "",
    val initGit: Boolean = false,
) {
    /**
     * Sanitised mod id derived from [modId] (falls back to the project name).
     * Must match `^[a-z0-9_]{1,64}$` and never be `minecraft`, `c`, or `horizon`.
     */
    fun resolvedModId(): String {
        val reserved = setOf("minecraft", "c", "horizon")
        val base = modId.ifBlank { name }
        val cleaned =
            base.lowercase()
                .replace(Regex("[^a-z0-9_]"), "_")
                .replace(Regex("_+"), "_")
                .trim('_')
                .ifBlank { "modid" }
        val nonReserved = if (cleaned in reserved) "custom_$cleaned" else cleaned
        val prefixed = if (nonReserved.first().isLetter()) nonReserved else "mod_$nonReserved"
        return prefixed.take(64).let { if (it.length < 2) "${it}_mod" else it }
    }

    private fun sanitizePkgSegment(raw: String, fallback: String): String {
        val cleaned = raw.lowercase().replace(Regex("[^a-z0-9_]"), "").trim('_').ifBlank { fallback }
        return if (cleaned.first().isDigit()) "_$cleaned" else cleaned
    }

    /** Lowercase alphanumeric slug of the author, used to build a package name. */
    fun authorSlug(): String = sanitizePkgSegment(author, "goydevv")

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
            .let { if (it.size < 2) listOf("com", "goydevv") + it else it }
            .joinToString(".")

    /** Package as a relative path (e.g. com.goydevv -> com/goydevv). */
    fun packagePath(): String = resolvedPackageName().replace('.', '/')

    /** Android compile/target SDK API level; defaults to 35 when unset or invalid. */
    fun resolvedCompileSdk(): Int = sdkVersion.trim().toIntOrNull()?.takeIf { it in 21..99 } ?: 35

    /** Android minSdk — 24 by default, but never above the chosen compile SDK. */
    fun resolvedMinSdk(): Int = minOf(24, resolvedCompileSdk())
}
