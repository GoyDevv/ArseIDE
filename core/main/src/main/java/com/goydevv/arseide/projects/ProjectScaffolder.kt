package com.goydevv.arseide.projects

import com.goydevv.arseide.horizon.HorizonModPackager
import com.goydevv.arseide.horizon.HorizonModPorter
import com.goydevv.arseide.utils.application
import java.io.File
import java.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.eclipse.jgit.api.Git

/**
 * Writes the real files for a [ProjectConfig] onto disk.
 */
object ProjectScaffolder {

    sealed interface Result {
        data class Success(val projectRoot: File, val warning: String? = null) : Result

        data class Failure(val message: String, val cause: Throwable? = null) : Result
    }

    /** 16x16 valid RGBA PNG placeholder texture for scaffolded Horizon blocks and items. */
    private const val PLACEHOLDER_PNG_B64 =
        "iVBORw0KGgoAAAANSUhEUgAAABAAAAAQCAIAAACQkWg2AAAAFUlEQVR4nGNgYPjPgBsw4dWj4QEAAP//AwD9cQFJ3m3CAAAAAElFTkSuQmCC"

    suspend fun scaffold(config: ProjectConfig): Result =
        withContext(Dispatchers.IO) {
            runCatching {
                    val root = File(config.parentDir, config.name)
                    if (root.exists()) {
                        return@withContext Result.Failure("A project named \"${config.name}\" already exists here.")
                    }
                    if (!root.mkdirs()) {
                        return@withContext Result.Failure(
                            "Could not create project directory. Check storage permissions for ${config.parentDir.absolutePath}."
                        )
                    }

                    val warning: String? =
                        when (config.template) {
                            ProjectTemplate.NONE -> {
                                scaffoldNone(root, config)
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
                            ProjectTemplate.MINECRAFT_MOD -> scaffoldMinecraft(root, config)
                            ProjectTemplate.ANDROID_COMPOSE -> {
                                scaffoldAndroidCompose(root, config)
                                null
                            }
                            ProjectTemplate.HORIZON_CONTENT_MOD -> {
                                scaffoldHorizonContentMod(root, config)
                                null
                            }
                            ProjectTemplate.HORIZON_WASM_CLIENT_MOD -> {
                                scaffoldHorizonWasmClientMod(root, config)
                                null
                            }
                            ProjectTemplate.HORIZON_WASM_SERVER_MOD -> {
                                scaffoldHorizonWasmServerMod(root, config)
                                null
                            }
                            ProjectTemplate.HORIZON_NATIVE_MIXIN_MOD -> {
                                scaffoldHorizonNativeMixinMod(root, config)
                                null
                            }
                        }

                    if (config.initGit) initGitRepository(root, config)

                    Result.Success(root, warning)
                }
                .getOrElse { Result.Failure(it.message ?: "Unknown error while creating project", it) }
        }

    // ---- helpers ----------------------------------------------------------------

    private fun File.write(relativePath: String, content: String): File {
        val target = File(this, relativePath)
        target.parentFile?.mkdirs()
        target.writeText(content.trimStart('\n'))
        return target
    }

    private fun File.writePng(relativePath: String) {
        val target = File(this, relativePath)
        target.parentFile?.mkdirs()
        target.writeBytes(Base64.getDecoder().decode(PLACEHOLDER_PNG_B64))
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

    // ---- 1. Horizon Content Mod (.hmod, API 0.2) --------------------------------

    private suspend fun scaffoldHorizonContentMod(root: File, config: ProjectConfig) {
        val modId = config.resolvedModId()
        val safeName = escapeJson(config.name)
        val safeDesc = escapeJson(config.modDescription.ifBlank { "Declarative Horizon 26.3 content mod" })
        val safeAuthor = escapeJson(config.author.ifBlank { "GoyDevv" })
        val ver = config.modVersion.ifBlank { "0.1.0" }

        root.write(
            "horizon-mod.toml",
            """
            id = "$modId"
            name = "$safeName"
            version = "$ver"
            api = "0.2"
            description = "$safeDesc"
            authors = ["$safeAuthor"]
            capabilities = ["registries"]
            """
                .trimIndent() + "\n",
        )

        // Declarative Horizon Registries: block, item, creative_tab, mob_effect, entity_type, menu
        root.write(
            "data/$modId/horizon/block/ruby_ore.json",
            """
            {
              "template": "minecraft:stone",
              "hardness": 3.0,
              "explosion_resistance": 3.0,
              "requires_tool": true,
              "sounds": "stone",
              "item": true
            }
            """
                .trimIndent() + "\n",
        )

        root.write(
            "data/$modId/horizon/block/ruby_block.json",
            """
            {
              "template": "minecraft:stone",
              "hardness": 5.0,
              "explosion_resistance": 6.0,
              "light": 4,
              "requires_tool": true,
              "sounds": "metal",
              "item": true
            }
            """
                .trimIndent() + "\n",
        )

        root.write(
            "data/$modId/horizon/item/ruby.json",
            """
            {
              "max_stack_size": 64,
              "rarity": "uncommon",
              "fire_resistant": false
            }
            """
                .trimIndent() + "\n",
        )

        root.write(
            "data/$modId/horizon/item/ruby_apple.json",
            """
            {
              "max_stack_size": 64,
              "rarity": "rare",
              "components": {
                "minecraft:food": {
                  "nutrition": 6,
                  "saturation": 1.2,
                  "can_always_eat": true
                }
              }
            }
            """
                .trimIndent() + "\n",
        )

        root.write(
            "data/$modId/horizon/creative_tab/main.json",
            """
            {
              "title": "$safeName",
              "icon": "$modId:ruby",
              "items": [
                "$modId:ruby",
                "$modId:ruby_apple",
                "$modId:ruby_ore",
                "$modId:ruby_block"
              ]
            }
            """
                .trimIndent() + "\n",
        )

        root.write(
            "data/$modId/horizon/mob_effect/ruby_vigor.json",
            """
            {
              "category": "beneficial",
              "color": 16724838
            }
            """
                .trimIndent() + "\n",
        )

        root.write(
            "data/$modId/horizon/entity_type/ruby_golem.json",
            """
            {
              "category": "creature",
              "width": 1.4,
              "height": 2.7,
              "client_tracking_range": 10
            }
            """
                .trimIndent() + "\n",
        )

        root.write(
            "data/$modId/horizon/menu/ruby_infuser.json",
            """
            {
              "title": "Ruby Infuser",
              "rows": 3
            }
            """
                .trimIndent() + "\n",
        )

        // Minecraft 26.3 Data: singular recipe, loot_table, tags/block, tags/item
        root.write(
            "data/$modId/recipe/ruby_block.json",
            """
            {
              "type": "minecraft:crafting_shaped",
              "category": "building",
              "pattern": [
                "###",
                "###",
                "###"
              ],
              "key": {
                "#": "$modId:ruby"
              },
              "result": {
                "id": "$modId:ruby_block",
                "count": 1
              }
            }
            """
                .trimIndent() + "\n",
        )

        root.write(
            "data/$modId/loot_table/blocks/ruby_ore.json",
            """
            {
              "type": "minecraft:block",
              "pools": [
                {
                  "rolls": 1.0,
                  "entries": [
                    {
                      "type": "minecraft:item",
                      "name": "$modId:ruby"
                    }
                  ]
                }
              ]
            }
            """
                .trimIndent() + "\n",
        )

        root.write(
            "data/$modId/tags/block/ores.json",
            """
            {
              "replace": false,
              "values": [
                "$modId:ruby_ore"
              ]
            }
            """
                .trimIndent() + "\n",
        )

        root.write(
            "data/$modId/tags/item/gems.json",
            """
            {
              "replace": false,
              "values": [
                "$modId:ruby"
              ]
            }
            """
                .trimIndent() + "\n",
        )

        // Minecraft 26.3 Assets: blockstates, models/block, models/item, items (26.3 dispatch), lang, textures
        for (bname in listOf("ruby_ore", "ruby_block")) {
            root.write(
                "assets/$modId/blockstates/$bname.json",
                """
                {
                  "variants": {
                    "": { "model": "$modId:block/$bname" }
                  }
                }
                """
                    .trimIndent() + "\n",
            )
            root.write(
                "assets/$modId/models/block/$bname.json",
                """
                {
                  "parent": "minecraft:block/cube_all",
                  "textures": {
                    "all": "$modId:block/$bname"
                  }
                }
                """
                    .trimIndent() + "\n",
            )
            root.write(
                "assets/$modId/models/item/$bname.json",
                """
                {
                  "parent": "$modId:block/$bname"
                }
                """
                    .trimIndent() + "\n",
            )
            root.write(
                "assets/$modId/items/$bname.json",
                """
                {
                  "model": {
                    "type": "minecraft:model",
                    "model": "$modId:item/$bname"
                  }
                }
                """
                    .trimIndent() + "\n",
            )
            root.writePng("assets/$modId/textures/block/$bname.png")
        }

        for (iname in listOf("ruby", "ruby_apple")) {
            root.write(
                "assets/$modId/models/item/$iname.json",
                """
                {
                  "parent": "minecraft:item/generated",
                  "textures": {
                    "layer0": "$modId:item/$iname"
                  }
                }
                """
                    .trimIndent() + "\n",
            )
            root.write(
                "assets/$modId/items/$iname.json",
                """
                {
                  "model": {
                    "type": "minecraft:model",
                    "model": "$modId:item/$iname"
                  }
                }
                """
                    .trimIndent() + "\n",
            )
            root.writePng("assets/$modId/textures/item/$iname.png")
        }

        root.write(
            "assets/$modId/lang/en_us.json",
            """
            {
              "itemGroup.$modId.main": "$safeName",
              "block.$modId.ruby_ore": "Ruby Ore",
              "block.$modId.ruby_block": "Block of Ruby",
              "item.$modId.ruby": "Ruby",
              "item.$modId.ruby_apple": "Ruby Apple"
            }
            """
                .trimIndent() + "\n",
        )

        HorizonModPorter.copyBundledTools(File(root, "tools"))
        root.write(".gitignore", "*.hmod\n/target/\n")
        root.write(
            "README.md",
            """
            # ${config.name} (`$modId.hmod`)

            Declarative Horizon Content Mod (API `0.2`, Minecraft `26.3` layout).

            ## Structure
            - `horizon-mod.toml`: Strict Horizon manifest (`capabilities = ["registries"]`)
            - `data/$modId/horizon/{block,item,creative_tab,mob_effect,entity_type,menu}/`: Declarative Horizon registries
            - `data/$modId/{recipe,loot_table,tags/block,tags/item}/`: Minecraft 26.3 singular data folders
            - `assets/$modId/items/*.json`: Minecraft 26.3 client item model dispatch files

            ## One-Click Build & Package
            Press **Run** in Arse IDE (or use the **Horizon** bottom tab -> **Package .hmod**) to validate and package `$modId.hmod`.
            """
                .trimIndent() + "\n",
        )

        // Pre-package initial <id>.hmod
        HorizonModPackager.packageProject(root)
    }

    // ---- 2. Horizon Client / Hybrid WASM Mod (.hmod, API 0.2) -------------------

    private suspend fun scaffoldHorizonWasmClientMod(root: File, config: ProjectConfig) {
        val modId = config.resolvedModId()
        val safeName = escapeJson(config.name)
        val safeDesc = escapeJson(config.modDescription.ifBlank { "Horizon Client/Hybrid WASM Mod" })
        val safeAuthor = escapeJson(config.author.ifBlank { "GoyDevv" })
        val ver = config.modVersion.ifBlank { "0.1.0" }

        HorizonModPorter.copyBundledSdk(File(root, "sdk/horizon-mod-sdk"))
        HorizonModPorter.copyBundledTools(File(root, "tools"))

        root.write(
            "horizon-mod.toml",
            """
            id = "$modId"
            name = "$safeName"
            version = "$ver"
            api = "0.2"
            entry = "mod.wasm"
            description = "$safeDesc"
            authors = ["$safeAuthor"]
            capabilities = ["registries", "hud", "render", "ui", "input", "storage"]
            experimental = ["ui", "render", "input"]
            """
                .trimIndent() + "\n",
        )

        root.write(
            "Cargo.toml",
            """
            [package]
            name = "$modId"
            version = "$ver"
            edition = "2021"

            [lib]
            crate-type = ["cdylib", "rlib"]

            [dependencies]
            horizon-mod-sdk = { path = "sdk/horizon-mod-sdk" }
            """
                .trimIndent() + "\n",
        )

        root.write(
            "src/lib.rs",
            """
            use horizon_mod_sdk::{
                export_mod, game, hud, input, log, render, storage, ui, Action, Anchor, InputEvent, KeybindId,
                Mod, RenderContext, ScreenDef, UiEvent, WidgetDef,
            };

            const HUD_TITLE_ID: u32 = 1;
            const HUD_TARGET_ID: u32 = 2;
            const SCREEN_MAIN: u32 = 10;
            const BTN_TOGGLE_HUD: u32 = 100;
            const BTN_CLOSE: u32 = 101;

            pub struct ClientWasmMod {
                hud_visible: bool,
                toggle_hud_key: Option<KeybindId>,
                open_screen_key: Option<KeybindId>,
                last_target: String,
            }

            impl Default for ClientWasmMod {
                fn default() -> Self {
                    Self {
                        hud_visible: true,
                        toggle_hud_key: None,
                        open_screen_key: None,
                        last_target: String::from("None"),
                    }
                }
            }

            impl Mod for ClientWasmMod {
                fn init(&mut self) -> Result<(), String> {
                    if let Some(bytes) = storage::get("hud_visible") {
                        self.hud_visible = bytes != b"0";
                    }
                    self.toggle_hud_key = input::register_keybind(
                        "$modId.toggle_hud",
                        "Toggle $safeName HUD",
                        "KeyH",
                        "$safeName",
                    )
                    .ok();
                    self.open_screen_key = input::register_keybind(
                        "$modId.open_panel",
                        "Open $safeName Panel",
                        "KeyJ",
                        "$safeName",
                    )
                    .ok();
                    log::info("$safeName WASM client mod initialized!");
                    Ok(())
                }

                fn tick(&mut self, _dt: f32) {
                    if !self.hud_visible {
                        let _ = hud::remove(HUD_TITLE_ID);
                        let _ = hud::remove(HUD_TARGET_ID);
                        return;
                    }

                    self.last_target = match game::raycast(5.0) {
                        Some(hit) => format!("{} ({}, {}, {})", hit.block_id, hit.block_pos.0, hit.block_pos.1, hit.block_pos.2),
                        None => String::from("Air"),
                    };

                    let _ = hud::text(
                        HUD_TITLE_ID,
                        "$safeName [H: HUD | J: Panel]",
                        Anchor::TopLeft,
                        8.0,
                        8.0,
                        1.0,
                        0xFF66E3F4,
                    );
                    let _ = hud::text(
                        HUD_TARGET_ID,
                        &format!("Looking at: {}", self.last_target),
                        Anchor::TopLeft,
                        8.0,
                        20.0,
                        1.0,
                        0xFFEAF0FA,
                    );
                }

                fn on_render(&mut self, _ctx: RenderContext) {
                    if self.hud_visible {
                        let _ = render::draw_rect(4.0, 4.0, 220.0, 30.0, 0x660A0E17);
                    }
                }

                fn on_input(&mut self, event: InputEvent) -> bool {
                    if event.action != Action::Press {
                        return false;
                    }
                    if self.toggle_hud_key == Some(event.keybind) {
                        self.hud_visible = !self.hud_visible;
                        let _ = storage::set("hud_visible", if self.hud_visible { b"1" } else { b"0" });
                        return true;
                    }
                    if self.open_screen_key == Some(event.keybind) {
                        self.open_control_screen();
                        return true;
                    }
                    false
                }

                fn on_ui_event(&mut self, event: UiEvent) {
                    match event {
                        UiEvent::ButtonClicked { screen, widget } if screen == SCREEN_MAIN => {
                            if widget == BTN_TOGGLE_HUD {
                                self.hud_visible = !self.hud_visible;
                                let _ = storage::set("hud_visible", if self.hud_visible { b"1" } else { b"0" });
                            } else if widget == BTN_CLOSE {
                                let _ = ui::close_screen();
                            }
                        }
                        _ => {}
                    }
                }
            }

            impl ClientWasmMod {
                fn open_control_screen(&self) {
                    let screen = ScreenDef {
                        id: SCREEN_MAIN,
                        title: "$safeName Settings".to_string(),
                        width: 240.0,
                        height: 150.0,
                        widgets: vec![
                            WidgetDef::Label {
                                id: 1,
                                x: 16.0,
                                y: 20.0,
                                text: format!("Target: {}", self.last_target),
                                scale: 1.0,
                                color: 0xFFFFFFFF,
                            },
                            WidgetDef::Button {
                                id: BTN_TOGGLE_HUD,
                                x: 16.0,
                                y: 52.0,
                                w: 208.0,
                                h: 24.0,
                                text: "Toggle HUD Overlay".to_string(),
                            },
                            WidgetDef::Button {
                                id: BTN_CLOSE,
                                x: 16.0,
                                y: 88.0,
                                w: 208.0,
                                h: 24.0,
                                text: "Close".to_string(),
                            },
                        ],
                    };
                    let _ = ui::open_screen(&screen);
                }
            }

            export_mod!(ClientWasmMod);
            """
                .trimIndent() + "\n",
        )

        // Include a sample 26.3 item + model dispatch so hybrid content + WASM works out of the box
        root.write(
            "data/$modId/horizon/item/scanner_wand.json",
            """
            {
              "max_stack_size": 1,
              "rarity": "rare"
            }
            """
                .trimIndent() + "\n",
        )
        root.write(
            "data/$modId/horizon/creative_tab/main.json",
            """
            {
              "title": "$safeName",
              "icon": "$modId:scanner_wand",
              "items": ["$modId:scanner_wand"]
            }
            """
                .trimIndent() + "\n",
        )
        root.write(
            "assets/$modId/models/item/scanner_wand.json",
            """
            {
              "parent": "minecraft:item/handheld",
              "textures": {
                "layer0": "$modId:item/scanner_wand"
              }
            }
            """
                .trimIndent() + "\n",
        )
        root.write(
            "assets/$modId/items/scanner_wand.json",
            """
            {
              "model": {
                "type": "minecraft:model",
                "model": "$modId:item/scanner_wand"
              }
            }
            """
                .trimIndent() + "\n",
        )
        root.writePng("assets/$modId/textures/item/scanner_wand.png")
        root.write(
            "assets/$modId/lang/en_us.json",
            """
            {
              "itemGroup.$modId.main": "$safeName",
              "item.$modId.scanner_wand": "Scanner Wand"
            }
            """
                .trimIndent() + "\n",
        )

        root.write(".gitignore", "/target/\n*.hmod\nmod.wasm\n")
        root.write(
            "README.md",
            """
            # ${config.name} (`$modId.hmod`)

            Client / Hybrid Rust WASM Mod for Horizon (`api = "0.2"`, `wasm32-unknown-unknown`).

            ## Building & Packaging
            Press **Run** in Arse IDE or run:
            ```bash
            cargo build --target wasm32-unknown-unknown --release
            python3 tools/package_hmod.py .
            ```
            """
                .trimIndent() + "\n",
        )
    }

    // ---- 3. Horizon Server Gameplay WASM Mod (.hmod, API 0.3) -------------------

    private suspend fun scaffoldHorizonWasmServerMod(root: File, config: ProjectConfig) {
        val modId = config.resolvedModId()
        val safeName = escapeJson(config.name)
        val safeDesc = escapeJson(config.modDescription.ifBlank { "Horizon Server Gameplay WASM Mod" })
        val safeAuthor = escapeJson(config.author.ifBlank { "GoyDevv" })
        val ver = config.modVersion.ifBlank { "0.1.0" }

        HorizonModPorter.copyBundledSdk(File(root, "sdk/horizon-mod-sdk"))
        HorizonModPorter.copyBundledTools(File(root, "tools"))

        root.write(
            "horizon-mod.toml",
            """
            id = "$modId"
            name = "$safeName"
            version = "$ver"
            api = "0.3"
            side = "server"
            entry = "mod.wasm"
            description = "$safeDesc"
            authors = ["$safeAuthor"]
            capabilities = ["events", "world", "players", "commands", "storage"]
            experimental = ["world", "players"]
            """
                .trimIndent() + "\n",
        )

        root.write(
            "Cargo.toml",
            """
            [package]
            name = "$modId"
            version = "$ver"
            edition = "2021"

            [lib]
            crate-type = ["cdylib", "rlib"]

            [dependencies]
            horizon-mod-sdk = { path = "sdk/horizon-mod-sdk" }
            """
                .trimIndent() + "\n",
        )

        root.write(
            "src/lib.rs",
            """
            use horizon_mod_sdk::{
                commands, export_server_mod, log, players, storage, world, BlockBreakEvent, BlockPos,
                BlockUseEvent, CommandContext, CommandResult, EventOutcome, PlayerJoinEvent, ServerMod,
            };

            #[derive(Default)]
            pub struct GameplayServerMod {
                protected_spawn_radius: i32,
            }

            impl ServerMod for GameplayServerMod {
                fn init(&mut self) -> Result<(), String> {
                    self.protected_spawn_radius = storage::get("spawn_radius")
                        .and_then(|b| std::str::from_utf8(&b).ok()?.parse::<i32>().ok())
                        .unwrap_or(16);

                    commands::register(
                        "protectradius",
                        "Set the protected spawn radius",
                        &["/protectradius <blocks>"],
                    )?;
                    commands::register(
                        "pillar",
                        "Place a 3-block glowstone pillar above your position",
                        &["/pillar"],
                    )?;
                    log::info(&format!(
                        "$safeName server mod loaded (protected_spawn_radius={})",
                        self.protected_spawn_radius
                    ));
                    Ok(())
                }

                fn on_player_join(&mut self, event: PlayerJoinEvent) {
                    let _ = players::send_message(
                        &event.player.name,
                        &format!("Welcome to $safeName, {}!", event.player.name),
                    );
                }

                fn on_block_break(&mut self, event: BlockBreakEvent) -> EventOutcome {
                    if event.pos.x.abs() <= self.protected_spawn_radius
                        && event.pos.z.abs() <= self.protected_spawn_radius
                    {
                        let _ = players::send_action_bar(
                            &event.player.name,
                            "Spawn protection is active in this area!",
                        );
                        return EventOutcome::Cancel;
                    }
                    EventOutcome::Continue
                }

                fn on_block_use(&mut self, event: BlockUseEvent) -> EventOutcome {
                    if event.is_sneaking {
                        if let Ok(block_id) = world::get_block_id(event.pos) {
                            let _ = players::send_action_bar(
                                &event.player.name,
                                &format!("Block @ ({}, {}, {}): {}", event.pos.x, event.pos.y, event.pos.z, block_id),
                            );
                        }
                    }
                    EventOutcome::Continue
                }

                fn on_command(&mut self, ctx: CommandContext) -> CommandResult {
                    match ctx.command.as_str() {
                        "protectradius" => {
                            let Some(first) = ctx.args.first() else {
                                return CommandResult::Error("Usage: /protectradius <blocks>".to_string());
                            };
                            match first.parse::<i32>() {
                                Ok(radius) if (0..=512).contains(&radius) => {
                                    self.protected_spawn_radius = radius;
                                    let _ = storage::set("spawn_radius", radius.to_string().as_bytes());
                                    CommandResult::Success(format!("Protected spawn radius set to {radius} blocks."))
                                }
                                _ => CommandResult::Error("Radius must be an integer between 0 and 512.".to_string()),
                            }
                        }
                        "pillar" => {
                            let base_x = ctx.sender.position.x.floor() as i32;
                            let base_y = ctx.sender.position.y.floor() as i32;
                            let base_z = ctx.sender.position.z.floor() as i32;
                            for dy in 2..=4 {
                                let _ = world::set_block_id(
                                    BlockPos::new(base_x, base_y + dy, base_z),
                                    "minecraft:glowstone",
                                );
                            }
                            CommandResult::Success("Placed a glowstone pillar above you.".to_string())
                        }
                        _ => CommandResult::NotHandled,
                    }
                }
            }

            export_server_mod!(GameplayServerMod);
            """
                .trimIndent() + "\n",
        )

        root.write(".gitignore", "/target/\n*.hmod\nmod.wasm\n")
        root.write(
            "README.md",
            """
            # ${config.name} (`$modId.hmod`)

            Server-Side Gameplay WASM Mod for Horizon (`api = "0.3"`, `side = "server"`).

            ## Building & Packaging
            Press **Run** in Arse IDE or run:
            ```bash
            cargo build --target wasm32-unknown-unknown --release
            python3 tools/package_hmod.py .
            ```
            """
                .trimIndent() + "\n",
        )
    }

    // ---- 4. Horizon Native Mixin Mod (.hnmod) -----------------------------------

    private suspend fun scaffoldHorizonNativeMixinMod(root: File, config: ProjectConfig) {
        val modId = config.resolvedModId()
        val safeName = escapeJson(config.name)
        val safeDesc = escapeJson(config.modDescription.ifBlank { "Horizon Native Mixin Mod (.hnmod)" })
        val safeAuthor = escapeJson(config.author.ifBlank { "GoyDevv" })
        val ver = config.modVersion.ifBlank { "0.1.0" }

        HorizonModPorter.copyBundledTools(File(root, "tools"))

        root.write(
            "horizon-mixin.toml",
            """
            id = "$modId"
            name = "$safeName"
            version = "$ver"
            api = "0.1"
            description = "$safeDesc"
            authors = ["$safeAuthor"]
            environment = "client"

            [libraries]
            linux_x86_64 = "libraries/linux-x86_64/lib$modId.so"
            linux_aarch64 = "libraries/linux-aarch64/lib$modId.so"
            windows_x86_64 = "libraries/windows-x86_64/$modId.dll"

            [[hooks]]
            target = "horizon_client::game::native_hooks::Game"
            method = "on_frame"
            point = "TAIL"
            callback = "on_game_frame_tail"

            [[hooks]]
            target = "horizon_client::game::native_hooks::Game"
            method = "on_key_input"
            point = "HEAD"
            callback = "on_game_key_input_head"
            cancellable = true
            """
                .trimIndent() + "\n",
        )

        root.write(
            "Cargo.toml",
            """
            [package]
            name = "$modId"
            version = "$ver"
            edition = "2021"

            [lib]
            crate-type = ["cdylib"]
            """
                .trimIndent() + "\n",
        )

        root.write(
            "src/lib.rs",
            """
            //! Native Horizon Mixin hooks targeting `horizon_client::game::native_hooks::Game`.

            use std::sync::atomic::{AtomicU64, Ordering};

            static FRAME_COUNTER: AtomicU64 = AtomicU64::new(0);

            #[repr(C)]
            pub struct CallbackInfo {
                pub cancelled: bool,
            }

            /// Injected at TAIL of `horizon_client::game::native_hooks::Game::on_frame`.
            #[no_mangle]
            pub extern "C" fn on_game_frame_tail(_dt: f32) {
                let _frame = FRAME_COUNTER.fetch_add(1, Ordering::Relaxed);
            }

            /// Injected at HEAD of `horizon_client::game::native_hooks::Game::on_key_input`.
            #[no_mangle]
            pub extern "C" fn on_game_key_input_head(key_code: i32, action: i32, ci: *mut CallbackInfo) {
                if key_code == 290 && action == 1 && !ci.is_null() {
                    unsafe {
                        (*ci).cancelled = false;
                    }
                }
            }
            """
                .trimIndent() + "\n",
        )

        root.write(".gitignore", "/target/\n*.hnmod\n")
        root.write(
            "README.md",
            """
            # ${config.name} (`$modId.hnmod`)

            Horizon Native Mixin Mod targeting `horizon_client::game::native_hooks::Game`.

            ## Building & Packaging
            Press **Run** in Arse IDE to compile the native `cdylib` and package `$modId.hnmod`.
            """
                .trimIndent() + "\n",
        )

        HorizonModPackager.packageProject(root)
    }

    // ---- None -------------------------------------------------------------------

    private fun scaffoldNone(root: File, config: ProjectConfig) {
        root.write("README.md", "# ${config.name}\n\nCreated with Arse IDE.\n")
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
                # ${config.name} - entry point


                def main() -> None:
                    print("Hello from $safeName!")


                if __name__ == "__main__":
                    main()
                """
                    .trimIndent() + "\n",
            )
        runCatching { mainFile.setExecutable(true, false) }
        root.write("requirements.txt", "# Add your pip dependencies here (one per line)\n")
        root.write(
            ".gitignore",
            """
            __pycache__/
            *.py[cod]
            .venv/
            venv/
            .env
            """
                .trimIndent() + "\n",
        )
        root.write(
            "README.md",
            "# ${config.name}\n\nRun with:\n\n```bash\n${if (python3) "python3" else "python"} main.py\n```\n",
        )
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
              "keywords": [],
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

            function main() {
              console.log("Hello from $safeDesc!");
            }

            main();
            """
                .trimIndent() + "\n",
        )
        root.write(
            ".gitignore",
            """
            node_modules/
            npm-debug.log*
            .env
            dist/
            """
                .trimIndent() + "\n",
        )
        root.write(
            "README.md",
            "# ${config.name}\n\n```bash\nnpm install\nnpm start\n```\n",
        )
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
                    <span class="badge">Arse IDE Web Starter</span>
                    <h1>$safeTitle</h1>
                    <p id="message">Welcome to $safeTitle — edit <code>index.html</code>, <code>style.css</code>, or <code>script.js</code> and hit Run to preview live.</p>
                    <div class="actions">
                        <button id="counter-btn" type="button">Clicks: <span id="count">0</span></button>
                    </div>
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
                font-family: system-ui, -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif;
                margin: 0;
                min-height: 100vh;
                display: grid;
                place-items: center;
                padding: 1.5rem;
                background: var(--bg);
                color: var(--text);
            }
            .card {
                max-width: 460px;
                width: 100%;
                text-align: center;
                padding: 2.25rem 1.75rem;
                border-radius: 24px;
                background: var(--surface);
                box-shadow: 0 12px 32px rgba(0, 0, 0, 0.28);
            }
            .badge {
                display: inline-block;
                padding: 0.3rem 0.85rem;
                border-radius: 999px;
                font-size: 0.75rem;
                font-weight: 600;
                color: var(--on-primary);
                background: var(--primary);
            }
            h1 { margin: 1rem 0 0.6rem; font-size: 2rem; }
            p { color: var(--muted); line-height: 1.6; margin-bottom: 1.5rem; }
            button {
                appearance: none;
                border: none;
                background: var(--primary);
                color: var(--on-primary);
                font-weight: 600;
                font-size: 0.95rem;
                padding: 0.75rem 1.5rem;
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
              console.log("$safeJsTitle is running!");
            });
            """
                .trimIndent() + "\n",
        )
        root.write(".gitignore", "node_modules/\ndist/\n.env\n")
        root.write("README.md", "# ${config.name}\n\nOpen `index.html` and press the Run button to launch the live preview.\n")
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

            # Fabric Properties (resolved for Minecraft $mc)
            minecraft_version=$mc
            yarn_mappings=${pins.yarn}
            loader_version=${pins.loader}

            # Mod Properties
            mod_version=${config.modVersion}
            maven_group=$pkg
            archives_base_name=$modId

            # Fabric API
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
                    LOGGER.info("$safeName initialized!");
                }
            }
            """
                .trimIndent() + "\n",
        )

        writeGradleWrapper(root, "8.10.2")
        root.write(".gitignore", ".gradle/\nbuild/\nout/\n.idea/\n*.iml\n")
        root.write("README.md", minecraftReadme(config, "Fabric", mc, jdk))

        return if (!pins.online) {
            "Used offline Fabric defaults for Minecraft $mc — check gradle.properties if you customize dependencies."
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
            # Resolved Forge build for Minecraft $mc
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
        root.write("README.md", minecraftReadme(config, "Forge", mc, jdk))

        return if (!forgePin.exactMatch) {
            "Forge does not have a published build for Minecraft $requestedMc yet — configured for Minecraft $mc (Forge $forge) so the project builds cleanly."
        } else {
            null
        }
    }

    private fun minecraftReadme(config: ProjectConfig, loader: String, mc: String, jdk: String): String =
        """
        # ${config.name}

        $loader mod for Minecraft $mc (Java $jdk).

        > Tip: Use the **Horizon** bottom tab in Arse IDE to port this mod to a Horizon `.hmod` project at any time!

        ```bash
        ./gradlew build
        ```
        """
            .trimIndent() + "\n"

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

    // ---- Android Jetpack Compose (Clean Material 3 project — no test bloat) -----

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

        // settings.gradle.kts
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

        // Root build.gradle.kts
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

        // Clean Version Catalog (no junit / espresso test bloat)
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

        // gradle.properties — build cache enabled & Phantom Process Killer friendly worker bounds
        root.write(
            "gradle.properties",
            """
            # Project-wide Gradle settings (optimised for Arse IDE on Android).
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

        // app/build.gradle.kts — clean production Android setup without unit/instrumented test bloat
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

        root.write("app/proguard-rules.pro", "# Add project specific ProGuard rules here.\n")
        root.write("app/.gitignore", "/build\n")

        // AndroidManifest.xml
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

        // Material You ui.theme package (Color.kt, Type.kt, Theme.kt)
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

        // MainActivity.kt — Clean Material You TopAppBar + ElevatedCard + FAB starter
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
                            Text("Increment (${'$'}count)")
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
                                    text = "Material You Ready",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "Built with Jetpack Compose and dynamic Material 3 theming in Arse IDE.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Spacer(modifier = Modifier.height(14.dp))
                                FilledTonalButton(onClick = { count = 0 }) {
                                    Text("Reset Counter")
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

        // Resources
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

        // Launcher icons
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

        root.write(
            "README.md",
            """
            # ${config.name}

            Material You (Material 3) Jetpack Compose Android project created with **Arse IDE**.

            ## Building
            Press **Sync** once, then **Run** in Arse IDE, or run in the terminal sandbox:

            ```bash
            ./gradlew assembleDebug
            ```
            """
                .trimIndent() + "\n",
        )
    }

    // ---- Gradle wrapper ---------------------------------------------------------

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
