package com.goydevv.arseide.projects

import com.goydevv.arseide.resources.drawables

/**
 * Project templates in the "Create Project" sheet.
 *
 * Primary templates (Minecraft Java Mod, Android Compose, Python, Node.js, Web, Empty) are listed
 * first, and Horizon Native Mod (`.hnmod`) templates are placed at the very end of the list.
 */
enum class ProjectTemplate(
    val displayName: String,
    val recommendsSandbox: Boolean,
    val iconRes: Int,
    val description: String,
    val showsPackageName: Boolean = false,
) {
    MINECRAFT_MOD(
        "Minecraft Java Mod",
        recommendsSandbox = true,
        iconRes = drawables.java,
        description = "Fabric or Forge Java mod with Gradle wrapper.",
        showsPackageName = true,
    ),
    ANDROID_COMPOSE(
        "Android (Compose)",
        recommendsSandbox = true,
        iconRes = drawables.android,
        description = "Jetpack Compose & Material 3 Android app.",
        showsPackageName = true,
    ),
    PYTHON3(
        "Python 3",
        recommendsSandbox = false,
        iconRes = drawables.python,
        description = "Python 3 script with main.py and requirements.txt.",
    ),
    PYTHON(
        "Python",
        recommendsSandbox = false,
        iconRes = drawables.python,
        description = "Minimal Python script.",
    ),
    NODEJS(
        "Node.js",
        recommendsSandbox = true,
        iconRes = drawables.javascript,
        description = "Node.js project with package.json and index.js.",
    ),
    WEB(
        "Static Web",
        recommendsSandbox = false,
        iconRes = drawables.html,
        description = "HTML5, CSS, and JavaScript starter.",
    ),
    NONE(
        "Empty Folder",
        recommendsSandbox = false,
        iconRes = drawables.folder,
        description = "Clean folder with README and .gitignore.",
    ),
    HORIZON_GAMEPLAY_HNMOD(
        "Horizon Gameplay (.hnmod)",
        recommendsSandbox = true,
        iconRes = drawables.extension,
        description = "Horizon Native Mod (.hnmod) with block break, key, and settings hooks.",
    ),
    HORIZON_HUD_COMMAND_HNMOD(
        "Horizon HUD & Command (.hnmod)",
        recommendsSandbox = true,
        iconRes = drawables.bolt,
        description = "Horizon Native Mod (.hnmod) with client HUD overlay and slash command hooks.",
    ),
    HORIZON_CRATE_ENGINE_HNMOD(
        "Horizon Engine Crate (.hnmod)",
        recommendsSandbox = true,
        iconRes = drawables.rust,
        description = "Horizon Native Mod (.hnmod) with companion Cargo crate under mixins/<id>/engine/.",
    );

    val showsMinecraftOptions: Boolean
        get() = this == MINECRAFT_MOD

    val isHorizonTemplate: Boolean
        get() =
            this == HORIZON_GAMEPLAY_HNMOD ||
                this == HORIZON_HUD_COMMAND_HNMOD ||
                this == HORIZON_CRATE_ENGINE_HNMOD
}

/** Mod loaders supported by the Minecraft Java Mod template. */
enum class ModLoader(val displayName: String) {
    FABRIC("Fabric"),
    FORGE("Forge"),
}

/**
 * Resolved parameters for scaffolding a new project via [ProjectScaffolder].
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
    /** Sanitised mod id (`[a-z0-9_]{1,64}`, never `minecraft`, `c`, or `horizon`). */
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

    fun authorSlug(): String = sanitizePkgSegment(author, "goydevv")

    fun nameSlug(): String = sanitizePkgSegment(name, "app")

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

    fun packagePath(): String = resolvedPackageName().replace('.', '/')

    fun resolvedCompileSdk(): Int = sdkVersion.trim().toIntOrNull()?.takeIf { it in 21..99 } ?: 35

    fun resolvedMinSdk(): Int = minOf(24, resolvedCompileSdk())
}
