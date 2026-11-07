package com.goydevv.arseide.horizon

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.goydevv.arseide.settings.Settings
import com.goydevv.arseide.utils.application
import java.io.File
import java.util.zip.ZipFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * Automated Java (`.jar` or Fabric / NeoForge / Forge project folder) -> Horizon Native Mod (`.hnmod`)
 * Converter.
 *
 * Follows the exact architecture in `horizon-hnmod-complete-kit.zip` (`tools/port_java_to_hnmod.py`):
 * 1. Accepts a `.jar` selected via the system file picker (`Uri`) or a `File` (`.jar` or project folder).
 * 2. Extracts metadata from `fabric.mod.json`, `META-INF/neoforge.mods.toml`, or `META-INF/mods.toml`.
 * 3. Scans `.class` bytecode constant pools and `.java`/`.kt` sources to detect hooks (`hud`, `key`,
 *    `destroy_block`, `right_click_block`, `use_item`, `tooltip`, `command`, `server_tick`,
 *    `living_damage`, `loot`).
 * 4. Generates a complete `.hnmod` project tree:
 *    - `horizon-native.json` at root (`schema = 1`, `id`, `version`, `game = "26.3"`, 40-char hex
 *      `horizon` and `mixins` revisions, and computed `hooks` API level)
 *    - `mixins/<id>/horizon-mixin.toml`
 *    - `mixins/<id>/runtime.rs`
 *    - `mixins/<id>/` hook files (`list.rs`, `settings.rs`, `settings_click.rs`, `tick.rs`, etc.)
 *    - `mixins/<id>/data/port_manifest.json`
 *    - `mixins/<id>/engine/Cargo.toml`, `README.md`, and `src/lib.rs`
 * 5. Validates and packages `<id>.hnmod`.
 */
object HorizonModPorter {

    data class PortResult(
        val success: Boolean,
        val outputDir: File,
        val packagedHnmod: File?,
        val modId: String,
        val modName: String,
        val version: String,
        val hooksApiLevel: Int,
        val detectedHooks: List<String>,
        val classesScanned: Int,
        val blocksDiscovered: Int,
        val itemsDiscovered: Int,
        val logs: List<String>,
        val error: String? = null,
    )

    private val RESERVED_IDS = setOf("minecraft", "c", "horizon")

    fun sanitizeModId(raw: String): String {
        var cleaned =
            raw.trim()
                .lowercase()
                .replace(Regex("[^a-z0-9_]"), "_")
                .replace(Regex("_+"), "_")
                .trim('_')
        if (cleaned.isEmpty() || cleaned in RESERVED_IDS) {
            cleaned = "ported_${cleaned.ifEmpty { "mod" }}"
        }
        if (cleaned.first().isDigit()) {
            cleaned = "mod_$cleaned"
        }
        return cleaned.take(64)
    }

    private fun sanitizeVersion(raw: String): String {
        val t = raw.trim()
        return if (t.isEmpty() || "${"$"}{" in t) "0.1.0" else t
    }

    /**
     * Converts a `.jar` selected from the Android system file picker (`Uri`) into a complete
     * `.hnmod` project under [targetParentDir] and packages `<id>.hnmod`.
     */
    suspend fun portFromUri(
        context: Context,
        jarUri: Uri,
        targetParentDir: File,
        customOutputName: String? = null,
    ): PortResult =
        withContext(Dispatchers.IO) {
            val displayName = resolveDisplayName(context, jarUri) ?: "selected_mod.jar"
            val tempJar =
                File(
                    context.cacheDir,
                    "picked_${System.currentTimeMillis()}_${displayName.replace(Regex("[^A-Za-z0-9._-]"), "_")}",
                )
            try {
                context.contentResolver.openInputStream(jarUri)?.use { input ->
                    tempJar.outputStream().use { output -> input.copyTo(output) }
                }
                    ?: return@withContext failResult(
                        targetParentDir,
                        listOf(),
                        "Could not read selected file from system picker.",
                    )

                port(
                    sourceFileOrDir = tempJar,
                    targetParentDir = targetParentDir,
                    customOutputName = customOutputName,
                    displaySourceName = displayName,
                )
            } finally {
                runCatching { tempJar.delete() }
            }
        }

    suspend fun port(
        sourceFileOrDir: File,
        targetParentDir: File,
        customOutputName: String? = null,
        displaySourceName: String? = null,
    ): PortResult =
        withContext(Dispatchers.IO) {
            val logs = mutableListOf<String>()
            var tempExtractDir: File? = null
            val sourceLabel = displaySourceName ?: sourceFileOrDir.name

            try {
                if (!sourceFileOrDir.exists()) {
                    return@withContext failResult(
                        targetParentDir,
                        logs,
                        "Source not found: ${sourceFileOrDir.absolutePath}",
                    )
                }

                val workingSourceDir: File =
                    if (sourceFileOrDir.isFile) {
                        logs += "Reading archive $sourceLabel…"
                        val tmp = File(targetParentDir, ".tmp_hnmod_${System.currentTimeMillis()}")
                        tmp.mkdirs()
                        tempExtractDir = tmp
                        extractZipOrJar(sourceFileOrDir, tmp)
                        tmp
                    } else if (sourceFileOrDir.isDirectory) {
                        logs += "Scanning project directory $sourceLabel…"
                        sourceFileOrDir
                    } else {
                        return@withContext failResult(targetParentDir, logs, "Unsupported source input.")
                    }

                val fallbackBase = sourceLabel.substringBeforeLast('.')
                val meta = parseJavaMetadata(workingSourceDir, fallbackBase)
                val modId = meta.id
                val version = meta.version
                logs += "Metadata: id=$modId, name=\"${meta.name}\", version=$version"

                val features = scanJavaFeatures(workingSourceDir)
                logs +=
                    "Scanned ${features.classes.size} classes, ${features.blocks.size} blocks, ${features.items.size} items."
                logs += "Detected hooks: ${features.activeHooks().joinToString(", ")}"

                val outFolderName = customOutputName?.trim()?.ifBlank { null } ?: "$modId-hnmod"
                val projectDir = File(targetParentDir, outFolderName)
                if (projectDir.exists()) projectDir.deleteRecursively()

                val mixinDir = File(projectDir, "mixins/$modId")
                val engineSrcDir = File(mixinDir, "engine/src")
                val dataDir = File(mixinDir, "data")
                engineSrcDir.mkdirs()
                dataDir.mkdirs()

                // 1. Write mixins/<id>/data/port_manifest.json
                val portManifest =
                    JSONObject().apply {
                        put("source", sourceLabel)
                        put("mod_id", modId)
                        put("name", meta.name)
                        put("version", version)
                        put("detected_hooks", JSONArray(features.activeHooks()))
                        put("java_classes_count", features.classes.size)
                        put("java_classes_sample", JSONArray(features.classes.take(50)))
                        put("blocks_discovered", JSONArray(features.blocks.distinct().sorted()))
                        put("items_discovered", JSONArray(features.items.distinct().sorted()))
                    }
                File(dataDir, "port_manifest.json").writeText(portManifest.toString(2) + "\n")

                val crateName = "${modId.replace('_', '-')}-engine"
                val crateIdent = "${modId}_engine"
                val runtimeMod = "${modId}_runtime"

                val injectionsToml = mutableListOf<String>()
                val helperFiles = linkedMapOf<String, String>()
                var maxHookApi = 1

                // Standard Options > Mods registration & client tick
                injectionsToml +=
                    """
                    [[injection]]
                    id = "list"
                    kind = "HEAD"
                    target = "horizon_client::game::native_hooks::Game<'_>::native_mods"
                    helper = "list.rs"
                    bindings = [
                      { name = "mods", ty = "&mut Vec<crate::game::native_hooks::NativeMod>", value = "&mut *mods" },
                    ]

                    [[injection]]
                    id = "settings"
                    kind = "HEAD"
                    target = "horizon_client::game::native_hooks::Game<'_>::native_settings"
                    helper = "settings.rs"
                    bindings = [
                      { name = "game", ty = "&mut crate::game::Game<'_>", value = "&mut *self" },
                      { name = "mod_id", ty = "&str", value = "mod_id" },
                      { name = "buttons", ty = "&mut Vec<String>", value = "&mut *buttons" },
                    ]

                    [[injection]]
                    id = "settings_click"
                    kind = "HEAD"
                    target = "horizon_client::game::native_hooks::Game<'_>::native_settings_click"
                    helper = "settings_click.rs"
                    bindings = [
                      { name = "game", ty = "&mut crate::game::Game<'_>", value = "&mut *self" },
                      { name = "mod_id", ty = "&str", value = "mod_id" },
                      { name = "button", ty = "usize", value = "button" },
                    ]

                    [[injection]]
                    id = "tick"
                    kind = "HEAD"
                    target = "horizon_client::game::native_hooks::Game<'_>::native_tick"
                    helper = "tick.rs"
                    bindings = [
                      { name = "game", ty = "&mut crate::game::Game<'_>", value = "&mut *self" },
                    ]
                    """
                        .trimIndent()

                helperFiles["list.rs"] =
                    """
                    {
                        mods.push(crate::game::native_hooks::NativeMod {
                            id: ${jsonString(modId)}.to_string(),
                            name: ${jsonString(meta.name)}.to_string(),
                            version: ${jsonString(version)}.to_string(),
                            description: ${jsonString(meta.description)}.to_string(),
                        });
                    }
                    """
                        .trimIndent() + "\n"

                helperFiles["settings.rs"] =
                    """
                    {
                        let _ = game;
                        if mod_id == ${jsonString(modId)} {
                            let status = if $crateIdent::is_enabled() { "ON" } else { "OFF" };
                            buttons.push(format!("${meta.name.replace("\"", "'")}: {status}"));
                            buttons.push(format!("HUD Overlay: {}", if $crateIdent::is_hud_enabled() { "Shown" } else { "Hidden" }));
                        }
                    }
                    """
                        .trimIndent() + "\n"

                helperFiles["settings_click.rs"] =
                    """
                    {
                        let _ = game;
                        if mod_id == ${jsonString(modId)} {
                            match button {
                                0 => { $crateIdent::toggle_enabled(); }
                                1 => { $crateIdent::toggle_hud(); }
                                _ => {}
                            }
                        }
                    }
                    """
                        .trimIndent() + "\n"

                helperFiles["tick.rs"] =
                    """
                    {
                        let _ = game;
                        if $crateIdent::is_enabled() {
                            $crateIdent::on_tick();
                        }
                    }
                    """
                        .trimIndent() + "\n"

                if (features.hud) {
                    injectionsToml +=
                        """
                        [[injection]]
                        id = "hud"
                        kind = "HEAD"
                        target = "horizon_client::game::native_hooks::Game<'_>::native_hud"
                        helper = "hud.rs"
                        bindings = [
                          { name = "game", ty = "&crate::game::Game<'_>", value = "&*self" },
                          { name = "shapes", ty = "&mut Vec<crate::game::native_hooks::NativeShape>", value = "&mut *shapes" },
                        ]
                        """
                            .trimIndent()
                    helperFiles["hud.rs"] =
                        """
                        {
                            if $crateIdent::is_enabled() && $crateIdent::is_hud_enabled() && !game.native_in_menu() {
                                shapes.push(crate::game::native_hooks::NativeShape::Text {
                                    text: $crateIdent::hud_label(),
                                    x: 8.0,
                                    y: 8.0,
                                    color: 0xFF7DD3FC,
                                    scale: 1.0,
                                    centered: false,
                                    shadow: true,
                                });
                            }
                        }
                        """
                            .trimIndent() + "\n"
                }

                if (features.key) {
                    injectionsToml +=
                        """
                        [[injection]]
                        id = "key"
                        kind = "HEAD"
                        target = "horizon_client::game::native_hooks::Game<'_>::native_key"
                        helper = "key.rs"
                        bindings = [
                          { name = "game", ty = "&mut crate::game::Game<'_>", value = "&mut *self" },
                          { name = "key", ty = "&str", value = "key" },
                          { name = "pressed", ty = "bool", value = "pressed" },
                        ]
                        """
                            .trimIndent()
                    helperFiles["key.rs"] =
                        """
                        {
                            if pressed && !game.native_in_menu() {
                                if let Some(msg) = $crateIdent::on_key_press(key) {
                                    game.native_chat(&msg);
                                }
                            }
                        }
                        """
                            .trimIndent() + "\n"
                }

                if (features.tooltip) {
                    maxHookApi = maxOf(maxHookApi, 12)
                    injectionsToml +=
                        """
                        [[injection]]
                        id = "tooltip"
                        kind = "HEAD"
                        target = "horizon_client::game::native_hooks::Game<'_>::native_tooltip"
                        helper = "tooltip.rs"
                        bindings = [
                          { name = "game", ty = "&crate::game::Game<'_>", value = "&*self" },
                          { name = "stack", ty = "&crate::game::native_hooks::NativeStack", value = "stack" },
                          { name = "lines", ty = "&mut Vec<crate::game::native_hooks::NativeTooltipLine>", value = "&mut *lines" },
                        ]
                        """
                            .trimIndent()
                    helperFiles["tooltip.rs"] =
                        """
                        {
                            let _ = (game, stack);
                            if $crateIdent::is_enabled() {
                                lines.push(crate::game::native_hooks::NativeTooltipLine {
                                    text: format!("[${meta.name.replace("\"", "'")}]"),
                                    color: 0xFF93C5FD,
                                });
                            }
                        }
                        """
                            .trimIndent() + "\n"
                }

                if (features.destroyBlock) {
                    maxHookApi = maxOf(maxHookApi, 14)
                    injectionsToml +=
                        """
                        [[injection]]
                        id = "destroy_block"
                        kind = "HEAD"
                        target = "horizon_client::game::native_hooks::Game<'_>::native_destroy_block"
                        helper = "destroy_block.rs"
                        bindings = [
                          { name = "game", ty = "&mut crate::game::Game<'_>", value = "&mut *self" },
                          { name = "pos", ty = "[i32; 3]", value = "pos" },
                          { name = "state", ty = "&str", value = "state" },
                          { name = "canceled", ty = "&mut bool", value = "&mut *canceled" },
                        ]
                        """
                            .trimIndent()
                    helperFiles["destroy_block.rs"] =
                        """
                        {
                            let _ = game;
                            if !*canceled && $crateIdent::is_enabled() {
                                if $crateIdent::should_cancel_block_break(pos, state) {
                                    *canceled = true;
                                }
                            }
                        }
                        """
                            .trimIndent() + "\n"
                }

                if (features.rightClickBlock) {
                    maxHookApi = maxOf(maxHookApi, 21)
                    injectionsToml +=
                        """
                        [[injection]]
                        id = "right_click_block"
                        kind = "HEAD"
                        target = "horizon_client::game::native_hooks::Game<'_>::native_right_click_block"
                        helper = "right_click_block.rs"
                        bindings = [
                          { name = "game", ty = "&mut crate::game::Game<'_>", value = "&mut *self" },
                          { name = "pos", ty = "[i32; 3]", value = "pos" },
                          { name = "canceled", ty = "&mut bool", value = "&mut *canceled" },
                        ]
                        """
                            .trimIndent()
                    helperFiles["right_click_block.rs"] =
                        """
                        {
                            let _ = game;
                            if !*canceled && $crateIdent::is_enabled() {
                                $crateIdent::on_right_click_block(pos);
                            }
                        }
                        """
                            .trimIndent() + "\n"
                }

                if (features.command) {
                    maxHookApi = maxOf(maxHookApi, 35)
                    injectionsToml +=
                        """
                        [[injection]]
                        id = "commands"
                        kind = "HEAD"
                        target = "horizon_client::game::native_hooks::Game<'_>::native_commands"
                        helper = "commands.rs"
                        bindings = [
                          { name = "commands", ty = "&mut Vec<String>", value = "&mut *commands" },
                        ]

                        [[injection]]
                        id = "command"
                        kind = "HEAD"
                        target = "horizon_client::game::native_hooks::Game<'_>::native_command"
                        helper = "command.rs"
                        bindings = [
                          { name = "game", ty = "&mut crate::game::Game<'_>", value = "&mut *self" },
                          { name = "name", ty = "&str", value = "name" },
                          { name = "args", ty = "&str", value = "args" },
                          { name = "ran", ty = "&mut bool", value = "&mut *ran" },
                        ]
                        """
                            .trimIndent()
                    helperFiles["commands.rs"] =
                        """
                        {
                            commands.push(${jsonString(modId)}.to_string());
                        }
                        """
                            .trimIndent() + "\n"
                    helperFiles["command.rs"] =
                        """
                        {
                            if name == ${jsonString(modId)} {
                                let reply = $crateIdent::on_command(args);
                                game.native_chat(&reply);
                                *ran = true;
                            }
                        }
                        """
                            .trimIndent() + "\n"
                }

                val horizonRev =
                    Settings.horizon_revision.takeIf { HorizonModValidator.isValidRevision(it) }
                        ?: HorizonModValidator.DEFAULT_HORIZON_REV
                val mixinsRev =
                    Settings.mixins_revision.takeIf { HorizonModValidator.isValidRevision(it) }
                        ?: HorizonModValidator.DEFAULT_MIXINS_REV
                val configuredMinHooks = Settings.horizon_hooks_api.coerceAtLeast(1)
                val finalHooksApi = maxOf(maxHookApi, configuredMinHooks)

                val mixinTomlContent =
                    buildString {
                        appendLine("[mod]")
                        appendLine("id = ${jsonString(modId)}")
                        appendLine("version = ${jsonString(version)}")
                        appendLine("priority = 100")
                        appendLine()
                        appendLine("[compatibility]")
                        appendLine("horizon_revision = ${jsonString(horizonRev)}")
                        appendLine()
                        appendLine("[[crate]]")
                        appendLine("id = \"engine\"")
                        appendLine("path = \"engine\"")
                        appendLine()
                        appendLine("[[module]]")
                        appendLine("id = \"runtime\"")
                        appendLine("file = \"crates/horizon-client/src/game.rs\"")
                        appendLine("name = \"$runtimeMod\"")
                        appendLine("source = \"runtime.rs\"")
                        appendLine()
                        append(injectionsToml.joinToString("\n\n"))
                        appendLine()
                    }
                File(mixinDir, "horizon-mixin.toml").writeText(mixinTomlContent)
                File(mixinDir, "runtime.rs").writeText("pub use $crateIdent::*;\n")

                for ((hName, hCode) in helperFiles) {
                    File(mixinDir, hName).writeText(hCode)
                }

                // Write companion crate under mixins/<id>/engine/
                File(mixinDir, "engine/Cargo.toml").writeText(
                    """
                    [package]
                    name = "$crateName"
                    version = "$version"
                    edition = "2024"

                    [dependencies]
                    """
                        .trimIndent() + "\n"
                )
                File(mixinDir, "engine/README.md").writeText(
                    "# ${meta.name} ($modId)\n\nPorted from `$sourceLabel` to Horizon `.hnmod`.\n"
                )

                val safeDisplayName = meta.name.replace("\"", "'")
                File(engineSrcDir, "lib.rs").writeText(
                    """
                    //! Ported engine crate for $safeDisplayName (`$modId`).
                    //! Discovered ${features.classes.size} Java classes, ${features.blocks.distinct().size} blocks, and ${features.items.distinct().size} items.

                    use std::sync::atomic::{AtomicBool, AtomicU64, Ordering};

                    static ENABLED: AtomicBool = AtomicBool::new(true);
                    static HUD_ENABLED: AtomicBool = AtomicBool::new(true);
                    static TICK_COUNT: AtomicU64 = AtomicU64::new(0);

                    pub fn is_enabled() -> bool {
                        ENABLED.load(Ordering::Relaxed)
                    }

                    pub fn toggle_enabled() -> bool {
                        let next = !is_enabled();
                        ENABLED.store(next, Ordering::Relaxed);
                        next
                    }

                    pub fn is_hud_enabled() -> bool {
                        HUD_ENABLED.load(Ordering::Relaxed)
                    }

                    pub fn toggle_hud() -> bool {
                        let next = !is_hud_enabled();
                        HUD_ENABLED.store(next, Ordering::Relaxed);
                        next
                    }

                    pub fn on_tick() {
                        TICK_COUNT.fetch_add(1, Ordering::Relaxed);
                    }

                    pub fn hud_label() -> String {
                        format!("$safeDisplayName [Active]")
                    }

                    pub fn on_key_press(key: &str) -> Option<String> {
                        if key.eq_ignore_ascii_case("key.keyboard.r") {
                            let on = toggle_enabled();
                            return Some(format!("$safeDisplayName: {}", if on { "Enabled" } else { "Disabled" }));
                        }
                        None
                    }

                    pub fn should_cancel_block_break(_pos: [i32; 3], _state: &str) -> bool {
                        false
                    }

                    pub fn on_right_click_block(_pos: [i32; 3]) {}

                    pub fn on_command(args: &str) -> String {
                        if args.trim().eq_ignore_ascii_case("toggle") {
                            let on = toggle_enabled();
                            format!("$safeDisplayName toggled {}", if on { "ON" } else { "OFF" })
                        } else {
                            format!("$safeDisplayName v$version (ticks: {})", TICK_COUNT.load(Ordering::Relaxed))
                        }
                    }
                    """
                        .trimIndent() + "\n"
                )

                // Write root horizon-native.json
                val nativeManifest =
                    JSONObject().apply {
                        put("schema", 1)
                        put("id", modId)
                        put("version", version)
                        put("game", "26.3")
                        put("horizon", horizonRev)
                        put("mixins", mixinsRev)
                        put("hooks", finalHooksApi)
                    }
                File(projectDir, "horizon-native.json").writeText(nativeManifest.toString(2) + "\n")

                // Copy bundled tools into tools/ for sandbox CLI builds
                copyBundledTools(File(projectDir, "tools"))

                // Package <id>.hnmod
                val pkgRes =
                    HorizonModPackager.packageProject(
                        projectDir = projectDir,
                        outputFile = File(projectDir, "$modId.hnmod"),
                    )
                logs += pkgRes.message

                PortResult(
                    success = pkgRes.success,
                    outputDir = projectDir,
                    packagedHnmod = pkgRes.archiveFile,
                    modId = modId,
                    modName = meta.name,
                    version = version,
                    hooksApiLevel = finalHooksApi,
                    detectedHooks = features.activeHooks(),
                    classesScanned = features.classes.size,
                    blocksDiscovered = features.blocks.distinct().size,
                    itemsDiscovered = features.items.distinct().size,
                    logs = logs,
                    error = if (pkgRes.success) null else pkgRes.message,
                )
            } catch (t: Throwable) {
                failResult(targetParentDir, logs, t.message ?: "Conversion failed")
            } finally {
                tempExtractDir?.let { runCatching { it.deleteRecursively() } }
            }
        }

    // ---- Internal helpers -------------------------------------------------------

    private data class ModMetadata(
        val id: String,
        val name: String,
        val version: String,
        val description: String,
    )

    private data class ScannedFeatures(
        var hud: Boolean = false,
        var key: Boolean = false,
        var destroyBlock: Boolean = false,
        var rightClickBlock: Boolean = false,
        var useItem: Boolean = false,
        var tooltip: Boolean = false,
        var command: Boolean = false,
        var serverTick: Boolean = false,
        var livingDamage: Boolean = false,
        var loot: Boolean = false,
        val classes: MutableList<String> = mutableListOf(),
        val blocks: MutableList<String> = mutableListOf(),
        val items: MutableList<String> = mutableListOf(),
    ) {
        fun activeHooks(): List<String> =
            buildList {
                add("mods")
                add("settings")
                add("settings_click")
                add("tick")
                if (hud) add("hud")
                if (key) add("key")
                if (tooltip) add("tooltip")
                if (destroyBlock) add("destroy_block")
                if (rightClickBlock) add("right_click_block")
                if (command) {
                    add("commands")
                    add("command")
                }
            }
    }

    private fun parseJavaMetadata(workDir: File, fallbackName: String): ModMetadata {
        val defaultId = sanitizeModId(fallbackName)
        val defaultName = fallbackName.replace('-', ' ').replace('_', ' ').replaceFirstChar { it.uppercase() }
        val defaultDesc = "Ported from $fallbackName to Horizon .hnmod"

        for (prefix in listOf("", "src/main/resources/")) {
            val fjson = File(workDir, "${prefix}fabric.mod.json")
            if (fjson.isFile) {
                runCatching {
                    val d = JSONObject(fjson.readText())
                    val id = sanitizeModId(d.optString("id", defaultId))
                    val name = d.optString("name", defaultName).ifBlank { defaultName }
                    val ver = sanitizeVersion(d.optString("version", "0.1.0"))
                    val desc =
                        d.optString("description", defaultDesc)
                            .replace("\n", " ")
                            .trim()
                            .ifBlank { defaultDesc }
                    return ModMetadata(id, name, ver, desc)
                }
            }
        }

        for (prefix in listOf("", "src/main/resources/")) {
            for (tomlRel in listOf("META-INF/neoforge.mods.toml", "META-INF/mods.toml")) {
                val p = File(workDir, "$prefix$tomlRel")
                if (p.isFile) {
                    val txt = runCatching { p.readText() }.getOrDefault("")
                    val mId = Regex("""modId\s*=\s*"([^"]+)"""").find(txt)?.groupValues?.getOrNull(1)
                    val mName = Regex("""displayName\s*=\s*"([^"]+)"""").find(txt)?.groupValues?.getOrNull(1)
                    val mVer = Regex("""version\s*=\s*"([^"]+)"""").find(txt)?.groupValues?.getOrNull(1)
                    val mDesc =
                        (Regex("""description\s*=\s*'''(.*?)'''""", setOf(RegexOption.DOT_MATCHES_ALL)).find(txt)
                                ?: Regex("""description\s*=\s*""" + "\"\"\"(.*?)\"\"\"", setOf(RegexOption.DOT_MATCHES_ALL)).find(txt))
                            ?.groupValues
                            ?.getOrNull(1)
                    return ModMetadata(
                        id = sanitizeModId(mId ?: defaultId),
                        name = mName?.trim()?.ifBlank { defaultName } ?: defaultName,
                        version = sanitizeVersion(mVer ?: "0.1.0"),
                        description = mDesc?.split(Regex("\\s+"))?.joinToString(" ")?.take(200)?.ifBlank { defaultDesc } ?: defaultDesc,
                    )
                }
            }
        }

        return ModMetadata(defaultId, defaultName, "0.1.0", defaultDesc)
    }

    private fun scanJavaFeatures(workDir: File): ScannedFeatures {
        val features = ScannedFeatures()
        val blobParts = StringBuilder()

        workDir
            .walkTopDown()
            .onEnter { it.name !in setOf(".git", "build", ".gradle", "target", "node_modules") }
            .filter { it.isFile }
            .forEach { file ->
                val rel = file.relativeTo(workDir).invariantSeparatorsPath
                val fn = file.name
                when {
                    fn.endsWith(".class") || fn.endsWith(".java") || fn.endsWith(".kt") -> {
                        if (fn.endsWith(".class") && '$' !in fn) {
                            features.classes += rel.removeSuffix(".class")
                        } else if (fn.endsWith(".java") || fn.endsWith(".kt")) {
                            features.classes += rel.substringBeforeLast('.')
                        }
                        runCatching {
                            val bytes = file.inputStream().use { it.readNBytes(256_000) }
                            blobParts.append(String(bytes, Charsets.ISO_8859_1)).append('\n')
                        }
                    }
                    rel.startsWith("assets/") && "/blockstates/" in rel && fn.endsWith(".json") -> {
                        features.blocks += fn.removeSuffix(".json")
                    }
                    rel.startsWith("assets/") && "/models/item/" in rel && fn.endsWith(".json") -> {
                        features.items += fn.removeSuffix(".json")
                    }
                }
            }

        val text = blobParts.toString()
        if (listOf("HudRenderCallback", "RenderGuiOverlayEvent", "GuiGraphics", "DrawContext", "InGameHud").any { it in text }) {
            features.hud = true
        }
        if (listOf("KeyBinding", "KeyMapping", "GLFW_KEY_", "InputEvent").any { it in text }) {
            features.key = true
        }
        if (listOf("PlayerBlockBreakEvents", "BreakEvent", "destroyBlock", "playerWillDestroy").any { it in text }) {
            features.destroyBlock = true
        }
        if (listOf("UseBlockCallback", "RightClickBlock", "useWithoutItem", "useItemOn").any { it in text }) {
            features.rightClickBlock = true
        }
        if (listOf("UseItemCallback", "RightClickItem", "ItemStack", "use(").any { it in text }) {
            features.useItem = true
        }
        if (listOf("ItemTooltipCallback", "ItemTooltipEvent", "appendHoverText").any { it in text }) {
            features.tooltip = true
        }
        if (listOf("CommandRegistrationCallback", "RegisterCommandsEvent", "Commands.literal", "CommandManager.literal").any { it in text }) {
            features.command = true
        }
        if (listOf("ServerTickEvents", "ServerTickEvent", "LevelTickEvent").any { it in text }) {
            features.serverTick = true
        }
        if (listOf("LivingIncomingDamageEvent", "LivingHurtEvent", "LivingDamageEvent", "hurtServer").any { it in text }) {
            features.livingDamage = true
        }
        if (listOf("LootTableEvents", "LootTableLoadEvent", "LootPool").any { it in text }) {
            features.loot = true
        }

        if (!features.hud && !features.key && !features.destroyBlock && !features.rightClickBlock && !features.tooltip && !features.command) {
            features.hud = true
            features.key = true
        }

        return features
    }

    private fun extractZipOrJar(archiveFile: File, destDir: File) {
        ZipFile(archiveFile).use { zip ->
            val entries = zip.entries()
            while (entries.hasMoreElements()) {
                val entry = entries.nextElement()
                val outFile = File(destDir, entry.name)
                if (!outFile.canonicalPath.startsWith(destDir.canonicalPath)) continue
                if (entry.isDirectory) {
                    outFile.mkdirs()
                } else {
                    outFile.parentFile?.mkdirs()
                    zip.getInputStream(entry).use { input ->
                        outFile.outputStream().use { output -> input.copyTo(output) }
                    }
                }
            }
        }
    }

    private fun resolveDisplayName(context: Context, uri: Uri): String? {
        runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (idx >= 0) return cursor.getString(idx)
                }
            }
        }
        return uri.lastPathSegment?.substringAfterLast('/')
    }

    private fun jsonString(s: String): String = JSONObject.quote(s)

    fun copyBundledTools(targetToolsDir: File) {
        val assetPrefix = "horizon/tools"
        listOf("port_java_to_hnmod.py", "validate_and_pack_hnmod.py").forEach { name ->
            val dest = File(targetToolsDir, name)
            runCatching {
                dest.parentFile?.mkdirs()
                application!!.assets.open("$assetPrefix/$name").use { input ->
                    dest.outputStream().use { output -> input.copyTo(output) }
                }
                dest.setExecutable(true, false)
            }
        }
    }

    private fun failResult(targetParentDir: File, logs: List<String>, message: String): PortResult =
        PortResult(
            success = false,
            outputDir = targetParentDir,
            packagedHnmod = null,
            modId = "",
            modName = "",
            version = "",
            hooksApiLevel = 1,
            detectedHooks = emptyList(),
            classesScanned = 0,
            blocksDiscovered = 0,
            itemsDiscovered = 0,
            logs = logs + "ERROR: $message",
            error = message,
        )
}
