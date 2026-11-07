# Arse IDE (`com.goydevv.arseide`)

**Arse IDE** is a Material You (Material 3) Android IDE for building Android (Jetpack Compose), Minecraft Java (Fabric / Forge), and **Horizon (`.hmod` / `.hnmod`)** mods directly on your device.

## Key Features

### 1. Horizon Mod Studio (`.hmod` / `.hnmod`)
- **4 Starter Templates**:
  - **Horizon Content Mod (`.hmod`, API `0.2`)**: Scaffolds `horizon-mod.toml` (`capabilities = ["registries"]`), declarative `data/<id>/horizon/{block,item,creative_tab,mob_effect,entity_type,menu}/`, and Minecraft 26.3 `assets/<id>/` & `data/<id>/` layout.
  - **Horizon Client / Hybrid WASM Mod (`.hmod`, API `0.1` / `0.2`)**: Scaffolds a Rust `cdylib` (`wasm32-unknown-unknown`) linked against `horizon-mod-sdk` with HUD, UI screen, keybind, raycast, and storage hooks (`export_mod!`), plus 26.3 `assets/` & `data/`.
  - **Horizon Server Gameplay WASM Mod (`.hmod`, API `0.3`)**: Scaffolds a server-side Rust WASM project (`side = "server"`, `export_server_mod!`) with `block_break`, `block_use`, `player_join`, world block manipulation, and custom commands.
  - **Horizon Native Mixin Mod (`.hnmod`)**: Scaffolds `horizon-mixin.toml` and Rust `extern "C"` hook helpers targeting `horizon_client::game::native_hooks::Game`.
- **Port Java / Fabric / NeoForge Mod to Horizon (`.hmod`)**:
  - Accepts either a compiled `.jar` or an open-source Fabric/NeoForge project folder.
  - Extracts and normalizes metadata (`[a-z0-9_]{1,64}`), upgrades 1.20/1.21 plural data paths and recipes/loot tables to Minecraft 26.3, generates `assets/<id>/items/<name>.json` model dispatches, converts Java `Registry.register(BuiltInRegistries.BLOCK/ITEM, ...)` calls to declarative `data/<id>/horizon/{block,item,creative_tab}/` JSON, and translates client/server gameplay hooks to Rust WASM (`src/lib.rs`).
- **One-Click Validation & Deflate Packaging**:
  - Validates `horizon-mod.toml` against Horizon's strict manifest rules and packages `<id>.hmod` / `<id>.hnmod` with no parent wrapper directory.

### 2. Material You (Material 3) Workspace UI
- **Dedicated File Tree Left Drawer**: Opened exclusively from the top-left File Explorer icon button.
- **5 Animated Bottom Navigation Fragments**: `Editor`, `Projects` (with Material 3 `ExtendedFloatingActionButton`), `Horizon`, `Git`, and `Tools & Config` with directional horizontal slide animations.

### 3. Android Build Cache & Phantom Process Killer Bypass
- **Gradle Build Cache Enabled**: Default `--build-cache` and `org.gradle.caching=true` eliminate `Build cache is disabled` task warnings.
- **Android 12+ Phantom Process Killer Bypass**: Foreground `WakeLock` + `WifiLock`, bounded Gradle/Cargo workers (`org.gradle.workers.max=2`, `CARGO_BUILD_JOBS=2`), in-process Kotlin compilation (`kotlin.compiler.execution.strategy=in-process`), and `device_config` / `settings_config_monitor_phantom_procs` helper.
