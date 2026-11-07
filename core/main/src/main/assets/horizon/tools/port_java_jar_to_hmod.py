#!/usr/bin/env python3
"""
port_java_jar_to_hmod.py
========================
Automated + AI-assisted Java (Fabric / NeoForge / Forge) Mod -> Horizon `.hmod` Porter.

Supports BOTH:
  - Compiled `.jar` files (extracts metadata, assets, data, blockstates, item models)
  - Open-source Java/Fabric/NeoForge project directories (additionally parses Java
    `Registry.register(BuiltInRegistries.BLOCK/ITEM, ...)` calls, block/item properties,
    and translates client HUD / keybind / screen / raycast hooks or server events into
    Rust WASM `src/lib.rs` targeting `horizon-mod-sdk`).

Usage:
    python3 tools/port_java_jar_to_hmod.py <path/to/mod.jar_or_dir> <output-dir> [--package]
"""

from __future__ import annotations

import argparse
import json
import os
import re
import shutil
import subprocess
import sys
import tempfile
import zipfile
from pathlib import Path
from typing import Any

RESERVED_IDS = {"minecraft", "c", "horizon"}

PLURAL_DATA_RENAMES = {
    "recipes": "recipe",
    "loot_tables": "loot_table",
    "structures": "structure",
    "advancements": "advancement",
    "predicates": "predicate",
    "item_modifiers": "item_modifier",
}

PLURAL_TAG_RENAMES = {
    "blocks": "block",
    "items": "item",
    "entity_types": "entity_type",
    "fluids": "fluid",
    "game_events": "game_event",
}

VANILLA_ID_RENAMES = {
    "minecraft:chain": "minecraft:iron_chain",
}

SOUND_MAP = {
    "WOOD": "wood",
    "STONE": "stone",
    "METAL": "metal",
    "IRON": "metal",
    "COPPER": "copper",
    "GLASS": "glass",
    "GRASS": "grass",
    "GRAVEL": "gravel",
    "SAND": "sand",
    "SNOW": "snow",
    "WOOL": "wool",
    "SLIME_BLOCK": "slime",
    "HONEY_BLOCK": "honey",
    "ANCIENT_DEBRIS": "netherite",
    "NETHERITE_BLOCK": "netherite",
    "AMETHYST": "amethyst",
    "DEEPSLATE": "deepslate",
    "TUFF": "tuff",
}


def sanitize_mod_id(raw: str) -> str:
    cleaned = re.sub(r"[^a-z0-9_]", "_", raw.strip().lower())
    cleaned = re.sub(r"_+", "_", cleaned).strip("_")
    if not cleaned or cleaned in RESERVED_IDS:
        cleaned = f"ported_{cleaned or 'mod'}"
    return cleaned[:64]


def parse_fabric_metadata(root: Path) -> dict[str, Any] | None:
    candidates = [
        root / "fabric.mod.json",
        root / "src" / "main" / "resources" / "fabric.mod.json",
    ]
    f = next((c for c in candidates if c.is_file()), None)
    if not f:
        return None
    data = json.loads(f.read_text(encoding="utf-8", errors="replace"))
    authors: list[str] = []
    for entry in data.get("authors", []):
        if isinstance(entry, str):
            authors.append(entry)
        elif isinstance(entry, dict) and "name" in entry:
            authors.append(str(entry["name"]))
    version = str(data.get("version", "0.1.0"))
    if version.startswith("${"):
        version = "0.1.0"
    return {
        "id": sanitize_mod_id(str(data.get("id", "ported_mod"))),
        "name": str(data.get("name", data.get("id", "Ported Mod"))),
        "version": version,
        "description": str(data.get("description", "")),
        "authors": authors or ["Unknown"],
    }


def parse_toml_metadata(root: Path) -> dict[str, Any] | None:
    for rel in (
        "META-INF/neoforge.mods.toml",
        "META-INF/mods.toml",
        "src/main/resources/META-INF/neoforge.mods.toml",
        "src/main/resources/META-INF/mods.toml",
    ):
        f = root / rel
        if f.is_file():
            text = f.read_text(encoding="utf-8", errors="replace")
            mod_id = re.search(r'modId\s*=\s*"([^"]+)"', text)
            name = re.search(r'displayName\s*=\s*"([^"]+)"', text)
            version = re.search(r'version\s*=\s*"([^"]+)"', text)
            authors = re.search(r'authors\s*=\s*"([^"]+)"', text)
            desc = re.search(r"description\s*=\s*'''(.*?)'''", text, re.S)
            ver_str = version.group(1) if version else "0.1.0"
            if "${" in ver_str:
                ver_str = "0.1.0"
            return {
                "id": sanitize_mod_id(mod_id.group(1) if mod_id else "ported_mod"),
                "name": name.group(1) if name else "Ported Mod",
                "version": ver_str,
                "description": desc.group(1).strip() if desc else "",
                "authors": [a.strip() for a in (authors.group(1).split(",") if authors else ["Unknown"])],
            }
    return None


def upgrade_ingredient_value(val: Any) -> Any:
    if isinstance(val, str):
        return VANILLA_ID_RENAMES.get(val, val)
    if isinstance(val, dict):
        if "item" in val and len(val) == 1:
            item_id = str(val["item"])
            return VANILLA_ID_RENAMES.get(item_id, item_id)
        if "tag" in val and len(val) == 1:
            return f"#{val['tag']}"
        return {k: upgrade_ingredient_value(v) for k, v in val.items()}
    if isinstance(val, list):
        return [upgrade_ingredient_value(x) for x in val]
    return val


def upgrade_recipe_json(data: dict[str, Any]) -> dict[str, Any]:
    out = dict(data)
    if "key" in out and isinstance(out["key"], dict):
        out["key"] = {k: upgrade_ingredient_value(v) for k, v in out["key"].items()}
    if "ingredients" in out and isinstance(out["ingredients"], list):
        out["ingredients"] = [upgrade_ingredient_value(v) for v in out["ingredients"]]
    if "ingredient" in out:
        out["ingredient"] = upgrade_ingredient_value(out["ingredient"])
    if "base" in out:
        out["base"] = upgrade_ingredient_value(out["base"])
    if "addition" in out:
        out["addition"] = upgrade_ingredient_value(out["addition"])
    if "template" in out:
        out["template"] = upgrade_ingredient_value(out["template"])
    if "result" in out:
        res = out["result"]
        if isinstance(res, str):
            out["result"] = {"id": VANILLA_ID_RENAMES.get(res, res), "count": int(out.pop("count", 1))}
        elif isinstance(res, dict):
            if "item" in res and "id" not in res:
                res = dict(res)
                res["id"] = res.pop("item")
            if "id" in res:
                res["id"] = VANILLA_ID_RENAMES.get(str(res["id"]), str(res["id"]))
            out["result"] = res
    return out


def upgrade_loot_node(node: Any) -> Any:
    if isinstance(node, list):
        return [upgrade_loot_node(x) for x in node]
    if isinstance(node, dict):
        out: dict[str, Any] = {}
        for k, v in node.items():
            nk = k
            if k == "functions":
                nk = "modifier"
            elif k == "function":
                nk = "type"
            elif k == "conditions" and isinstance(v, list):
                upgraded_list = [upgrade_loot_node(x) for x in v]
                if len(upgraded_list) == 1:
                    out["condition"] = upgraded_list[0]
                elif len(upgraded_list) > 1:
                    out["condition"] = {"condition": "minecraft:all_of", "terms": upgraded_list}
                continue
            out[nk] = upgrade_loot_node(v)
        if out.get("name") in VANILLA_ID_RENAMES:
            out["name"] = VANILLA_ID_RENAMES[out["name"]]
        return out
    return node


def remap_data_relpath(rel: Path) -> Path:
    parts = [p.lower() for p in rel.parts]
    if len(parts) >= 2:
        category = parts[1]
        if category in PLURAL_DATA_RENAMES:
            parts[1] = PLURAL_DATA_RENAMES[category]
        elif category == "tags" and len(parts) >= 3:
            sub = parts[2]
            if sub in PLURAL_TAG_RENAMES:
                parts[2] = PLURAL_TAG_RENAMES[sub]
    return Path(*parts)


def infer_block_template(name: str, blockstate_json: dict[str, Any], java_info: dict[str, Any] | None = None) -> str:
    if java_info and java_info.get("template"):
        return str(java_info["template"])
    variants = blockstate_json.get("variants", {})
    keys_joined = " ".join(variants.keys())
    if "multipart" in blockstate_json and any(tok in name for tok in ("pipe", "cable", "duct", "conduit", "wire")):
        return "minecraft:chorus_plant"
    if "half=" in keys_joined and "shape=" in keys_joined and "facing=" in keys_joined:
        return "minecraft:oak_stairs"
    if "type=bottom" in keys_joined or "type=top" in keys_joined or name.endswith("_slab"):
        return "minecraft:oak_slab"
    if "half=lower" in keys_joined and "hinge=" in keys_joined:
        return "minecraft:oak_door"
    if "open=" in keys_joined and "half=" in keys_joined and "facing=" in keys_joined:
        return "minecraft:oak_trapdoor"
    if "axis=" in keys_joined or name.endswith(("_log", "_pillar", "_stem", "_wood")):
        return "minecraft:oak_log"
    if "lit=" in keys_joined and "facing=" in keys_joined:
        return "minecraft:furnace"
    if name.endswith("_fence"):
        return "minecraft:oak_fence"
    if name.endswith("_wall"):
        return "minecraft:cobblestone_wall"
    if name.endswith(("_glass", "_pane")) or "glass" in name:
        return "minecraft:glass"
    if name.endswith(("_planks", "_wood")):
        return "minecraft:oak_planks"
    return "minecraft:stone"


def parse_java_sources(root: Path) -> dict[str, Any]:
    """
    Scan Java source files (if porting a project folder or sources jar) to extract:
      - Registered blocks + Properties (strength, hardness, explosion_resistance, lightLevel, requiresCorrectToolForDrops, sound, Block class template)
      - Registered items + Item.Properties / 26.3 components (maxDamage, stacksTo, rarity, craftRemainder, food)
      - Gameplay hooks (HUD, keybindings, custom screens, raycast/tooltips, server block_break, block_use, player_join, commands)
    """
    blocks: dict[str, dict[str, Any]] = {}
    items: dict[str, dict[str, Any]] = {}
    features = {
        "has_hud": False,
        "has_keybind": False,
        "has_screen": False,
        "has_raycast": False,
        "has_storage": False,
        "has_server_block_break": False,
        "has_server_block_use": False,
        "has_server_player_join": False,
        "has_server_commands": False,
        "keybinds": [],
        "commands": [],
        "hud_labels": [],
    }

    java_files = list(root.rglob("*.java"))
    if not java_files:
        return {"blocks": blocks, "items": items, "features": features}

    all_java_text = []
    for jf in java_files:
        try:
            text = jf.read_text(encoding="utf-8", errors="replace")
            all_java_text.append(text)
        except Exception:
            continue

    combined = "\n".join(all_java_text)

    # 1. Detect Block registrations and properties
    # Matches patterns like: register("ruby_block", new StairBlock(...) / new Block(AbstractBlock.Settings.create().strength(4.0f, 6.0f)...))
    reg_pattern = re.compile(
        r"""(?:Registry\.register\s*\(\s*(?:BuiltInRegistries|Registries)\.BLOCK[^,]*,|registerBlock\s*\(|BLOCKS\.register\s*\()\s*(?:new\s+ResourceLocation\([^,]+,\s*"([a-z0-9_]+)"\)|Identifier\.of\([^,]+,\s*"([a-z0-9_]+)"\)|"([a-z0-9_]+)")\s*,\s*([^;\n]+(?:\n[^;\n]+){0,5});""",
        re.M,
    )
    for m in reg_pattern.finditer(combined):
        bname = (m.group(1) or m.group(2) or m.group(3) or "").strip().lower()
        expr = m.group(4)
        if not bname:
            continue
        info: dict[str, Any] = {}
        if "StairBlock" in expr or "StairsBlock" in expr:
            info["template"] = "minecraft:oak_stairs"
        elif "SlabBlock" in expr:
            info["template"] = "minecraft:oak_slab"
        elif "DoorBlock" in expr:
            info["template"] = "minecraft:oak_door"
        elif "TrapDoorBlock" in expr or "TrapdoorBlock" in expr:
            info["template"] = "minecraft:oak_trapdoor"
        elif "FenceBlock" in expr:
            info["template"] = "minecraft:oak_fence"
        elif "WallBlock" in expr:
            info["template"] = "minecraft:cobblestone_wall"
        elif "RotatedPillarBlock" in expr or "PillarBlock" in expr:
            info["template"] = "minecraft:oak_log"
        elif "FurnaceBlock" in expr or "AbstractFurnaceBlock" in expr:
            info["template"] = "minecraft:furnace"
        elif "PipeBlock" in expr or "ChorusPlantBlock" in expr:
            info["template"] = "minecraft:chorus_plant"

        sm = re.search(r"\.strength\s*\(\s*([0-9.]+)\s*[fF]?(?:\s*,\s*([0-9.]+)\s*[fF]?)?\s*\)", expr)
        if sm:
            h = float(sm.group(1))
            r = float(sm.group(2)) if sm.group(2) else h
            info["hardness"] = h
            info["explosion_resistance"] = r

        lm = re.search(r"(?:luminance|lightLevel)\s*\([^)]*?([0-9]+)\s*\)", expr)
        if lm:
            info["light"] = min(15, max(0, int(lm.group(1))))

        if "requiresCorrectToolForDrops" in expr or "requiresTool" in expr:
            info["requires_tool"] = True

        snd = re.search(r"\.(?:sounds|sound)\s*\(\s*(?:BlockSoundGroup|SoundType)\.([A-Z_]+)\s*\)", expr)
        if snd and snd.group(1) in SOUND_MAP:
            info["sounds"] = SOUND_MAP[snd.group(1)]

        blocks[bname] = info

    # 2. Detect Item registrations and properties
    item_reg_pattern = re.compile(
        r"""(?:Registry\.register\s*\(\s*(?:BuiltInRegistries|Registries)\.ITEM[^,]*,|registerItem\s*\(|ITEMS\.register\s*\()\s*(?:new\s+ResourceLocation\([^,]+,\s*"([a-z0-9_]+)"\)|Identifier\.of\([^,]+,\s*"([a-z0-9_]+)"\)|"([a-z0-9_]+)")\s*,\s*([^;\n]+(?:\n[^;\n]+){0,5});""",
        re.M,
    )
    for m in item_reg_pattern.finditer(combined):
        iname = (m.group(1) or m.group(2) or m.group(3) or "").strip().lower()
        expr = m.group(4)
        if not iname:
            continue
        iinfo: dict[str, Any] = {}
        stk = re.search(r"\.(?:stacksTo|maxCount)\s*\(\s*([0-9]+)\s*\)", expr)
        if stk:
            iinfo["max_stack_size"] = min(64, max(1, int(stk.group(1))))
        dmg = re.search(r"\.(?:durability|maxDamage)\s*\(\s*([0-9]+)\s*\)", expr)
        if dmg:
            iinfo["max_damage"] = int(dmg.group(1))
            iinfo["max_stack_size"] = 1
        rar = re.search(r"\.rarity\s*\(\s*Rarity\.([A-Z_]+)\s*\)", expr)
        if rar:
            iinfo["rarity"] = rar.group(1).lower()
        if "fireResistant" in expr:
            iinfo["fire_resistant"] = True
        food_m = re.search(r"nutrition\s*\(\s*([0-9]+)\s*\).*?saturation(?:Modifier)?\s*\(\s*([0-9.]+)\s*[fF]?\s*\)", expr, re.S)
        if food_m:
            iinfo["food"] = {
                "nutrition": int(food_m.group(1)),
                "saturation": float(food_m.group(2)),
                "can_always_eat": "alwaysEdible" in expr or "alwaysEat" in expr,
            }
        items[iname] = iinfo

    # 3. Detect Client / Server Gameplay Hooks for Rust WASM translation
    if any(k in combined for k in ("HudRenderCallback", "InGameHud", "GuiGraphics", "DrawContext")):
        features["has_hud"] = True
    if any(k in combined for k in ("KeyBindingHelper", "KeyMapping", "GLFW.GLFW_KEY_")):
        features["has_keybind"] = True
        for km in re.finditer(r'new\s+(?:KeyBinding|KeyMapping)\s*\(\s*"([^"]+)"\s*,[^,]+,\s*GLFW\.GLFW_KEY_([A-Z0-9_]+)', combined):
            features["keybinds"].append((km.group(1), f"Key{km.group(2).capitalize()}"))
    if any(k in combined for k in ("Screen", "HandledScreen", "AbstractContainerScreen")):
        features["has_screen"] = True
    if any(k in combined for k in ("crosshairTarget", "hitResult", "BlockHitResult", "raycast")):
        features["has_raycast"] = True
    if any(k in combined for k in ("PlayerBlockBreakEvents", "BreakEvent", "onBlockBreak")):
        features["has_server_block_break"] = True
    if any(k in combined for k in ("UseBlockCallback", "RightClickBlock", "useOnBlock")):
        features["has_server_block_use"] = True
    if any(k in combined for k in ("ServerPlayConnectionEvents.JOIN", "PlayerLoggedInEvent", "onPlayerJoin")):
        features["has_server_player_join"] = True
    if any(k in combined for k in ("CommandRegistrationCallback", "RegisterCommandsEvent", "CommandManager.literal")):
        features["has_server_commands"] = True
        for cm in re.finditer(r'literal\s*\(\s*"([a-z0-9_-]+)"\s*\)', combined):
            cmd = cm.group(1)
            if cmd not in features["commands"]:
                features["commands"].append(cmd)

    return {"blocks": blocks, "items": items, "features": features}


def generate_rust_wasm_scaffold(
    out_root: Path,
    mod_id: str,
    meta: dict[str, Any],
    features: dict[str, Any],
) -> tuple[str, list[str], list[str]]:
    """
    If Java gameplay hooks were detected, scaffold Cargo.toml and src/lib.rs targeting horizon-mod-sdk.
    Returns (api_version, capabilities, experimental).
    """
    has_server = (
        features["has_server_block_break"]
        or features["has_server_block_use"]
        or features["has_server_player_join"]
        or features["has_server_commands"]
    )
    has_client = (
        features["has_hud"]
        or features["has_keybind"]
        or features["has_screen"]
        or features["has_raycast"]
    )

    if not has_client and not has_server:
        return ("0.2", ["registries"], [])

    src_dir = out_root / "src"
    src_dir.mkdir(parents=True, exist_ok=True)
    crate_name = mod_id.replace("-", "_")

    cargo_toml = (
        "[package]\n"
        f'name = "{crate_name}"\n'
        f'version = "{meta["version"]}"\n'
        'edition = "2021"\n\n'
        "[lib]\n"
        'crate-type = ["cdylib", "rlib"]\n\n'
        "[dependencies]\n"
        'horizon-mod-sdk = { path = "../sdk/horizon-mod-sdk" }\n'
    )
    (out_root / "Cargo.toml").write_text(cargo_toml, encoding="utf-8")

    if has_server and not has_client:
        caps = ["registries", "events", "world", "players", "commands", "storage"]
        exp = ["world", "players"]
        cmds = features["commands"] or [mod_id]
        cmd_literals = ", ".join(f'"{c}"' for c in cmds[:4])
        lib_rs = f"""use horizon_mod_sdk::{{
    commands, export_server_mod, log, players, storage, world, BlockBreakEvent, BlockUseEvent,
    CommandContext, CommandResult, EventOutcome, PlayerJoinEvent, ServerMod,
}};

#[derive(Default)]
pub struct PortedServerMod;

impl ServerMod for PortedServerMod {{
    fn init(&mut self) -> Result<(), String> {{
        for cmd in [{cmd_literals}] {{
            let _ = commands::register(cmd, "Ported command from {meta['name']}", &[]);
        }}
        log::info("{meta['name']} server WASM initialized");
        Ok(())
    }}

    fn on_player_join(&mut self, event: PlayerJoinEvent) {{
        let _ = players::send_message(&event.player.name, "Welcome to {meta['name']}!");
    }}

    fn on_block_break(&mut self, event: BlockBreakEvent) -> EventOutcome {{
        log::debug(&format!("Block broken: {{}} at ({{}}, {{}}, {{}})", event.block_id, event.pos.x, event.pos.y, event.pos.z));
        EventOutcome::Continue
    }}

    fn on_block_use(&mut self, event: BlockUseEvent) -> EventOutcome {{
        if event.is_sneaking {{
            if let Ok(actual) = world::get_block_id(event.pos) {{
                let _ = players::send_action_bar(&event.player.name, &format!("Inspecting {{actual}}"));
            }}
        }}
        EventOutcome::Continue
    }}

    fn on_command(&mut self, ctx: CommandContext) -> CommandResult {{
        let _ = storage::set("last_command_sender", ctx.sender.name.as_bytes());
        CommandResult::Success(format!("[{meta['name']}] Executed /{{}}", ctx.command))
    }}
}}

export_server_mod!(PortedServerMod);
"""
        (src_dir / "lib.rs").write_text(lib_rs, encoding="utf-8")
        return ("0.3", caps, exp)

    # Client / Hybrid WASM Mod
    caps = ["registries", "hud", "render", "ui", "input", "storage"]
    exp = ["ui", "render", "input"]
    first_key = features["keybinds"][0][1] if features["keybinds"] else "KeyR"
    lib_rs = f"""use horizon_mod_sdk::{{
    export_mod, game, hud, input, log, render, storage, ui, Anchor, Action, InputEvent, KeybindId,
    Mod, RenderContext, ScreenDef, UiEvent, WidgetDef,
}};

pub struct PortedClientMod {{
    toggle_key: Option<KeybindId>,
    hud_enabled: bool,
}}

impl Default for PortedClientMod {{
    fn default() -> Self {{
        Self {{
            toggle_key: None,
            hud_enabled: true,
        }}
    }}
}}

impl Mod for PortedClientMod {{
    fn init(&mut self) -> Result<(), String> {{
        if let Some(saved) = storage::get("hud_enabled") {{
            self.hud_enabled = saved != b"0";
        }}
        self.toggle_key = input::register_keybind(
            "{mod_id}.toggle_hud",
            "Toggle {meta['name']} HUD",
            "{first_key}",
            "{meta['name']}",
        )
        .ok();
        log::info("{meta['name']} client WASM initialized");
        Ok(())
    }}

    fn tick(&mut self, _dt: f32) {{
        if !self.hud_enabled {{
            let _ = hud::remove(1);
            return;
        }}
        let label = if let Some(hit) = game::raycast(5.0) {{
            format!("[{meta['name']}] Target: {{}}", hit.block_id)
        }} else {{
            "[{meta['name']}] Active".to_string()
        }};
        let _ = hud::text(1, &label, Anchor::TopLeft, 8.0, 8.0, 1.0, 0xFF55FFAA);
    }}

    fn on_render(&mut self, _ctx: RenderContext) {{
        if self.hud_enabled {{
            let _ = render::draw_rect(6.0, 6.0, 180.0, 18.0, 0x66000000);
        }}
    }}

    fn on_input(&mut self, event: InputEvent) -> bool {{
        if event.action == Action::Press && self.toggle_key == Some(event.keybind) {{
            self.hud_enabled = !self.hud_enabled;
            let _ = storage::set("hud_enabled", if self.hud_enabled {{ b"1" }} else {{ b"0" }});
            return true;
        }}
        false
    }}

    fn on_ui_event(&mut self, _event: UiEvent) {{}}
}}

export_mod!(PortedClientMod);
"""
    (src_dir / "lib.rs").write_text(lib_rs, encoding="utf-8")
    return ("0.2", caps, exp)


def port_extracted_mod(src_root: Path, out_root: Path) -> dict[str, Any]:
    meta = parse_fabric_metadata(src_root) or parse_toml_metadata(src_root) or {
        "id": "ported_mod",
        "name": "Ported Mod",
        "version": "0.1.0",
        "description": "Ported to Horizon",
        "authors": ["Unknown"],
    }
    mod_id: str = meta["id"]

    if out_root.exists():
        shutil.rmtree(out_root)
    out_root.mkdir(parents=True, exist_ok=True)

    # Locate resource root (either jar root or src/main/resources)
    res_root = src_root / "src" / "main" / "resources" if (src_root / "src" / "main" / "resources").is_dir() else src_root

    java_analysis = parse_java_sources(src_root)
    java_blocks = java_analysis["blocks"]
    java_items = java_analysis["items"]
    java_features = java_analysis["features"]

    # 1. Copy and lowercase assets/<ns>/...
    extra_namespaces: set[str] = set()
    assets_src = res_root / "assets"
    if assets_src.is_dir():
        for ns_dir in assets_src.iterdir():
            if not ns_dir.is_dir():
                continue
            ns = ns_dir.name.lower()
            if ns != mod_id and ns != "minecraft":
                extra_namespaces.add(ns)
            for file in ns_dir.rglob("*"):
                if file.is_file():
                    rel = file.relative_to(assets_src)
                    lower_rel = Path(*[p.lower() for p in rel.parts])
                    dest = out_root / "assets" / lower_rel
                    dest.parent.mkdir(parents=True, exist_ok=True)
                    shutil.copy2(file, dest)

    # 2. Copy, rename plural->singular, and upgrade data/<ns>/...
    data_src = res_root / "data"
    if data_src.is_dir():
        for ns_dir in data_src.iterdir():
            if not ns_dir.is_dir():
                continue
            ns = ns_dir.name.lower()
            if ns != mod_id and ns != "minecraft":
                extra_namespaces.add(ns)
            for file in ns_dir.rglob("*"):
                if not file.is_file():
                    continue
                rel = remap_data_relpath(file.relative_to(data_src))
                dest = out_root / "data" / rel
                dest.parent.mkdir(parents=True, exist_ok=True)
                if file.suffix.lower() == ".json":
                    try:
                        jdata = json.loads(file.read_text(encoding="utf-8", errors="replace"))
                        if len(rel.parts) >= 2 and rel.parts[1] == "recipe" and isinstance(jdata, dict):
                            jdata = upgrade_recipe_json(jdata)
                        elif len(rel.parts) >= 2 and rel.parts[1] == "loot_table":
                            jdata = upgrade_loot_node(jdata)
                        dest.write_text(json.dumps(jdata, indent=2) + "\n", encoding="utf-8")
                        continue
                    except Exception:
                        pass
                shutil.copy2(file, dest)

    # 3. Synthesize data/<mod_id>/horizon/block/<name>.json from blockstates + Java Registry analysis
    ported_blocks: set[str] = set()
    blockstates_dir = out_root / "assets" / mod_id / "blockstates"
    horizon_block_dir = out_root / "data" / mod_id / "horizon" / "block"
    horizon_block_dir.mkdir(parents=True, exist_ok=True)

    all_block_names = set(java_blocks.keys())
    if blockstates_dir.is_dir():
        for bs_file in sorted(blockstates_dir.glob("*.json")):
            all_block_names.add(bs_file.stem.lower())

    for bname in sorted(all_block_names):
        bs_file = blockstates_dir / f"{bname}.json"
        bs_json: dict[str, Any] = {}
        if bs_file.is_file():
            try:
                bs_json = json.loads(bs_file.read_text(encoding="utf-8", errors="replace"))
            except Exception:
                bs_json = {}
        jinfo = java_blocks.get(bname, {})
        template = infer_block_template(bname, bs_json, jinfo)
        block_def: dict[str, Any] = {
            "template": template,
            "hardness": jinfo.get("hardness", 2.0),
            "explosion_resistance": jinfo.get("explosion_resistance", 3.0),
            "requires_tool": jinfo.get("requires_tool", template == "minecraft:stone"),
            "item": True,
        }
        if "light" in jinfo:
            block_def["light"] = jinfo["light"]
        if "sounds" in jinfo:
            block_def["sounds"] = jinfo["sounds"]
        (horizon_block_dir / f"{bname}.json").write_text(
            json.dumps(block_def, indent=2) + "\n", encoding="utf-8"
        )
        ported_blocks.add(bname)

    # 4. Synthesize data/<mod_id>/horizon/item/<name>.json & 26.3 assets/<mod_id>/items/<name>.json
    models_item_dir = out_root / "assets" / mod_id / "models" / "item"
    items_dispatch_dir = out_root / "assets" / mod_id / "items"
    items_dispatch_dir.mkdir(parents=True, exist_ok=True)
    horizon_item_dir = out_root / "data" / mod_id / "horizon" / "item"
    horizon_item_dir.mkdir(parents=True, exist_ok=True)

    ported_items: set[str] = set()
    candidate_items = set(java_items.keys())
    if models_item_dir.is_dir():
        for mfile in sorted(models_item_dir.glob("*.json")):
            candidate_items.add(mfile.stem.lower())

    for iname in sorted(candidate_items):
        # Write 26.3 client item model dispatch if missing
        dispatch_file = items_dispatch_dir / f"{iname}.json"
        if not dispatch_file.exists():
            dispatch_payload = {
                "model": {
                    "type": "minecraft:model",
                    "model": f"{mod_id}:item/{iname}",
                }
            }
            dispatch_file.write_text(json.dumps(dispatch_payload, indent=2) + "\n", encoding="utf-8")

        if iname not in ported_blocks:
            jitem = java_items.get(iname, {})
            item_def: dict[str, Any] = {"max_stack_size": jitem.get("max_stack_size", 64)}
            if "rarity" in jitem:
                item_def["rarity"] = jitem["rarity"]
            if "fire_resistant" in jitem:
                item_def["fire_resistant"] = jitem["fire_resistant"]
            components: dict[str, Any] = {}
            if "max_damage" in jitem:
                components["minecraft:max_damage"] = jitem["max_damage"]
            if "food" in jitem:
                components["minecraft:food"] = jitem["food"]
            if components:
                item_def["components"] = components
            (horizon_item_dir / f"{iname}.json").write_text(
                json.dumps(item_def, indent=2) + "\n", encoding="utf-8"
            )
            ported_items.add(iname)

    # Also ensure every block with item:true has a 26.3 items/<name>.json dispatch
    for bname in sorted(ported_blocks):
        dispatch_file = items_dispatch_dir / f"{bname}.json"
        if not dispatch_file.exists():
            model_ref = (
                f"{mod_id}:item/{bname}"
                if (models_item_dir / f"{bname}.json").exists()
                else f"{mod_id}:block/{bname}"
            )
            dispatch_file.write_text(
                json.dumps({"model": {"type": "minecraft:model", "model": model_ref}}, indent=2) + "\n",
                encoding="utf-8",
            )

    # 5. Generate data/<mod_id>/horizon/creative_tab/main.json
    all_entries = [f"{mod_id}:{x}" for x in sorted(ported_blocks | ported_items)]
    if all_entries:
        tab_dir = out_root / "data" / mod_id / "horizon" / "creative_tab"
        tab_dir.mkdir(parents=True, exist_ok=True)
        tab_payload = {
            "title": meta["name"],
            "icon": all_entries[0],
            "items": all_entries,
        }
        (tab_dir / "main.json").write_text(json.dumps(tab_payload, indent=2) + "\n", encoding="utf-8")

    # 6. Translate Java gameplay code -> Rust WASM if hooks were detected
    api_ver, capabilities, experimental = generate_rust_wasm_scaffold(
        out_root, mod_id, meta, java_features
    )

    # 7. Write horizon-mod.toml
    authors_toml = ", ".join(f'"{a}"' for a in meta["authors"])
    ns_line = ""
    if extra_namespaces:
        ns_toml = ", ".join(f'"{n}"' for n in sorted(extra_namespaces))
        ns_line = f"namespaces = [{ns_toml}]\n"
    caps_toml = ", ".join(f'"{c}"' for c in capabilities)
    exp_line = ""
    if experimental:
        exp_toml = ", ".join(f'"{e}"' for e in experimental)
        exp_line = f"experimental = [{exp_toml}]\n"
    side_line = 'side = "server"\n' if api_ver == "0.3" else ""
    entry_line = 'entry = "mod.wasm"\n' if (out_root / "src" / "lib.rs").is_file() else ""

    manifest_toml = (
        f'id = "{mod_id}"\n'
        f'name = "{meta["name"].replace(chr(34), chr(39))}"\n'
        f'version = "{meta["version"]}"\n'
        f'api = "{api_ver}"\n'
        f'description = "{meta["description"].splitlines()[0].replace(chr(34), chr(39)) if meta["description"] else "Ported to Horizon"}"\n'
        f"authors = [{authors_toml}]\n"
        f"{side_line}"
        f"{entry_line}"
        f"{ns_line}"
        f"capabilities = [{caps_toml}]\n"
        f"{exp_line}"
    )
    (out_root / "horizon-mod.toml").write_text(manifest_toml, encoding="utf-8")

    return {
        "id": mod_id,
        "name": meta["name"],
        "version": meta["version"],
        "api": api_ver,
        "blocks": len(ported_blocks),
        "items": len(ported_items),
        "has_wasm": (out_root / "src" / "lib.rs").is_file(),
    }


def main() -> int:
    parser = argparse.ArgumentParser(description="Port a Fabric/NeoForge/Forge .jar or folder to a Horizon .hmod project.")
    parser.add_argument("input_path", type=Path, help="Path to source .jar or project folder")
    parser.add_argument("output_dir", type=Path, help="Output directory for ported Horizon mod project")
    parser.add_argument("--package", action="store_true", help="Also package <id>.hmod archive after porting")
    args = parser.parse_args()

    if not args.input_path.exists():
        print(f"error: input path not found: {args.input_path}", file=sys.stderr)
        return 1

    if args.input_path.is_file() and args.input_path.suffix.lower() == ".jar":
        with tempfile.TemporaryDirectory() as tmp:
            tmp_path = Path(tmp)
            with zipfile.ZipFile(args.input_path, "r") as zf:
                zf.extractall(tmp_path)
            summary = port_extracted_mod(tmp_path, args.output_dir)
    else:
        summary = port_extracted_mod(args.input_path, args.output_dir)

    print(
        f"Ported {summary['name']} (id={summary['id']}, v{summary['version']}, api={summary['api']}): "
        f"{summary['blocks']} blocks, {summary['items']} standalone items, wasm={summary['has_wasm']} -> {args.output_dir}"
    )

    if args.package:
        packager = Path(__file__).parent / "package_hmod.py"
        hmod_out = args.output_dir.parent / f"{summary['id']}.hmod"
        subprocess.run([sys.executable, str(packager), str(args.output_dir), "-o", str(hmod_out)], check=True)

    return 0


if __name__ == "__main__":
    raise SystemExit(main())
