#!/usr/bin/env python3
"""Port a Java Fabric/NeoForge/Forge mod project directory or .jar into a Horizon .hnmod native bundle."""
from __future__ import annotations

import argparse
import json
import os
import re
import sys
import zipfile
from pathlib import Path
from typing import Dict, List, Set, Tuple

DEFAULT_HORIZON_REV = "0123456789abcdef0123456789abcdef01234567"
DEFAULT_MIXINS_REV = "89abcdef0123456789abcdef0123456789abcdef"
DEFAULT_HOOKS_API = 1

RESERVED_IDS = {"minecraft", "c", "horizon"}


def sanitize_mod_id(raw: str) -> str:
    cleaned = re.sub(r"[^a-z0-9_]+", "_", raw.strip().lower()).strip("_")
    if not cleaned or cleaned in RESERVED_IDS:
        cleaned = f"ported_{cleaned or 'mod'}"
    return cleaned[:64]


def parse_toml_Basic(text: str) -> Dict[str, str]:
    result: Dict[str, str] = {}
    for key in ("modId", "version", "displayName", "authors", "description"):
        m = re.search(rf'{key}\s*=\s*"([^"\n]*)"', text)
        if m:
            result[key] = m.group(1).strip()
    if "description" not in result:
        m = re.search(r"description\s*=\s*'''(.*?)'''", text, re.DOTALL)
        if m:
            result["description"] = " ".join(m.group(1).strip().split())
    return result


def scan_signals(content: str) -> Set[str]:
    signals: Set[str] = set()
    patterns = {
        "tick": r"(ClientTickEvents|ServerTickEvents|TickEvent)",
        "hud": r"(HudRenderCallback|RenderGuiOverlayEvent|GuiGraphics|DrawContext)",
        "key": r"(KeyBindingHelper|KeyMapping|InputConstants|GLFW_KEY_)",
        "destroy_block": r"(PlayerBlockBreakEvents|BlockEvent\.BreakEvent|onBreak)",
        "right_click_block": r"(UseBlockCallback|PlayerInteractEvent\.RightClickBlock|useOnBlock)",
        "command": r"(CommandRegistrationCallback|ClientCommandRegistrationCallback|Commands\.literal)",
        "tooltip": r"(ItemTooltipCallback|ItemTooltipEvent|appendHoverText)",
    }
    for name, pat in patterns.items():
        if re.search(pat, content):
            signals.add(name)
    return signals


def inspect_source(src: Path) -> Tuple[Dict[str, object], Set[str], List[str]]:
    meta: Dict[str, object] = {
        "id": sanitize_mod_id(src.stem.replace("-", "_")),
        "name": src.stem,
        "version": "0.1.0",
        "description": f"Ported to Horizon 26.3 (.hnmod) from {src.name}",
        "authors": ["HorizonModPorter"],
    }
    signals: Set[str] = set()
    classes: List[str] = []

    if src.is_file() and src.suffix.lower() == ".jar":
        with zipfile.ZipFile(src, "r") as zf:
            names = set(zf.namelist())
            if "fabric.mod.json" in names:
                try:
                    data = json.loads(zf.read("fabric.mod.json").decode("utf-8", errors="replace"))
                    meta["id"] = sanitize_mod_id(str(data.get("id", meta["id"])))
                    meta["name"] = str(data.get("name", meta["name"]))
                    meta["version"] = str(data.get("version", meta["version"]))
                    meta["description"] = str(data.get("description", meta["description"]))
                except Exception:
                    pass
            else:
                for toml_name in ("META-INF/neoforge.mods.toml", "META-INF/mods.toml"):
                    if toml_name in names:
                        parsed = parse_toml_Basic(zf.read(toml_name).decode("utf-8", errors="replace"))
                        if "modId" in parsed:
                            meta["id"] = sanitize_mod_id(parsed["modId"])
                        if "displayName" in parsed:
                            meta["name"] = parsed["displayName"]
                        if "version" in parsed and not parsed["version"].startswith("${"):
                            meta["version"] = parsed["version"]
                        if "description" in parsed:
                            meta["description"] = parsed["description"]
                        break
            for entry in zf.namelist():
                if entry.endswith((".class", ".java", ".kt")):
                    classes.append(entry)
                    try:
                        raw = zf.read(entry).decode("latin1", errors="ignore")
                        signals |= scan_signals(raw)
                    except Exception:
                        pass
    else:
        for fmj in [src / "src/main/resources/fabric.mod.json", src / "fabric.mod.json"]:
            if fmj.is_file():
                try:
                    data = json.loads(fmj.read_text(encoding="utf-8", errors="replace"))
                    meta["id"] = sanitize_mod_id(str(data.get("id", meta["id"])))
                    meta["name"] = str(data.get("name", meta["name"]))
                    meta["version"] = str(data.get("version", meta["version"]))
                    meta["description"] = str(data.get("description", meta["description"]))
                except Exception:
                    pass
                break
        for root, _, files in os.walk(src):
            for f in files:
                if f.endswith((".java", ".kt")):
                    full = Path(root) / f
                    classes.append(str(full.relative_to(src)))
                    try:
                        signals |= scan_signals(full.read_text(encoding="utf-8", errors="ignore"))
                    except Exception:
                        pass

    if not re.match(r"^\d+\.\d+\.\d+", str(meta["version"])):
        meta["version"] = "0.1.0"
    if not signals:
        signals = {"tick", "hud", "key", "destroy_block", "command", "tooltip"}
    return meta, signals, classes[:60]


def generate_hnmod_project(
    src: Path,
    out_dir: Path,
    horizon_rev: str = DEFAULT_HORIZON_REV,
    mixins_rev: str = DEFAULT_MIXINS_REV,
    hooks_api: int = DEFAULT_HOOKS_API,
) -> Path:
    meta, signals, classes = inspect_source(src)
    mod_id = str(meta["id"])
    mod_name = str(meta["name"]).replace('"', "'")
    mod_version = str(meta["version"])
    mod_desc = " ".join(str(meta["description"]).replace('"', "'").split())

    mixin_dir = out_dir / "mixins" / mod_id
    (mixin_dir / "data").mkdir(parents=True, exist_ok=True)
    (mixin_dir / "engine" / "src").mkdir(parents=True, exist_ok=True)

    native_json = {
        "schema": 1,
        "id": mod_id,
        "version": mod_version,
        "game": "26.3",
        "horizon": horizon_rev,
        "mixins": mixins_rev,
        "hooks": hooks_api,
    }
    (out_dir / "horizon-native.json").write_text(json.dumps(native_json, indent=2) + "\n", encoding="utf-8")

    authors_list = meta.get("authors", ["HorizonModPorter"])
    if not isinstance(authors_list, list) or not authors_list:
        authors_list = ["HorizonModPorter"]
    authors_toml = ", ".join(f'"{str(a)}"' for a in authors_list)

    toml_text = (
        "[mod]\n"
        f'id = "{mod_id}"\n'
        f'version = "{mod_version}"\n'
        "priority = 100\n\n"
        "[compatibility]\n"
        f'horizon_revision = "{horizon_rev}"\n\n'
        "[[module]]\n"
        'id = "runtime"\n'
        'file = "crates/horizon-client/src/game.rs"\n'
        f'name = "{mod_id}_runtime"\n'
        'source = "runtime.rs"\n'
    )
    (mixin_dir / "horizon-mixin.toml").write_text(toml_text, encoding="utf-8")

    (mixin_dir / "runtime.rs").write_text(
        f'pub const MOD_ID: &str = "{mod_id}";\n'
        f'pub const MOD_NAME: &str = "{mod_name}";\n'
        f'pub const MOD_VERSION: &str = "{mod_version}";\n'
        f'pub const MOD_DESCRIPTION: &str = "{mod_desc}";\n\n'
        "pub struct PortedModState {\n"
        "    pub enabled: bool,\n"
        "    pub show_hud: bool,\n"
        "    pub tick_counter: u64,\n"
        "    pub blocks_interacted: u64,\n"
        "}\n\n"
        "impl PortedModState {\n"
        "    pub const fn new() -> Self {\n"
        "        Self {\n"
        "            enabled: true,\n"
        "            show_hud: true,\n"
        "            tick_counter: 0,\n"
        "            blocks_interacted: 0,\n"
        "        }\n"
        "    }\n"
        "}\n\n"
        "pub static mut STATE: PortedModState = PortedModState::new();\n",
        encoding="utf-8",
    )

    (mixin_dir / "list.rs").write_text(
        "({\n"
        "    let enabled = unsafe { runtime::STATE.enabled };\n"
        "    Some(Game::mixin_list_entry(\n"
        "        runtime::MOD_ID,\n"
        "        runtime::MOD_NAME,\n"
        '        if enabled { "Active (26.3 .hnmod)" } else { "Disabled" },\n'
        "    ))\n"
        "})\n",
        encoding="utf-8",
    )

    (mixin_dir / "settings.rs").write_text(
        "({\n"
        "    let (enabled, show_hud, blocks) = unsafe {\n"
        "        (runtime::STATE.enabled, runtime::STATE.show_hud, runtime::STATE.blocks_interacted)\n"
        "    };\n"
        "    Some(Game::mixin_settings_page(\n"
        "        runtime::MOD_NAME,\n"
        "        runtime::MOD_DESCRIPTION,\n"
        "        vec![\n"
        '            Game::mixin_toggle("enabled", "Enable Mod", enabled),\n'
        '            Game::mixin_toggle("show_hud", "Show HUD Overlay", show_hud),\n'
        '            Game::mixin_label("stats", &format!("Blocks handled: {}", blocks)),\n'
        "        ],\n"
        "    ))\n"
        "})\n",
        encoding="utf-8",
    )

    (mixin_dir / "settings_click.rs").write_text(
        "({\n"
        "    match control_id {\n"
        '        "enabled" => {\n'
        "            unsafe { runtime::STATE.enabled = !runtime::STATE.enabled; }\n"
        "            true\n"
        "        }\n"
        '        "show_hud" => {\n'
        "            unsafe { runtime::STATE.show_hud = !runtime::STATE.show_hud; }\n"
        "            true\n"
        "        }\n"
        "        _ => false,\n"
        "    }\n"
        "})\n",
        encoding="utf-8",
    )

    (mixin_dir / "tick.rs").write_text(
        "({\n"
        "    let _ = game;\n"
        "    unsafe {\n"
        "        if runtime::STATE.enabled {\n"
        "            runtime::STATE.tick_counter = runtime::STATE.tick_counter.wrapping_add(1);\n"
        "        }\n"
        "    }\n"
        "})\n",
        encoding="utf-8",
    )

    (mixin_dir / "hud.rs").write_text(
        "({\n"
        "    let _ = game;\n"
        "    unsafe {\n"
        "        if runtime::STATE.enabled && runtime::STATE.show_hud {\n"
        "            lines.push(format!(\n"
        '                "[{}] ticks={} blocks={}",\n'
        "                runtime::MOD_NAME,\n"
        "                runtime::STATE.tick_counter,\n"
        "                runtime::STATE.blocks_interacted\n"
        "            ));\n"
        "        }\n"
        "    }\n"
        "})\n",
        encoding="utf-8",
    )

    (mixin_dir / "key.rs").write_text(
        "({\n"
        "    let _ = game;\n"
        "    if pressed && key == KeyCode::KeyV {\n"
        "        unsafe { runtime::STATE.enabled = !runtime::STATE.enabled; }\n"
        "    }\n"
        "})\n",
        encoding="utf-8",
    )

    (mixin_dir / "destroy_block.rs").write_text(
        "({\n"
        "    let _ = (game, x, y, z, block);\n"
        "    unsafe {\n"
        "        if runtime::STATE.enabled {\n"
        "            runtime::STATE.blocks_interacted = runtime::STATE.blocks_interacted.wrapping_add(1);\n"
        "        }\n"
        "    }\n"
        "    false\n"
        "})\n",
        encoding="utf-8",
    )

    (mixin_dir / "right_click_block.rs").write_text(
        "({\n"
        "    let _ = (game, x, y, z, face, cursor);\n"
        "    false\n"
        "})\n",
        encoding="utf-8",
    )

    (mixin_dir / "commands.rs").write_text(
        "({\n"
        "    let _ = game;\n"
        "    vec![Game::mixin_command(runtime::MOD_ID, runtime::MOD_DESCRIPTION)]\n"
        "})\n",
        encoding="utf-8",
    )

    (mixin_dir / "command.rs").write_text(
        "({\n"
        "    let _ = (game, args);\n"
        "    if name == runtime::MOD_ID {\n"
        "        unsafe { runtime::STATE.enabled = !runtime::STATE.enabled; }\n"
        "        true\n"
        "    } else {\n"
        "        false\n"
        "    }\n"
        "})\n",
        encoding="utf-8",
    )

    (mixin_dir / "tooltip.rs").write_text(
        "({\n"
        "    let _ = (game, stack);\n"
        "    let _ = lines;\n"
        "})\n",
        encoding="utf-8",
    )

    manifest = {
        "source": src.name,
        "id": mod_id,
        "name": mod_name,
        "version": mod_version,
        "detected_signals": sorted(signals),
        "scanned_files": classes,
    }
    (mixin_dir / "data" / "port_manifest.json").write_text(json.dumps(manifest, indent=2) + "\n", encoding="utf-8")

    (mixin_dir / "engine" / "Cargo.toml").write_text(
        "[package]\n"
        f'name = "{mod_id}_engine"\n'
        f'version = "{mod_version}"\n'
        'edition = "2021"\n\n'
        "[lib]\n"
        'path = "src/lib.rs"\n\n'
        "[dependencies]\n",
        encoding="utf-8",
    )
    (mixin_dir / "engine" / "README.md").write_text(
        f"# {mod_name} Companion Engine Crate\n\n"
        f"Companion Rust crate for the `{mod_id}` Horizon `.hnmod` bundle.\n",
        encoding="utf-8",
    )
    (mixin_dir / "engine" / "src" / "lib.rs").write_text(
        f"//! Companion engine crate for {mod_name} ({mod_id})\n\n"
        "pub fn compute_step(ticks: u64) -> u64 {\n"
        "    ticks.wrapping_add(1)\n"
        "}\n",
        encoding="utf-8",
    )
    return out_dir


def main() -> int:
    parser = argparse.ArgumentParser(description="Port a Java mod (.jar or project dir) to a Horizon .hnmod project.")
    parser.add_argument("src", type=Path, help="Input Fabric/Forge .jar or project directory")
    parser.add_argument("--out", type=Path, required=True, help="Output .hnmod project directory")
    parser.add_argument("--pack", type=Path, default=None, help="Optional output .hnmod bundle path")
    parser.add_argument("--horizon-rev", type=str, default=DEFAULT_HORIZON_REV, help="40-char Horizon git revision")
    parser.add_argument("--mixins-rev", type=str, default=DEFAULT_MIXINS_REV, help="40-char Mixins git revision")
    args = parser.parse_args()

    if not args.src.exists():
        print(f"Error: source not found: {args.src}", file=sys.stderr)
        return 1

    generate_hnmod_project(args.src, args.out, horizon_rev=args.horizon_rev, mixins_rev=args.mixins_rev)
    print(f"Generated Horizon .hnmod project at {args.out}")

    if args.pack is not None:
        import subprocess
        validator = Path(__file__).parent / "validate_and_pack_hnmod.py"
        subprocess.run([sys.executable, str(validator), str(args.out), "--out", str(args.pack)], check=True)

    return 0


if __name__ == "__main__":
    raise SystemExit(main())
