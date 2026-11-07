# Contributing to Arse IDE

Thank you for your interest in contributing to **[Arse IDE](https://github.com/GoyDevv/ArseIDE)**! Whether you are fixing a bug, refining Material You themes, improving the Ubuntu sandbox terminal, or expanding the Horizon `.hnmod` toolchain, this guide covers everything you need to know to land your changes smoothly.

---

## Table of Contents

1. [Ways to Contribute](#1-ways-to-contribute)
2. [AI-Assisted Contributions & Disclosure Policy ("AI Slop" Rules)](#2-ai-assisted-contributions--disclosure-policy-ai-slop-rules)
3. [Repository Architecture](#3-repository-architecture)
4. [Local Development & Build Setup](#4-local-development--build-setup)
5. [Pull Request Checklist & Template](#5-pull-request-checklist--template)

---

## 1. Ways to Contribute

- **Bug Reports**: Open an issue at [`https://github.com/GoyDevv/ArseIDE/issues`](https://github.com/GoyDevv/ArseIDE/issues) with reproduction steps, Android version, device ABI (`arm64-v8a`, `armeabi-v7a`, `x86_64`), and relevant stack traces or terminal logs.
- **Feature Requests & UI Polish**: Propose improvements to the editor, project scaffolder, Material You color system, or terminal sandbox.
- **Pull Requests**: Submit bug fixes, new project templates, performance optimizations, or documentation updates.

---

## 2. AI-Assisted Contributions & Disclosure Policy ("AI Slop" Rules)

**AI-generated code ("AI slop"), AI-assisted pull requests, and autonomous agent contributions are 100% allowed in Arse IDE**—provided you explain **everything in full technical detail**.

Unexplained, copy-pasted AI dumps without context will be closed. Well-documented AI contributions that clearly explain what was changed, which model was used, why it was used, and how it was verified are gladly accepted!

### Mandatory Disclosure Requirements for Every PR / Issue

Every Pull Request (and any AI-filed Issue) **must** include an **AI & Authorship Disclosure Block** at the top of the description answering all five of the following:

1. **Opened By (`Human` vs. `AI Agent`)**:
   - State explicitly whether the PR or Issue was opened manually by a human developer, by a human using an AI assistant, or autonomously by an AI agent/bot.
2. **Model(s) & Harness Used**:
   - Name the exact model(s), provider, and coding tool/harness used (for example: `Claude 3.7 Sonnet via Cursor`, `GPT-4o via GitHub Copilot`, `Gemini 2.5 Pro`, `DeepSeek-R1`, or `None — 100% handwritten`).
3. **Why AI Was Used & Prompt/Task Summary**:
   - Explain what problem you asked the model to solve, why AI was chosen for that part of the workflow (e.g., boilerplate scaffolding, regex parsing, Compose animation math, porting script generation), and any constraints you gave it.
4. **Detailed Technical Walkthrough (File-by-File)**:
   - Explain **in detail** what every modified file and function does, why the implementation works, and what edge cases were handled (e.g., Android API level differences, thread safety on `Dispatchers.IO`, Compose recomposition behavior, or `.hnmod` bundle validation rules).
5. **Human Review & Verification Evidence**:
   - State what you (or the agent's test runner) actually tested and verified before submitting—such as `./gradlew assembleDebug`, unit/script runs, device testing, or UI checks.

### Copy-Paste Disclosure Template (Include in Your PR Description)

```markdown
## Authorship & AI Disclosure

- **Opened By**: [ ] Human User  |  [ ] Human + AI Pair  |  [ ] Autonomous AI Agent
- **AI Model(s) Used**: `<e.g., Gemini / Claude / GPT / None>`
- **Tool / Harness**: `<e.g., Android Studio, Cursor, CLI Agent, Web Chat>`
- **Why AI Was Used**:
  > Explain why AI was used for this change and summarize the prompt or instructions given.

### Detailed Explanation of Changes
- **`path/to/FileA.kt`**: Explain what changed, why it was needed, and how it interacts with existing state.
- **`path/to/FileB.kt`**: Explain the logic, edge cases handled, and any trade-offs made.

### Verification Performed
- [ ] Verified build / syntax checks
- [ ] Tested on Android device / emulator (specify Android version & ABI)
- [ ] Verified Material You Light, Dark, and AMOLED themes
```

---

## 3. Repository Architecture

```text
ArseIDE/
├── app/                                  # Android application module (com.goydevv.arseide)
├── benchmark/                            # Macrobenchmark & startup profiling
├── baselineprofile/                      # Baseline profile generator
├── core/
│   ├── main/                             # Primary IDE logic, Compose UI, Editor, Terminal, Git & Horizon Studio
│   │   └── src/main/
│   │       ├── assets/
│   │       │   ├── horizon/              # Bundled .hnmod templates, reference validator & Python porter/packager
│   │       │   ├── terminal/             # Ubuntu PRoot setup, init, auto_setup & project_runner scripts
│   │       │   └── textmate/             # TextMate grammars & base editor color schemes
│   │       └── java/com/goydevv/arseide/
│   │           ├── activities/           # MainActivity, MainContent, AutoSetupDialog & Terminal Activity
│   │           ├── components/           # Material You navigation bar, cards & live GitHub ChangelogDialog
│   │           ├── drawer/               # File Tree drawer & AddProjectSheet
│   │           ├── editor/               # Rosemoe Sora Editor wrapper, TextMate & ThemeManager
│   │           ├── git/                  # JGit client & GitHub CLI integration
│   │           ├── horizon/              # .hnmod Validator, Packager, Porter & Converter Sheet
│   │           ├── projects/             # ProjectsBrowser, CreateProjectDialog & ProjectScaffolder
│   │           ├── runner/               # Background ProjectRunner & output sheet
│   │           ├── settings/             # App preferences & Settings screens
│   │           ├── terminal/             # AutoSetup, SessionService & PRoot terminal backend
│   │           └── theme/                # XedTheme, Material You dynamic color normalization & presets
│   ├── components/                       # Shared UI primitives
│   ├── resources/                        # String, drawable & localization resources
│   ├── terminal-view/                    # Terminal emulator view
│   └── termux-shared/                    # Termux shared session utilities
└── tools/horizon/                        # Standalone CLI scripts for .hnmod porting and validation
```

---

## 4. Local Development & Build Setup

1. **Fork and Clone**:
   ```bash
   git clone https://github.com/<your-username>/ArseIDE.git
   cd ArseIDE
   ```
2. **Create a Feature Branch**:
   ```bash
   git checkout -b feat/your-feature-name
   ```
3. **Code Formatting & Style**:
   - Kotlin code follows standard Kotlin / Compose conventions (4-space indentation).
   - You can format Kotlin sources with `./gradlew ktfmtFormat` if needed.
   - Always use `MaterialTheme.colorScheme` tokens instead of hardcoded hex colors in Compose UI so **Material You (Dynamic Color)**, **Light**, **Dark**, and **AMOLED** modes stay cohesive.
4. **Test Horizon `.hnmod` Scripts (if modifying `core/main/src/main/assets/horizon/tools/`)**:
   ```bash
   python3 tools/horizon/validate_and_pack_hnmod.py core/main/src/main/assets/horizon/templates/01-gameplay-veinminer-hnmod
   ```

---

## 5. Pull Request Checklist & Template

Before submitting your PR to [`https://github.com/GoyDevv/ArseIDE`](https://github.com/GoyDevv/ArseIDE):

- [ ] Does your PR description include the completed **Authorship & AI Disclosure** block (including whether it was opened by an AI or user, which model was used, and a detailed explanation)?
- [ ] If you touched UI or themes, did you verify contrast in **Material You Dynamic Color**, **Dark**, **Light**, and **AMOLED** modes?
- [ ] If you touched terminal or sandbox scripts, does the fallback behavior work cleanly when first-launch Auto-Setup was skipped?
- [ ] Are all repository links pointing to `https://github.com/GoyDevv/ArseIDE`?

Thank you for helping build **Arse IDE**!
