package com.goydevv.arseide.projects

import com.goydevv.arseide.horizon.HorizonModPackager
import com.goydevv.arseide.horizon.HorizonModPorter
import com.goydevv.arseide.horizon.HorizonModValidator
import com.goydevv.arseide.settings.Settings
import com.goydevv.arseide.utils.application
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.eclipse.jgit.api.Git

/**
 * Scaffolds new projects onto disk.
 */
object ProjectScaffolder {

    sealed interface Result {
        data class Success(val projectRoot: File, val warning: String? = null) : Result

        data class Failure(val message: String, val cause: Throwable? = null) : Result
    }

    suspend fun scaffold(config: ProjectConfig): Result =
        withContext(Dispatchers.IO) {
            runCatching {
                    val root = File(config.parentDir, config.name)
                    if (root.exists()) {
                        return@withContext Result.Failure("A project named \"${config.name}\" already exists.")
                    }
                    if (!root.mkdirs()) {
                        return@withContext Result.Failure(
                            "Could not create project directory at ${config.parentDir.absolutePath}."
                        )
                    }

                    val warning: String? =
                        when (config.template) {
                            ProjectTemplate.MINECRAFT_MOD -> scaffoldMinecraft(root, config)
                            ProjectTemplate.ANDROID_COMPOSE -> {
                                scaffoldAndroidCompose(root, config)
                                null
                            }
                            ProjectTemplate.PYTHON3 -> {
                                scaffoldPython(root, config, python3 = true)
                                null
                            }
                            ProjectTemplate.PYTHON -> {
                                scaffoldPython(root, config, python3 = false)
                                null
                            }
                            ProjectTemplate.NODEJS -> {
                                scaffoldNode(root, config)
                                null
                            }
                            ProjectTemplate.WEB -> {
                                scaffoldWeb(root, config)
                                null
                            }
                            ProjectTemplate.NONE -> {
                                scaffoldNone(root, config)
                                null
                            }
                            ProjectTemplate.HORIZON_GAMEPLAY_HNMOD -> {
                                scaffoldHorizonGameplayHnmod(root, config)
                                null
                            }
                            ProjectTemplate.HORIZON_HUD_COMMAND_HNMOD -> {
                                scaffoldHorizonHudCommandHnmod(root, config)
                                null
                            }
                            ProjectTemplate.HORIZON_CRATE_ENGINE_HNMOD -> {
                                scaffoldHorizonCrateEngineHnmod(root, config)
                                null
                            }
                        }

                    if (config.initGit) initGitRepository(root, config)

                    Result.Success(root, warning)
                }
                .getOrElse { Result.Failure(it.message ?: "Error creating project", it) }
        }

    // ---- helpers ----------------------------------------------------------------

    private fun File.write(relativePath: String, content: String): File {
        val target = File(this, relativePath)
        target.parentFile?.mkdirs()
        target.writeText(content.trimStart('\n'))
        return target
    }

    private fun escapeJson(s: String): String =
        s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "")

    private fun escapeXml(s: String): String =
        s.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "\\'")

    private fun escapeHtml(s: String): String =
        s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")

    private fun safeJavaClassName(raw: String, fallback: String): String {
        val cleaned = raw.replace(Regex("[^A-Za-z0-9]"), "").ifBlank { fallback }
        val prefixed = if (cleaned.first().isDigit()) "App$cleaned" else cleaned
        return prefixed.replaceFirstChar { it.uppercase() }
    }

    private fun gradleJavaVersionEnum(jdk: String): String =
        if (jdk == "8" || jdk == "1.8") "VERSION_1_8" else "VERSION_$jdk"

    private fun initGitRepository(root: File, config: ProjectConfig) {
        runCatching {
            Git.init().setDirectory(root).call().use { git ->
                git.add().addFilepattern(".").call()
                val author = config.author.ifBlank { "GoyDevv" }
                git.commit()
                    .setAuthor(author, "noreply@arseide.dev")
                    .setCommitter(author, "noreply@arseide.dev")
                    .setMessage("Initial commit")
                    .call()
            }
        }
    }

    private fun horizonRev(): String =
        Settings.horizon_revision.takeIf { HorizonModValidator.isValidRevision(it) }
            ?: HorizonModValidator.DEFAULT_HORIZON_REV

    private fun mixinsRev(): String =
        Settings.mixins_revision.takeIf { HorizonModValidator.isValidRevision(it) }
            ?: HorizonModValidator.DEFAULT_MIXINS_REV

    // ---- 1. Horizon Gameplay (.hnmod) — Veinminer / Block Break / Key / Settings ----

    private suspend fun scaffoldHorizonGameplayHnmod(root: File, config: ProjectConfig) {
        val modId = config.resolvedModId()
        val safeName = escapeJson(config.name)
        val safeDesc = escapeJson(config.modDescription.ifBlank { "Breaks connected ores and logs in one swing." })
        val ver = config.modVersion.ifBlank { "0.1.0" }
        val hRev = horizonRev()
        val mRev = mixinsRev()
        val runtimeMod = "${modId}_runtime"

        root.write(
            "horizon-native.json",
            """
            {
              "schema": 1,
              "id": "$modId",
              "version": "$ver",
              "game": "26.3",
              "horizon": "$hRev",
              "mixins": "$mRev",
              "hooks": 35
            }
            """
                .trimIndent() + "\n",
        )

        root.write(
            "mixins/$modId/horizon-mixin.toml",
            """
            [mod]
            id = "$modId"
            version = "$ver"
            priority = 100

            [compatibility]
            horizon_revision = "$hRev"

            [[module]]
            id = "runtime"
            file = "crates/horizon-client/src/game.rs"
            name = "$runtimeMod"
            source = "runtime.rs"

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
            id = "click"
            kind = "HEAD"
            target = "horizon_client::game::native_hooks::Game<'_>::native_settings_click"
            helper = "click.rs"
            bindings = [
              { name = "game", ty = "&mut crate::game::Game<'_>", value = "&mut *self" },
              { name = "mod_id", ty = "&str", value = "mod_id" },
              { name = "button", ty = "usize", value = "button" },
            ]

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
                .trimIndent() + "\n",
        )

        root.write(
            "mixins/$modId/runtime.rs",
            """
            use std::sync::atomic::{AtomicBool, AtomicUsize, Ordering};

            pub static ENABLED: AtomicBool = AtomicBool::new(true);
            pub static MAX_BLOCKS: AtomicUsize = AtomicUsize::new(32);
            pub static IN_VEIN: AtomicBool = AtomicBool::new(false);

            pub fn is_enabled() -> bool {
                ENABLED.load(Ordering::Relaxed)
            }

            pub fn toggle_enabled() -> bool {
                let next = !is_enabled();
                ENABLED.store(next, Ordering::Relaxed);
                next
            }

            pub fn max_blocks() -> usize {
                MAX_BLOCKS.load(Ordering::Relaxed)
            }

            pub fn cycle_max_blocks() -> usize {
                let next = match max_blocks() {
                    16 => 32,
                    32 => 64,
                    _ => 16,
                };
                MAX_BLOCKS.store(next, Ordering::Relaxed);
                next
            }

            pub fn is_vein_target(block_id: &str) -> bool {
                block_id.ends_with("_ore") || block_id.ends_with("_log") || block_id.ends_with("_stem")
            }
            """
                .trimIndent() + "\n",
        )

        root.write(
            "mixins/$modId/list.rs",
            """
            {
                mods.push(crate::game::native_hooks::NativeMod {
                    id: "$modId".to_string(),
                    name: "$safeName".to_string(),
                    version: "$ver".to_string(),
                    description: "$safeDesc".to_string(),
                });
            }
            """
                .trimIndent() + "\n",
        )

        root.write(
            "mixins/$modId/settings.rs",
            """
            {
                let _ = game;
                if mod_id == "$modId" {
                    let state = if crate::game::$runtimeMod::is_enabled() { "ON" } else { "OFF" };
                    let limit = crate::game::$runtimeMod::max_blocks();
                    buttons.push(format!("$safeName: {state}"));
                    buttons.push(format!("Max Connected Blocks: {limit}"));
                }
            }
            """
                .trimIndent() + "\n",
        )

        root.write(
            "mixins/$modId/click.rs",
            """
            {
                let _ = game;
                if mod_id == "$modId" {
                    match button {
                        0 => { crate::game::$runtimeMod::toggle_enabled(); }
                        1 => { crate::game::$runtimeMod::cycle_max_blocks(); }
                        _ => {}
                    }
                }
            }
            """
                .trimIndent() + "\n",
        )

        root.write(
            "mixins/$modId/key.rs",
            """
            {
                if pressed && key.eq_ignore_ascii_case("key.keyboard.grave.accent") && !game.native_in_menu() {
                    let on = crate::game::$runtimeMod::toggle_enabled();
                    game.native_chat(&format!(
                        "$safeName: {}",
                        if on { "ON" } else { "OFF" }
                    ));
                }
            }
            """
                .trimIndent() + "\n",
        )

        root.write(
            "mixins/$modId/destroy_block.rs",
            """
            {
                if !*canceled
                    && crate::game::$runtimeMod::is_enabled()
                    && !crate::game::$runtimeMod::IN_VEIN.load(std::sync::atomic::Ordering::Relaxed)
                {
                    let block_id = state.split('[').next().unwrap_or(state);
                    if crate::game::$runtimeMod::is_vein_target(block_id) {
                        crate::game::$runtimeMod::IN_VEIN.store(true, std::sync::atomic::Ordering::Relaxed);
                        let limit = crate::game::$runtimeMod::max_blocks();
                        let mut visited = std::collections::BTreeSet::new();
                        let mut queue = std::collections::VecDeque::new();
                        visited.insert(pos);
                        queue.push_back(pos);

                        while let Some([x, y, z]) = queue.pop_front() {
                            if visited.len() >= limit {
                                break;
                            }
                            for dx in -1..=1 {
                                for dy in -1..=1 {
                                    for dz in -1..=1 {
                                        if dx == 0 && dy == 0 && dz == 0 {
                                            continue;
                                        }
                                        let next = [x + dx, y + dy, z + dz];
                                        if visited.contains(&next) {
                                            continue;
                                        }
                                        if let Some(nb) = game.native_block(next) {
                                            let nb_id = nb.split('[').next().unwrap_or(&nb);
                                            if nb_id == block_id {
                                                visited.insert(next);
                                                queue.push_back(next);
                                                let _ = game.native_set_block(
                                                    next,
                                                    "minecraft:air",
                                                    crate::game::native_hooks::flags::UPDATE_ALL,
                                                );
                                                if visited.len() >= limit {
                                                    break;
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        crate::game::$runtimeMod::IN_VEIN.store(false, std::sync::atomic::Ordering::Relaxed);
                    }
                }
            }
            """
                .trimIndent() + "\n",
        )

        HorizonModPorter.copyBundledTools(File(root, "tools"))
        HorizonModPackager.packageProject(root)
    }

    // ---- 2. Horizon HUD & Command (.hnmod) --------------------------------------

    private suspend fun scaffoldHorizonHudCommandHnmod(root: File, config: ProjectConfig) {
        val modId = config.resolvedModId()
        val safeName = escapeJson(config.name)
        val safeDesc = escapeJson(config.modDescription.ifBlank { "Client HUD overlay and slash command mod." })
        val ver = config.modVersion.ifBlank { "0.1.0" }
        val hRev = horizonRev()
        val mRev = mixinsRev()
        val runtimeMod = "${modId}_runtime"

        root.write(
            "horizon-native.json",
            """
            {
              "schema": 1,
              "id": "$modId",
              "version": "$ver",
              "game": "26.3",
              "horizon": "$hRev",
              "mixins": "$mRev",
              "hooks": 35
            }
            """
                .trimIndent() + "\n",
        )

        root.write(
            "mixins/$modId/horizon-mixin.toml",
            """
            [mod]
            id = "$modId"
            version = "$ver"
            priority = 100

            [compatibility]
            horizon_revision = "$hRev"

            [[module]]
            id = "runtime"
            file = "crates/horizon-client/src/game.rs"
            name = "$runtimeMod"
            source = "runtime.rs"

            [[injection]]
            id = "list"
            kind = "HEAD"
            target = "horizon_client::game::native_hooks::Game<'_>::native_mods"
            helper = "list.rs"
            bindings = [
              { name = "mods", ty = "&mut Vec<crate::game::native_hooks::NativeMod>", value = "&mut *mods" },
            ]

            [[injection]]
            id = "hud"
            kind = "HEAD"
            target = "horizon_client::game::native_hooks::Game<'_>::native_hud"
            helper = "hud.rs"
            bindings = [
              { name = "game", ty = "&crate::game::Game<'_>", value = "&*self" },
              { name = "shapes", ty = "&mut Vec<crate::game::native_hooks::NativeShape>", value = "&mut *shapes" },
            ]

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
                .trimIndent() + "\n",
        )

        root.write(
            "mixins/$modId/runtime.rs",
            """
            use std::sync::atomic::{AtomicBool, Ordering};

            pub static SHOW_HUD: AtomicBool = AtomicBool::new(true);

            pub fn is_shown() -> bool {
                SHOW_HUD.load(Ordering::Relaxed)
            }

            pub fn toggle() -> bool {
                let next = !is_shown();
                SHOW_HUD.store(next, Ordering::Relaxed);
                next
            }
            """
                .trimIndent() + "\n",
        )

        root.write(
            "mixins/$modId/list.rs",
            """
            {
                mods.push(crate::game::native_hooks::NativeMod {
                    id: "$modId".to_string(),
                    name: "$safeName".to_string(),
                    version: "$ver".to_string(),
                    description: "$safeDesc".to_string(),
                });
            }
            """
                .trimIndent() + "\n",
        )

        root.write(
            "mixins/$modId/hud.rs",
            """
            {
                if crate::game::$runtimeMod::is_shown() && !game.native_in_menu() {
                    if let Some(p) = game.native_player() {
                        shapes.push(crate::game::native_hooks::NativeShape::Rect {
                            x: 6.0,
                            y: 6.0,
                            w: 152.0,
                            h: 22.0,
                            color: 0x9010141F,
                        });
                        shapes.push(crate::game::native_hooks::NativeShape::Text {
                            text: format!("XYZ: {:.1}, {:.1}, {:.1}", p.x, p.y, p.z),
                            x: 12.0,
                            y: 13.0,
                            color: 0xFFE2E8F0,
                            scale: 1.0,
                            centered: false,
                            shadow: true,
                        });
                    }
                }
            }
            """
                .trimIndent() + "\n",
        )

        root.write(
            "mixins/$modId/commands.rs",
            """
            {
                commands.push("$modId".to_string());
            }
            """
                .trimIndent() + "\n",
        )

        root.write(
            "mixins/$modId/command.rs",
            """
            {
                let _ = args;
                if name == "$modId" {
                    let on = crate::game::$runtimeMod::toggle();
                    game.native_chat(&format!("$safeName HUD: {}", if on { "ON" } else { "OFF" }));
                    *ran = true;
                }
            }
            """
                .trimIndent() + "\n",
        )

        HorizonModPorter.copyBundledTools(File(root, "tools"))
        HorizonModPackager.packageProject(root)
    }

    // ---- 3. Horizon Engine Crate (.hnmod) ---------------------------------------

    private suspend fun scaffoldHorizonCrateEngineHnmod(root: File, config: ProjectConfig) {
        val modId = config.resolvedModId()
        val safeName = escapeJson(config.name)
        val ver = config.modVersion.ifBlank { "0.1.0" }
        val hRev = horizonRev()
        val mRev = mixinsRev()
        val crateName = "${modId.replace('_', '-')}-engine"
        val crateIdent = "${modId}_engine"
        val runtimeMod = "${modId}_runtime"

        root.write(
            "horizon-native.json",
            """
            {
              "schema": 1,
              "id": "$modId",
              "version": "$ver",
              "game": "26.3",
              "horizon": "$hRev",
              "mixins": "$mRev",
              "hooks": 41
            }
            """
                .trimIndent() + "\n",
        )

        root.write(
            "mixins/$modId/horizon-mixin.toml",
            """
            [mod]
            id = "$modId"
            version = "$ver"
            priority = 100

            [compatibility]
            horizon_revision = "$hRev"

            [[crate]]
            id = "engine"
            path = "engine"

            [[module]]
            id = "runtime"
            file = "crates/horizon-client/src/game.rs"
            name = "$runtimeMod"
            source = "runtime.rs"

            [[injection]]
            id = "list"
            kind = "HEAD"
            target = "horizon_client::game::native_hooks::Game<'_>::native_mods"
            helper = "list.rs"
            bindings = [
              { name = "mods", ty = "&mut Vec<crate::game::native_hooks::NativeMod>", value = "&mut *mods" },
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
                .trimIndent() + "\n",
        )

        root.write(
            "mixins/$modId/runtime.rs",
            """
            pub fn summary() -> &'static str {
                $crateIdent::mod_banner()
            }
            """
                .trimIndent() + "\n",
        )

        root.write(
            "mixins/$modId/list.rs",
            """
            {
                mods.push(crate::game::native_hooks::NativeMod {
                    id: "$modId".to_string(),
                    name: "$safeName".to_string(),
                    version: "$ver".to_string(),
                    description: crate::game::$runtimeMod::summary().to_string(),
                });
            }
            """
                .trimIndent() + "\n",
        )

        root.write(
            "mixins/$modId/tick.rs",
            """
            {
                let _ = game;
                $crateIdent::on_client_tick();
            }
            """
                .trimIndent() + "\n",
        )

        root.write(
            "mixins/$modId/data/ported_config.json",
            """
            {
              "enabled": true,
              "multiplier": 2.0
            }
            """
                .trimIndent() + "\n",
        )

        root.write(
            "mixins/$modId/engine/Cargo.toml",
            """
            [package]
            name = "$crateName"
            version = "$ver"
            edition = "2024"

            [dependencies]
            """
                .trimIndent() + "\n",
        )

        root.write(
            "mixins/$modId/engine/README.md",
            "# $safeName Engine Crate\n\nCompanion Rust library crate for `$modId`.\n",
        )

        root.write(
            "mixins/$modId/engine/src/lib.rs",
            """
            use std::sync::atomic::{AtomicU64, Ordering};

            static TICKS: AtomicU64 = AtomicU64::new(0);

            pub fn mod_banner() -> &'static str {
                "$safeName companion Cargo crate"
            }

            pub fn on_client_tick() -> u64 {
                TICKS.fetch_add(1, Ordering::Relaxed) + 1
            }
            """
                .trimIndent() + "\n",
        )

        HorizonModPorter.copyBundledTools(File(root, "tools"))
        HorizonModPackager.packageProject(root)
    }

    // ---- None -------------------------------------------------------------------

    private fun scaffoldNone(root: File, config: ProjectConfig) {
        root.write("README.md", "# ${config.name}\n")
        root.write(".gitignore", ".DS_Store\n*.log\n/build/\n/out/\n")
    }

    // ---- Python -----------------------------------------------------------------

    private fun scaffoldPython(root: File, config: ProjectConfig, python3: Boolean) {
        val shebang = if (python3) "#!/usr/bin/env python3" else "#!/usr/bin/env python"
        val safeName = escapeJson(config.name)
        val mainFile =
            root.write(
                "main.py",
                """
                $shebang

                def main() -> None:
                    print("Hello from $safeName!")


                if __name__ == "__main__":
                    main()
                """
                    .trimIndent() + "\n",
            )
        runCatching { mainFile.setExecutable(true, false) }
        root.write("requirements.txt", "# pip dependencies\n")
        root.write(".gitignore", "__pycache__/\n*.py[cod]\n.venv/\nvenv/\n.env\n")
        root.write("README.md", "# ${config.name}\n\n```bash\n${if (python3) "python3" else "python"} main.py\n```\n")
    }

    // ---- Node.js ----------------------------------------------------------------

    private fun scaffoldNode(root: File, config: ProjectConfig) {
        val pkg = config.name.lowercase().replace(Regex("[^a-z0-9-_]"), "-").trim('-').ifBlank { "app" }
        val safeDesc = escapeJson(config.name)
        val safeAuthor = escapeJson(config.author)
        root.write(
            "package.json",
            """
            {
              "name": "$pkg",
              "version": "1.0.0",
              "description": "$safeDesc",
              "main": "index.js",
              "type": "commonjs",
              "scripts": {
                "start": "node index.js"
              },
              "author": "$safeAuthor",
              "license": "MIT"
            }
            """
                .trimIndent() + "\n",
        )
        root.write(
            "index.js",
            """
            "use strict";

            console.log("Hello from $safeDesc!");
            """
                .trimIndent() + "\n",
        )
        root.write(".gitignore", "node_modules/\nnpm-debug.log*\n.env\ndist/\n")
        root.write("README.md", "# ${config.name}\n\n```bash\nnpm install\nnpm start\n```\n")
    }

    // ---- Static Web -------------------------------------------------------------

    private fun scaffoldWeb(root: File, config: ProjectConfig) {
        val safeTitle = escapeHtml(config.name)
        val safeJsTitle = escapeJson(config.name)
        root.write(
            "index.html",
            """
            <!DOCTYPE html>
            <html lang="en">
            <head>
                <meta charset="UTF-8" />
                <meta name="viewport" content="width=device-width, initial-scale=1.0" />
                <title>$safeTitle</title>
                <link rel="stylesheet" href="style.css" />
            </head>
            <body>
                <main class="card">
                    <h1>$safeTitle</h1>
                    <p>Edit <code>index.html</code>, <code>style.css</code>, or <code>script.js</code> and tap Run.</p>
                    <button id="counter-btn" type="button">Clicks: <span id="count">0</span></button>
                </main>
                <script src="script.js"></script>
            </body>
            </html>
            """
                .trimIndent() + "\n",
        )
        root.write(
            "style.css",
            """
            :root {
                color-scheme: light dark;
                --bg: #0f141c;
                --surface: #1b2230;
                --primary: #8ab4f8;
                --on-primary: #062e6f;
                --text: #e2e8f0;
                --muted: #94a3b8;
            }
            * { box-sizing: border-box; }
            body {
                font-family: system-ui, -apple-system, sans-serif;
                margin: 0;
                min-height: 100vh;
                display: grid;
                place-items: center;
                padding: 1.5rem;
                background: var(--bg);
                color: var(--text);
            }
            .card {
                max-width: 440px;
                width: 100%;
                text-align: center;
                padding: 2rem 1.5rem;
                border-radius: 20px;
                background: var(--surface);
            }
            p { color: var(--muted); line-height: 1.5; margin-bottom: 1.25rem; }
            button {
                border: none;
                background: var(--primary);
                color: var(--on-primary);
                font-weight: 600;
                padding: 0.7rem 1.4rem;
                border-radius: 999px;
                cursor: pointer;
            }
            """
                .trimIndent() + "\n",
        )
        root.write(
            "script.js",
            """
            document.addEventListener("DOMContentLoaded", () => {
              let count = 0;
              const btn = document.getElementById("counter-btn");
              const countEl = document.getElementById("count");
              if (btn && countEl) {
                btn.addEventListener("click", () => {
                  count += 1;
                  countEl.textContent = String(count);
                });
              }
              console.log("$safeJsTitle loaded");
            });
            """
                .trimIndent() + "\n",
        )
        root.write(".gitignore", "node_modules/\ndist/\n.env\n")
        root.write("README.md", "# ${config.name}\n")
    }

    // ---- Minecraft Java Mod (Fabric / Forge) ------------------------------------

    private fun scaffoldMinecraft(root: File, config: ProjectConfig): String? =
        when (config.modLoader) {
            ModLoader.FORGE -> scaffoldForge(root, config)
            else -> scaffoldFabric(root, config)
        }

    private fun scaffoldFabric(root: File, config: ProjectConfig): String? {
        val modId = config.resolvedModId()
        val pkg = config.resolvedPackageName()
        val pkgPath = pkg.replace('.', '/')
        val mainClass = safeJavaClassName(config.name, "ExampleMod")
        val mc = config.minecraftVersion.ifBlank { "1.21.4" }
        val jdk = config.jdkVersion.ifBlank { "21" }
        val javaVerEnum = gradleJavaVersionEnum(jdk)
        val pins = ModVersions.fabricPins(mc)
        val safeName = escapeJson(config.name)
        val safeDesc = escapeJson(config.modDescription.ifBlank { config.name })
        val safeAuthor = escapeJson(config.author.ifBlank { "GoyDevv" })

        val mappingsLine =
            if (pins.hasYarn) {
                "mappings \"net.fabricmc:yarn:\${project.yarn_mappings}:v2\""
            } else {
                "mappings loom.officialMojangMappings()"
            }

        root.write(
            "gradle.properties",
            """
            org.gradle.jvmargs=-Xmx1536m -Dfile.encoding=UTF-8
            org.gradle.parallel=true
            org.gradle.caching=true
            org.gradle.workers.max=2

            minecraft_version=$mc
            yarn_mappings=${pins.yarn}
            loader_version=${pins.loader}

            mod_version=${config.modVersion}
            maven_group=$pkg
            archives_base_name=$modId

            fabric_version=${pins.fabricApi}
            """
                .trimIndent() + "\n",
        )

        root.write(
            "settings.gradle",
            """
            pluginManagement {
                repositories {
                    maven { name = 'Fabric'; url = 'https://maven.fabricmc.net/' }
                    mavenCentral()
                    gradlePluginPortal()
                }
            }

            rootProject.name = '$modId'
            """
                .trimIndent() + "\n",
        )

        root.write(
            "build.gradle",
            """
            plugins {
                id 'fabric-loom' version '1.9-SNAPSHOT'
                id 'maven-publish'
            }

            version = project.mod_version
            group = project.maven_group

            base { archivesName = project.archives_base_name }

            repositories {
                mavenCentral()
            }

            dependencies {
                minecraft "com.mojang:minecraft:${'$'}{project.minecraft_version}"
                $mappingsLine
                modImplementation "net.fabricmc:fabric-loader:${'$'}{project.loader_version}"
                modImplementation "net.fabricmc.fabric-api:fabric-api:${'$'}{project.fabric_version}"
            }

            processResources {
                inputs.property "version", project.version
                filteringCharset "UTF-8"
                filesMatching("fabric.mod.json") {
                    expand "version": project.version
                }
            }

            java {
                sourceCompatibility = JavaVersion.$javaVerEnum
                targetCompatibility = JavaVersion.$javaVerEnum
                withSourcesJar()
            }

            tasks.withType(JavaCompile).configureEach {
                it.options.encoding = "UTF-8"
                it.options.release = $jdk
            }
            """
                .trimIndent() + "\n",
        )

        root.write(
            "src/main/resources/fabric.mod.json",
            """
            {
              "schemaVersion": 1,
              "id": "$modId",
              "version": "${'$'}{version}",
              "name": "$safeName",
              "description": "$safeDesc",
              "authors": ["$safeAuthor"],
              "license": "MIT",
              "environment": "*",
              "entrypoints": {
                "main": ["$pkg.$mainClass"]
              },
              "mixins": ["$modId.mixins.json"],
              "depends": {
                "fabricloader": ">=${pins.loader}",
                "minecraft": "~$mc",
                "java": ">=$jdk",
                "fabric-api": "*"
              }
            }
            """
                .trimIndent() + "\n",
        )

        root.write(
            "src/main/resources/$modId.mixins.json",
            """
            {
              "required": true,
              "package": "$pkg.mixin",
              "compatibilityLevel": "JAVA_$jdk",
              "mixins": [],
              "client": [],
              "injectors": { "defaultRequire": 1 }
            }
            """
                .trimIndent() + "\n",
        )

        root.write(
            "src/main/java/$pkgPath/$mainClass.java",
            """
            package $pkg;

            import net.fabricmc.api.ModInitializer;
            import org.slf4j.Logger;
            import org.slf4j.LoggerFactory;

            public class $mainClass implements ModInitializer {
                public static final String MOD_ID = "$modId";
                public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

                @Override
                public void onInitialize() {
                    LOGGER.info("$safeName initialized");
                }
            }
            """
                .trimIndent() + "\n",
        )

        writeGradleWrapper(root, "8.10.2")
        root.write(".gitignore", ".gradle/\nbuild/\nout/\n.idea/\n*.iml\n")
        root.write("README.md", "# ${config.name}\n\nFabric mod for Minecraft $mc (Java $jdk).\n")

        return if (!pins.online) {
            "Used offline Fabric defaults for Minecraft $mc."
        } else {
            null
        }
    }

    private fun scaffoldForge(root: File, config: ProjectConfig): String? {
        val modId = config.resolvedModId()
        val pkg = config.resolvedPackageName()
        val pkgPath = pkg.replace('.', '/')
        val mainClass = safeJavaClassName(config.name, "ExampleMod")
        val requestedMc = config.minecraftVersion.ifBlank { "1.20.1" }
        val forgePin = ModVersions.resolveForgePin(requestedMc)
        val mc = forgePin.mcVersion
        val forge = forgePin.forgeVersion
        val forgeMajor = forge.substringBefore(".").ifBlank { "47" }
        val forgeMajorInt = forgeMajor.toIntOrNull() ?: 47
        val jdk =
            if (!forgePin.exactMatch) "17"
            else config.jdkVersion.ifBlank { if (forgeMajorInt >= 50) "21" else "17" }
        val packFormat = packFormatForMc(mc)
        val safeName = escapeJson(config.name)
        val safeDesc = escapeJson(config.modDescription.ifBlank { config.name })
        val safeAuthor = escapeJson(config.author.ifBlank { "GoyDevv" })

        val modConstructor =
            if (forgeMajorInt >= 50) {
                """
                public $mainClass(FMLJavaModLoadingContext context) {
                    IEventBus modEventBus = context.getModEventBus();
                    LOGGER.info("$safeName loading");
                }
                """
                    .trimIndent()
            } else {
                """
                public $mainClass() {
                    IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();
                    LOGGER.info("$safeName loading");
                }
                """
                    .trimIndent()
            }

        root.write(
            "gradle.properties",
            """
            org.gradle.jvmargs=-Xmx1536m -Dfile.encoding=UTF-8
            org.gradle.daemon=false
            org.gradle.caching=true
            org.gradle.workers.max=2

            minecraft_version=$mc
            forge_version=$forge
            mod_id=$modId
            mod_version=${config.modVersion}
            maven_group=$pkg
            """
                .trimIndent() + "\n",
        )

        root.write(
            "settings.gradle",
            """
            pluginManagement {
                repositories {
                    maven { url = 'https://maven.minecraftforge.net/' }
                    gradlePluginPortal()
                    mavenCentral()
                }
            }

            rootProject.name = '$modId'
            """
                .trimIndent() + "\n",
        )

        root.write(
            "build.gradle",
            """
            plugins {
                id 'net.minecraftforge.gradle' version '[6.0.24,6.2)'
                id 'java'
            }

            version = project.mod_version
            group = project.maven_group

            base {
                archivesName = project.mod_id
            }

            java {
                toolchain.languageVersion = JavaLanguageVersion.of($jdk)
            }

            minecraft {
                mappings channel: 'official', version: project.minecraft_version
                copyIdeResources = true
            }

            repositories {
                mavenCentral()
                maven { url = 'https://maven.minecraftforge.net/' }
            }

            dependencies {
                minecraft "net.minecraftforge:forge:${'$'}{project.minecraft_version}-${'$'}{project.forge_version}"
            }

            tasks.withType(JavaCompile).configureEach {
                options.encoding = 'UTF-8'
            }

            jar {
                manifest {
                    attributes([
                        "Specification-Title"   : project.mod_id,
                        "Implementation-Title"  : project.mod_id,
                        "Implementation-Version": project.version
                    ])
                }
            }
            """
                .trimIndent() + "\n",
        )

        root.write(
            "src/main/resources/META-INF/mods.toml",
            """
            modLoader="javafml"
            loaderVersion="[$forgeMajor,)"
            license="MIT"

            [[mods]]
            modId="$modId"
            version="${config.modVersion}"
            displayName="$safeName"
            authors="$safeAuthor"
            description='''
            $safeDesc
            '''

            [[dependencies.$modId]]
                modId="forge"
                mandatory=true
                versionRange="[$forgeMajor,)"
                ordering="NONE"
                side="BOTH"

            [[dependencies.$modId]]
                modId="minecraft"
                mandatory=true
                versionRange="[$mc]"
                ordering="NONE"
                side="BOTH"
            """
                .trimIndent() + "\n",
        )

        root.write(
            "src/main/resources/pack.mcmeta",
            """
            {
              "pack": {
                "description": "$safeName resources",
                "pack_format": $packFormat
              }
            }
            """
                .trimIndent() + "\n",
        )

        root.write(
            "src/main/java/$pkgPath/$mainClass.java",
            """
            package $pkg;

            import net.minecraftforge.fml.common.Mod;
            import net.minecraftforge.eventbus.api.IEventBus;
            import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
            import org.slf4j.Logger;
            import com.mojang.logging.LogUtils;

            @Mod($mainClass.MOD_ID)
            public class $mainClass {
                public static final String MOD_ID = "$modId";
                private static final Logger LOGGER = LogUtils.getLogger();

                $modConstructor
            }
            """
                .trimIndent() + "\n",
        )

        writeGradleWrapper(root, "8.8")
        root.write(".gitignore", ".gradle/\nbuild/\nout/\n.idea/\n*.iml\n")
        root.write("README.md", "# ${config.name}\n\nForge mod for Minecraft $mc (Java $jdk).\n")

        return if (!forgePin.exactMatch) {
            "Configured for Minecraft $mc (Forge $forge)."
        } else {
            null
        }
    }

    private fun packFormatForMc(mc: String): Int =
        when {
            mc.startsWith("1.21.4") -> 46
            mc.startsWith("1.21.2") || mc.startsWith("1.21.3") -> 42
            mc.startsWith("1.21") -> 34
            mc.startsWith("1.20.5") || mc.startsWith("1.20.6") -> 32
            mc.startsWith("1.20.3") || mc.startsWith("1.20.4") -> 22
            mc.startsWith("1.20.2") -> 18
            mc.startsWith("1.20") -> 15
            mc.startsWith("1.19") -> 9
            else -> 15
        }

    // ---- Android Jetpack Compose (Clean Material 3 project) ---------------------

    private fun scaffoldAndroidCompose(root: File, config: ProjectConfig) {
        val pkg = config.resolvedPackageName()
        val pkgPath = pkg.replace('.', '/')
        val jdk = config.jdkVersion.takeIf { it in listOf("17", "21") } ?: "21"
        val sdk = config.resolvedCompileSdk()
        val minSdk = config.resolvedMinSdk()
        val appNoSpace = safeJavaClassName(config.name, "App")
        val styleName = "Theme.$appNoSpace"
        val safeRootName = escapeJson(config.name)
        val safeXmlAppName = escapeXml(config.name)

        root.write(
            "settings.gradle.kts",
            """
            pluginManagement {
                repositories {
                    google {
                        content {
                            includeGroupByRegex("com\\.android.*")
                            includeGroupByRegex("com\\.google.*")
                            includeGroupByRegex("androidx.*")
                        }
                    }
                    mavenCentral()
                    gradlePluginPortal()
                }
            }
            dependencyResolutionManagement {
                repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
                repositories {
                    google()
                    mavenCentral()
                }
            }

            rootProject.name = "$safeRootName"
            include(":app")
            """
                .trimIndent() + "\n",
        )

        root.write(
            "build.gradle.kts",
            """
            plugins {
                alias(libs.plugins.android.application) apply false
                alias(libs.plugins.kotlin.compose) apply false
            }
            """
                .trimIndent() + "\n",
        )

        root.write(
            "gradle/libs.versions.toml",
            """
            [versions]
            agp = "9.2.1"
            kotlin = "2.3.20"
            coreKtx = "1.17.0"
            lifecycleRuntimeKtx = "2.10.0"
            activityCompose = "1.12.0"
            composeBom = "2025.11.01"

            [libraries]
            androidx-core-ktx = { group = "androidx.core", name = "core-ktx", version.ref = "coreKtx" }
            androidx-lifecycle-runtime-ktx = { group = "androidx.lifecycle", name = "lifecycle-runtime-ktx", version.ref = "lifecycleRuntimeKtx" }
            androidx-activity-compose = { group = "androidx.activity", name = "activity-compose", version.ref = "activityCompose" }
            androidx-compose-bom = { group = "androidx.compose", name = "compose-bom", version.ref = "composeBom" }
            androidx-ui = { group = "androidx.compose.ui", name = "ui" }
            androidx-ui-graphics = { group = "androidx.compose.ui", name = "ui-graphics" }
            androidx-ui-tooling = { group = "androidx.compose.ui", name = "ui-tooling" }
            androidx-ui-tooling-preview = { group = "androidx.compose.ui", name = "ui-tooling-preview" }
            androidx-material3 = { group = "androidx.compose.material3", name = "material3" }

            [plugins]
            android-application = { id = "com.android.application", version.ref = "agp" }
            kotlin-compose = { id = "org.jetbrains.kotlin.plugin.compose", version.ref = "kotlin" }
            """
                .trimIndent() + "\n",
        )

        root.write(
            "gradle.properties",
            """
            org.gradle.jvmargs=-Xmx1536m -Dfile.encoding=UTF-8
            org.gradle.parallel=true
            org.gradle.caching=true
            org.gradle.workers.max=2
            kotlin.compiler.execution.strategy=in-process
            android.useAndroidX=true
            kotlin.code.style=official
            android.nonTransitiveRClass=true
            """
                .trimIndent() + "\n",
        )

        root.write(
            "app/build.gradle.kts",
            """
            plugins {
                alias(libs.plugins.android.application)
                alias(libs.plugins.kotlin.compose)
            }

            android {
                namespace = "$pkg"
                compileSdk = $sdk

                defaultConfig {
                    applicationId = "$pkg"
                    minSdk = $minSdk
                    targetSdk = $sdk
                    versionCode = 1
                    versionName = "1.0.0"

                    vectorDrawables { useSupportLibrary = true }
                }

                buildTypes {
                    release {
                        isMinifyEnabled = true
                        isShrinkResources = true
                        proguardFiles(
                            getDefaultProguardFile("proguard-android-optimize.txt"),
                            "proguard-rules.pro",
                        )
                    }
                }
                compileOptions {
                    sourceCompatibility = JavaVersion.VERSION_$jdk
                    targetCompatibility = JavaVersion.VERSION_$jdk
                }
                buildFeatures { compose = true }
            }

            kotlin {
                jvmToolchain($jdk)
            }

            dependencies {
                implementation(libs.androidx.core.ktx)
                implementation(libs.androidx.lifecycle.runtime.ktx)
                implementation(libs.androidx.activity.compose)
                implementation(platform(libs.androidx.compose.bom))
                implementation(libs.androidx.ui)
                implementation(libs.androidx.ui.graphics)
                implementation(libs.androidx.ui.tooling.preview)
                implementation(libs.androidx.material3)
                debugImplementation(libs.androidx.ui.tooling)
            }
            """
                .trimIndent() + "\n",
        )

        root.write("app/proguard-rules.pro", "# ProGuard rules\n")
        root.write("app/.gitignore", "/build\n")

        root.write(
            "app/src/main/AndroidManifest.xml",
            """
            <?xml version="1.0" encoding="utf-8"?>
            <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                xmlns:tools="http://schemas.android.com/tools">

                <application
                    android:allowBackup="true"
                    android:dataExtractionRules="@xml/data_extraction_rules"
                    android:fullBackupContent="@xml/backup_rules"
                    android:icon="@mipmap/ic_launcher"
                    android:label="@string/app_name"
                    android:roundIcon="@mipmap/ic_launcher_round"
                    android:supportsRtl="true"
                    android:theme="@style/$styleName"
                    tools:targetApi="31">
                    <activity
                        android:name=".MainActivity"
                        android:exported="true"
                        android:theme="@style/$styleName">
                        <intent-filter>
                            <action android:name="android.intent.action.MAIN" />
                            <category android:name="android.intent.category.LAUNCHER" />
                        </intent-filter>
                    </activity>
                </application>

            </manifest>
            """
                .trimIndent() + "\n",
        )

        root.write(
            "app/src/main/java/$pkgPath/ui/theme/Color.kt",
            """
            package $pkg.ui.theme

            import androidx.compose.ui.graphics.Color

            val PrimaryDark = Color(0xFFADC6FF)
            val SecondaryDark = Color(0xFFBCC6E2)
            val TertiaryDark = Color(0xFF66E3F4)

            val PrimaryLight = Color(0xFF2856A8)
            val SecondaryLight = Color(0xFF515E79)
            val TertiaryLight = Color(0xFF006874)
            """
                .trimIndent() + "\n",
        )

        root.write(
            "app/src/main/java/$pkgPath/ui/theme/Type.kt",
            """
            package $pkg.ui.theme

            import androidx.compose.material3.Typography
            import androidx.compose.ui.text.TextStyle
            import androidx.compose.ui.text.font.FontFamily
            import androidx.compose.ui.text.font.FontWeight
            import androidx.compose.ui.unit.sp

            val Typography = Typography(
                bodyLarge = TextStyle(
                    fontFamily = FontFamily.Default,
                    fontWeight = FontWeight.Normal,
                    fontSize = 16.sp,
                    lineHeight = 24.sp,
                    letterSpacing = 0.5.sp,
                ),
            )
            """
                .trimIndent() + "\n",
        )

        root.write(
            "app/src/main/java/$pkgPath/ui/theme/Theme.kt",
            """
            package $pkg.ui.theme

            import android.os.Build
            import androidx.compose.foundation.isSystemInDarkTheme
            import androidx.compose.material3.MaterialTheme
            import androidx.compose.material3.darkColorScheme
            import androidx.compose.material3.dynamicDarkColorScheme
            import androidx.compose.material3.dynamicLightColorScheme
            import androidx.compose.material3.lightColorScheme
            import androidx.compose.runtime.Composable
            import androidx.compose.ui.platform.LocalContext

            private val DarkColorScheme = darkColorScheme(
                primary = PrimaryDark,
                secondary = SecondaryDark,
                tertiary = TertiaryDark,
            )

            private val LightColorScheme = lightColorScheme(
                primary = PrimaryLight,
                secondary = SecondaryLight,
                tertiary = TertiaryLight,
            )

            @Composable
            fun ${appNoSpace}Theme(
                darkTheme: Boolean = isSystemInDarkTheme(),
                dynamicColor: Boolean = true,
                content: @Composable () -> Unit,
            ) {
                val colorScheme = when {
                    dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
                        val context = LocalContext.current
                        if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
                    }
                    darkTheme -> DarkColorScheme
                    else -> LightColorScheme
                }

                MaterialTheme(
                    colorScheme = colorScheme,
                    typography = Typography,
                    content = content,
                )
            }
            """
                .trimIndent() + "\n",
        )

        root.write(
            "app/src/main/java/$pkgPath/MainActivity.kt",
            """
            package $pkg

            import android.os.Bundle
            import androidx.activity.ComponentActivity
            import androidx.activity.compose.setContent
            import androidx.activity.enableEdgeToEdge
            import androidx.compose.foundation.layout.Arrangement
            import androidx.compose.foundation.layout.Column
            import androidx.compose.foundation.layout.Spacer
            import androidx.compose.foundation.layout.fillMaxSize
            import androidx.compose.foundation.layout.fillMaxWidth
            import androidx.compose.foundation.layout.height
            import androidx.compose.foundation.layout.padding
            import androidx.compose.material3.ElevatedCard
            import androidx.compose.material3.ExperimentalMaterial3Api
            import androidx.compose.material3.ExtendedFloatingActionButton
            import androidx.compose.material3.FilledTonalButton
            import androidx.compose.material3.LargeTopAppBar
            import androidx.compose.material3.MaterialTheme
            import androidx.compose.material3.Scaffold
            import androidx.compose.material3.Text
            import androidx.compose.runtime.Composable
            import androidx.compose.runtime.getValue
            import androidx.compose.runtime.mutableIntStateOf
            import androidx.compose.runtime.remember
            import androidx.compose.runtime.setValue
            import androidx.compose.ui.Modifier
            import androidx.compose.ui.text.font.FontWeight
            import androidx.compose.ui.tooling.preview.Preview
            import androidx.compose.ui.unit.dp
            import $pkg.ui.theme.${appNoSpace}Theme

            class MainActivity : ComponentActivity() {
                override fun onCreate(savedInstanceState: Bundle?) {
                    super.onCreate(savedInstanceState)
                    enableEdgeToEdge()
                    setContent {
                        ${appNoSpace}Theme {
                            MainScreen(appTitle = "$safeRootName")
                        }
                    }
                }
            }

            @OptIn(ExperimentalMaterial3Api::class)
            @Composable
            fun MainScreen(appTitle: String) {
                var count by remember { mutableIntStateOf(0) }
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    topBar = {
                        LargeTopAppBar(
                            title = {
                                Text(
                                    text = appTitle,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                        )
                    },
                    floatingActionButton = {
                        ExtendedFloatingActionButton(
                            onClick = { count++ },
                        ) {
                            Text("Count: ${'$'}count")
                        }
                    },
                ) { innerPadding ->
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                            .padding(horizontal = 20.dp, vertical = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(20.dp)) {
                                Text(
                                    text = appTitle,
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "Edit MainActivity.kt and tap Run to build.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Spacer(modifier = Modifier.height(14.dp))
                                FilledTonalButton(onClick = { count = 0 }) {
                                    Text("Reset")
                                }
                            }
                        }
                    }
                }
            }

            @Preview(showBackground = true)
            @Composable
            fun MainScreenPreview() {
                ${appNoSpace}Theme { MainScreen("$safeRootName") }
            }
            """
                .trimIndent() + "\n",
        )

        root.write(
            "app/src/main/res/values/strings.xml",
            """
            <?xml version="1.0" encoding="utf-8"?>
            <resources>
                <string name="app_name">$safeXmlAppName</string>
            </resources>
            """
                .trimIndent() + "\n",
        )
        root.write(
            "app/src/main/res/values/colors.xml",
            """
            <?xml version="1.0" encoding="utf-8"?>
            <resources>
                <color name="black">#FF000000</color>
                <color name="white">#FFFFFFFF</color>
            </resources>
            """
                .trimIndent() + "\n",
        )
        root.write(
            "app/src/main/res/values/themes.xml",
            """
            <resources xmlns:tools="http://schemas.android.com/tools">
                <style name="$styleName" parent="android:Theme.Material.Light.NoActionBar" />
            </resources>
            """
                .trimIndent() + "\n",
        )
        root.write(
            "app/src/main/res/values-night/themes.xml",
            """
            <resources xmlns:tools="http://schemas.android.com/tools">
                <style name="$styleName" parent="android:Theme.Material.NoActionBar" />
            </resources>
            """
                .trimIndent() + "\n",
        )

        root.write(
            "app/src/main/res/xml/backup_rules.xml",
            """
            <?xml version="1.0" encoding="utf-8"?>
            <full-backup-content>
            </full-backup-content>
            """
                .trimIndent() + "\n",
        )
        root.write(
            "app/src/main/res/xml/data_extraction_rules.xml",
            """
            <?xml version="1.0" encoding="utf-8"?>
            <data-extraction-rules>
                <cloud-backup>
                </cloud-backup>
            </data-extraction-rules>
            """
                .trimIndent() + "\n",
        )

        root.write(
            "app/src/main/res/drawable/ic_launcher_background.xml",
            """
            <vector xmlns:android="http://schemas.android.com/apk/res/android"
                android:width="108dp"
                android:height="108dp"
                android:viewportWidth="108"
                android:viewportHeight="108">
                <path android:fillColor="#0E1726" android:pathData="M0,0h108v108h-108z" />
            </vector>
            """
                .trimIndent() + "\n",
        )
        root.write(
            "app/src/main/res/drawable/ic_launcher_foreground.xml",
            """
            <vector xmlns:android="http://schemas.android.com/apk/res/android"
                android:width="108dp"
                android:height="108dp"
                android:viewportWidth="108"
                android:viewportHeight="108">
                <path android:fillColor="#66E3F4" android:pathData="M54,28 L78,76 L66,76 L61,64 L47,64 L42,76 L30,76 Z M50,55 L58,55 L54,44 Z" />
            </vector>
            """
                .trimIndent() + "\n",
        )
        root.write(
            "app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml",
            """
            <?xml version="1.0" encoding="utf-8"?>
            <adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
                <background android:drawable="@drawable/ic_launcher_background" />
                <foreground android:drawable="@drawable/ic_launcher_foreground" />
                <monochrome android:drawable="@drawable/ic_launcher_foreground" />
            </adaptive-icon>
            """
                .trimIndent() + "\n",
        )
        root.write(
            "app/src/main/res/mipmap-anydpi-v26/ic_launcher_round.xml",
            """
            <?xml version="1.0" encoding="utf-8"?>
            <adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
                <background android:drawable="@drawable/ic_launcher_background" />
                <foreground android:drawable="@drawable/ic_launcher_foreground" />
                <monochrome android:drawable="@drawable/ic_launcher_foreground" />
            </adaptive-icon>
            """
                .trimIndent() + "\n",
        )
        val legacyIcon =
            """
            <?xml version="1.0" encoding="utf-8"?>
            <layer-list xmlns:android="http://schemas.android.com/apk/res/android">
                <item android:drawable="@drawable/ic_launcher_background" />
                <item android:drawable="@drawable/ic_launcher_foreground" />
            </layer-list>
            """
                .trimIndent() + "\n"
        root.write("app/src/main/res/mipmap/ic_launcher.xml", legacyIcon)
        root.write("app/src/main/res/mipmap/ic_launcher_round.xml", legacyIcon)

        root.write(
            ".gitignore",
            """
            *.iml
            .gradle
            /local.properties
            /.idea
            .DS_Store
            /build
            /captures
            .externalNativeBuild
            .cxx
            local.properties
            """
                .trimIndent() + "\n",
        )

        writeGradleWrapper(root, "9.5.1")
        root.write("README.md", "# ${config.name}\n\n```bash\n./gradlew assembleDebug\n```\n")
    }

    private fun writeGradleWrapper(root: File, gradleVersion: String) {
        root.write(
            "gradle/wrapper/gradle-wrapper.properties",
            """
            distributionBase=GRADLE_USER_HOME
            distributionPath=wrapper/dists
            distributionUrl=https\://services.gradle.org/distributions/gradle-$gradleVersion-bin.zip
            networkTimeout=10000
            validateDistributionUrl=true
            zipStoreBase=GRADLE_USER_HOME
            zipStorePath=wrapper/dists
            """
                .trimIndent() + "\n",
        )

        copyAsset("templates/gradle-wrapper.jar", File(root, "gradle/wrapper/gradle-wrapper.jar"))
        val gradlew = File(root, "gradlew")
        copyAsset("templates/gradlew", gradlew)
        copyAsset("templates/gradlew.bat", File(root, "gradlew.bat"))
        runCatching { gradlew.setExecutable(true, false) }
    }

    private fun copyAsset(assetPath: String, target: File) {
        runCatching {
            target.parentFile?.mkdirs()
            application!!.assets.open(assetPath).use { input ->
                target.outputStream().use { output -> input.copyTo(output) }
            }
        }
    }
}
