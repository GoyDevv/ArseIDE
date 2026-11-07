<div align="center">

<img src="docs/assets/icon.png" alt="Arse IDE Logo" width="136" height="136" />

# Arse IDE

**A fast, Material You Android IDE for Kotlin/Java, Android Jetpack Compose, Minecraft Fabric/Forge, and Horizon Native Mods (`.hnmod`).**

[![GitHub Repository](https://img.shields.io/badge/GitHub-GoyDevv%2FArseIDE-6366F1?style=for-the-badge&logo=github)](https://github.com/GoyDevv/ArseIDE)
[![Latest Release](https://img.shields.io/github/v/release/GoyDevv/ArseIDE?style=for-the-badge&color=06B6D4&label=Release)](https://github.com/GoyDevv/ArseIDE/releases)
[![Platform](https://img.shields.io/badge/Android-8.0%2B%20(API%2026%2B)-34D399?style=for-the-badge&logo=android)](https://github.com/GoyDevv/ArseIDE)
[![Contributing](https://img.shields.io/badge/PRs%20%26%20AI-Welcome-C084FC?style=for-the-badge)](CONTRIBUTING.md)

</div>

---

## Highlights

| Pillar | What You Get |
| :--- | :--- |
| **Material You Workspace** | Four focused bottom-navigation destinations (**Editor**, **Projects**, **Git**, **Settings**), dynamic Android 12+ Monet theming, AMOLED pure-black mode, and a dedicated File Tree drawer. |
| **Fast `+` Project Actions** | One-tap `+` action sheet in the **Projects** tab to scaffold a new project, convert a `.jar` or Fabric/Forge mod to `.hnmod`, mount a local directory, clone a Git repository, or open `/sdcard` and `~`. |
| **Resilient Linux Sandbox** | Full Ubuntu 24.04 PRoot environment with multi-mirror rootfs fallback (`Xed-Editor/Karbon-PackagesX` + official `cdimage.ubuntu.com`). If you skip Auto-Setup on first launch, you can trigger **Run Full Auto-Setup** anytime from the Terminal, Projects tab, or Settings. |
| **Horizon `.hnmod` Studio** | Scaffold, validate, compile, and package native Rust Horizon 26.3 `.hnmod` bundles (`horizon-native.json` + `mixins/<id>/`), or pick any `.jar` mod via the Android system file picker to port it automatically. |
| **Live GitHub Release Notes** | Queries [`GoyDevv/ArseIDE` Releases](https://github.com/GoyDevv/ArseIDE/releases) on startup—rendering Markdown release notes when updated to a published release, prompting when a newer version is out, and staying silent when no release entry exists. |

---

## Workspace & Navigation Tour

### 1. Editor
- Powered by **Rosemoe Sora Editor** with TextMate grammars, incremental syntax highlighting, sticky scroll, minimap, and LSP integration.
- Automatic theme synchronization across **Material You (Dynamic Color)**, **BlueBerry**, **Obsidian Glass**, **Lime**, and **AMOLED Pure Black**.
- Reorderable file tabs with Git diff coloring (`Added`, `Modified`, `Conflicted`) and unsaved-change indicators.

### 2. Projects & Fast `+` Menu
- Scans your sandbox workspace and displays project size, package/mod ID, Git status, and timestamps with live search filtering.
- Tap **any project card** to open its interactive action sheet:
  - **Open in Editor**
  - **Build / Run Project** (or **Compile & Package `.hnmod`**)
  - **Convert to Horizon `.hnmod`** (for Fabric & Forge Java mods)
  - **Open Terminal Here**
- Tap the **`+ Add / Open` FAB** for instant access to all workspace entry points:
  - **Create New Project** (*Minecraft Mod, Android Compose, Python, Node.js, Web, or Horizon `.hnmod`*)
  - **Convert Fabric / Forge Mod or `.jar` to `.hnmod`** (*uses the Android system `.jar` file picker*)
  - **Open Directory** (*SAF folder mount*)
  - **Clone Git Repository**
  - **Internal Storage (`/sdcard`)** & **Terminal Home (`~`)**
  - **Run Sandbox & Toolchain Auto-Setup** (*always visible if first-launch setup was skipped*)

### 3. Git Client
- Built-in **JGit** + GitHub CLI integration: initialize repositories, stage/unstage files, inspect diffs, switch branches, commit, pull, and push directly from the **Git** tab.

### 4. Settings & Built-In Tools
- **Editor & Code Appearance**: Font size slider (`10sp`–`28sp`), tab width (`2 / 4 / 8`), line numbers, word wrap, extra symbol bar, and auto-save.
- **Compiler & `.hnmod` Toolchain**: Toggle `--build-cache` for Gradle and incremental Cargo caching, pin 40-character hex `horizon` and `mixins` revisions, and configure JDK/NDK/dependencies.
- **Tools & Utilities**: Launch the `.jar -> .hnmod` converter, run the `.hnmod` bundle validator, clean `build/` & `target/` caches, open the Ubuntu terminal, or apply the Android 12+ Phantom Process Killer bypass.
- **UI & Animations**: Toggle Material You dynamic colors, AMOLED dark mode, preset themes, smooth tab transitions, and cursor animations with instant editor re-theming.

---

## Horizon Native Mod (`.hnmod`) Pipeline

Arse IDE targets the **Horizon 26.3 `.hnmod` (`horizon-mixins`)** bundle specification:

```text
<project-root>/
├── horizon-native.json              # schema = 1, id, version, game = "26.3", 40-char horizon & mixins SHAs, hooks API
└── mixins/
    └── <id>/
        ├── horizon-mixin.toml       # [mod], [compatibility].horizon_revision, [[module]], [[injection]]
        ├── runtime.rs               # Shared state & constants injected into horizon_client::game
        ├── list.rs / settings.rs / settings_click.rs / tick.rs / hud.rs / key.rs / destroy_block.rs ...
        ├── data/
        │   └── port_manifest.json   # Extracted metadata & event signals when ported from a Java .jar
        └── engine/                  # Companion Rust crate verified via background `cargo check` / `rustc`
            ├── Cargo.toml
            └── src/lib.rs
```

Every build validates the bundle against the rules of `crates/horizon-mods/src/index/native.rs` (`Bundle::read`) and outputs `build/<id>.hnmod`.

---

## Building from Source

### Prerequisites
- **JDK 21**
- **Android SDK** (Compile SDK `37`, Min SDK `26`)

### Commands

```bash
# Clone the repository
git clone https://github.com/GoyDevv/ArseIDE.git
cd ArseIDE

# Build debug APK
./gradlew assembleDebug

# Build release / pro APK
./gradlew assembleRelease
```

---

## Contributing

Contributions—whether written by hand or generated with AI coding assistants—are welcome!  
Please read **[CONTRIBUTING.md](CONTRIBUTING.md)** before opening an Issue or Pull Request for our workflow guide and **AI-Assisted Contribution Disclosure Policy**.

---

## License

Arse IDE is released under the **GNU General Public License v3.0 (GPL-3.0)**. See [LICENSE](LICENSE) for full terms.
