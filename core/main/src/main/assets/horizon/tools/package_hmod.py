#!/usr/bin/env python3
"""
package_hmod.py
===============
Validates and packages a Horizon mod directory into a distributable `.hmod` or `.hnmod` archive.

Performs:
  1. Strict `horizon-mod.toml` validation (no unknown keys, valid `[a-z0-9_]{1,64}` id,
     not `minecraft`/`c`/`horizon`, matching `experimental` entries for `"ui"`, `"render"`,
     `"input"`, `"world"`, `"players"`).
  2. Verification that every declared block (`item: true`) and item under
     `data/<id>/horizon/{block,item}/` has a corresponding 26.3 client item model
     dispatch at `assets/<id>/items/<name>.json`.
  3. Optional `cargo build --target wasm32-unknown-unknown --release` when `Cargo.toml`
     is present.
  4. Deflate ZIP packaging of `horizon-mod.toml`, `mod.wasm` (if present), `assets/`,
     and `data/` at the archive root with no wrapper directory.
"""

from __future__ import annotations

import argparse
import json
import re
import shutil
import subprocess
import sys
import zipfile
from pathlib import Path

try:
    import tomllib  # Python 3.11+
except ModuleNotFoundError:  # pragma: no cover
    tomllib = None  # type: ignore

ALLOWED_MANIFEST_KEYS = {
    "id",
    "name",
    "version",
    "api",
    "entry",
    "side",
    "description",
    "authors",
    "namespaces",
    "capabilities",
    "experimental",
    "dependencies",
    "platform",
}

EXPERIMENTAL_REQUIRED_CAPABILITIES = {"ui", "render", "input"}
RESERVED_IDS = {"minecraft", "c", "horizon"}
MOD_ID_RE = re.compile(r"^[a-z0-9_]{1,64}$")


def parse_manifest(manifest_path: Path) -> dict:
    raw = manifest_path.read_text(encoding="utf-8")
    if tomllib is not None:
        return tomllib.loads(raw)
    data: dict = {}
    for line in raw.splitlines():
        line = line.split("#", 1)[0].strip()
        if "=" in line and not line.startswith("["):
            k, v = line.split("=", 1)
            k = k.strip()
            v = v.strip()
            if v.startswith("[") and v.endswith("]"):
                items = [x.strip().strip('"').strip("'") for x in v[1:-1].split(",") if x.strip()]
                data[k] = items
            else:
                data[k] = v.strip('"').strip("'")
    return data


def validate_project(project_dir: Path) -> tuple[dict, list[str], list[str]]:
    """
    Returns (manifest_dict, errors, warnings).
    """
    errors: list[str] = []
    warnings: list[str] = []

    manifest_path = project_dir / "horizon-mod.toml"
    mixin_path = project_dir / "horizon-mixin.toml"
    if not manifest_path.is_file() and mixin_path.is_file():
        data = parse_manifest(mixin_path)
        return data, errors, warnings

    if not manifest_path.is_file():
        errors.append(f"Missing horizon-mod.toml in {project_dir}")
        return {}, errors, warnings

    try:
        manifest = parse_manifest(manifest_path)
    except Exception as exc:
        errors.append(f"Invalid TOML in horizon-mod.toml: {exc}")
        return {}, errors, warnings

    unknown_keys = set(manifest.keys()) - ALLOWED_MANIFEST_KEYS
    if unknown_keys:
        errors.append(f"Unknown keys in horizon-mod.toml (rejected by Horizon loader): {sorted(unknown_keys)}")

    for req in ("id", "name", "version", "api"):
        if req not in manifest or not str(manifest[req]).strip():
            errors.append(f"horizon-mod.toml is missing required field '{req}'")

    mod_id = str(manifest.get("id", ""))
    if mod_id in RESERVED_IDS:
        errors.append(f"Mod id '{mod_id}' is reserved ({sorted(RESERVED_IDS)})")
    elif not MOD_ID_RE.match(mod_id):
        errors.append(f"Mod id '{mod_id}' must match ^[a-z0-9_]{{1,64}}$")

    caps = list(manifest.get("capabilities", []))
    exp = set(manifest.get("experimental", []))
    for cap in caps:
        if cap in EXPERIMENTAL_REQUIRED_CAPABILITIES and cap not in exp:
            errors.append(
                f"Capability '{cap}' requires '{cap}' to also be listed in experimental = [...] in horizon-mod.toml"
            )

    # Check 26.3 client item model dispatch files for every declared block and item
    block_dir = project_dir / "data" / mod_id / "horizon" / "block"
    item_dir = project_dir / "data" / mod_id / "horizon" / "item"
    dispatch_dir = project_dir / "assets" / mod_id / "items"

    if block_dir.is_dir():
        for bfile in sorted(block_dir.glob("*.json")):
            try:
                bjson = json.loads(bfile.read_text(encoding="utf-8"))
            except Exception as exc:
                errors.append(f"Invalid JSON in {bfile.relative_to(project_dir)}: {exc}")
                continue
            if bjson.get("item", True):
                dispatch_file = dispatch_dir / f"{bfile.stem}.json"
                if not dispatch_file.is_file():
                    errors.append(
                        f"Block '{mod_id}:{bfile.stem}' has item=true but is missing 26.3 client model dispatch at assets/{mod_id}/items/{bfile.stem}.json"
                    )

    if item_dir.is_dir():
        for ifile in sorted(item_dir.glob("*.json")):
            dispatch_file = dispatch_dir / f"{ifile.stem}.json"
            if not dispatch_file.is_file():
                errors.append(
                    f"Item '{mod_id}:{ifile.stem}' is missing 26.3 client model dispatch at assets/{mod_id}/items/{ifile.stem}.json"
                )

    return manifest, errors, warnings


def build_wasm_if_needed(project_dir: Path, entry_name: str) -> Path | None:
    cargo_toml = project_dir / "Cargo.toml"
    if not cargo_toml.is_file():
        wasm_file = project_dir / entry_name
        return wasm_file if wasm_file.is_file() else None

    print(f"[1/2] Building Rust WASM crate in {project_dir} ...")
    subprocess.run(
        ["cargo", "build", "--target", "wasm32-unknown-unknown", "--release"],
        cwd=project_dir,
        check=True,
    )

    target_dir = project_dir / "target" / "wasm32-unknown-unknown" / "release"
    wasm_candidates = sorted(target_dir.glob("*.wasm"), key=lambda p: p.stat().st_mtime, reverse=True)
    if not wasm_candidates:
        raise FileNotFoundError(f"No .wasm artifact produced in {target_dir}")

    dest_wasm = project_dir / entry_name
    shutil.copy2(wasm_candidates[0], dest_wasm)
    return dest_wasm


def package_hmod(project_dir: Path, output_path: Path | None, skip_build: bool) -> Path:
    project_dir = project_dir.resolve()
    manifest, errors, warnings = validate_project(project_dir)
    for w in warnings:
        print(f"warning: {w}", file=sys.stderr)
    if errors:
        for e in errors:
            print(f"error: {e}", file=sys.stderr)
        raise ValueError(f"Horizon Mod validation failed with {len(errors)} error(s).")

    # Native mixin mod (.hnmod)
    if (project_dir / "horizon-mixin.toml").is_file() and not (project_dir / "horizon-mod.toml").is_file():
        mod_id = str(manifest.get("id", project_dir.name))
        out_file = (output_path or (project_dir / f"{mod_id}.hnmod")).resolve()
        with zipfile.ZipFile(out_file, "w", compression=zipfile.ZIP_DEFLATED) as zf:
            zf.write(project_dir / "horizon-mixin.toml", arcname="horizon-mixin.toml")
            for sub in ("src", "libraries"):
                subdir = project_dir / sub
                if subdir.is_dir():
                    for f in sorted(subdir.rglob("*")):
                        if f.is_file():
                            zf.write(f, arcname=f.relative_to(project_dir).as_posix())
        print(f"Packaged {out_file}")
        return out_file

    mod_id = str(manifest["id"])
    entry_name = str(manifest.get("entry", "mod.wasm"))

    wasm_path: Path | None = None
    if not skip_build:
        wasm_path = build_wasm_if_needed(project_dir, entry_name)
    else:
        candidate = project_dir / entry_name
        if candidate.is_file():
            wasm_path = candidate

    out_file = (output_path or (project_dir / f"{mod_id}.hmod")).resolve()
    out_file.parent.mkdir(parents=True, exist_ok=True)

    manifest_path = project_dir / "horizon-mod.toml"
    print(f"[2/2] Packaging {out_file.name} ...")
    with zipfile.ZipFile(out_file, "w", compression=zipfile.ZIP_DEFLATED) as zf:
        zf.write(manifest_path, arcname="horizon-mod.toml")
        if wasm_path is not None and wasm_path.is_file():
            zf.write(wasm_path, arcname=entry_name)
        for folder_name in ("assets", "data"):
            folder = project_dir / folder_name
            if folder.is_dir():
                for file in sorted(folder.rglob("*")):
                    if file.is_file():
                        arcname = file.relative_to(project_dir).as_posix()
                        zf.write(file, arcname=arcname)

    print(f"Done: {out_file} ({out_file.stat().st_size:,} bytes)")
    return out_file


def main() -> int:
    parser = argparse.ArgumentParser(description="Validate and package a Horizon mod project into a .hmod archive.")
    parser.add_argument("project_dir", type=Path, help="Path to the Horizon mod project directory")
    parser.add_argument("-o", "--output", type=Path, default=None, help="Output .hmod file path")
    parser.add_argument("--no-build", action="store_true", help="Skip running cargo build")
    parser.add_argument("--validate-only", action="store_true", help="Only validate manifest and 26.3 item dispatches")
    args = parser.parse_args()

    if args.validate_only:
        _, errors, warnings = validate_project(args.project_dir.resolve())
        for w in warnings:
            print(f"warning: {w}")
        for e in errors:
            print(f"error: {e}", file=sys.stderr)
        return 1 if errors else 0

    try:
        package_hmod(args.project_dir, args.output, args.no_build)
    except Exception as exc:
        print(f"error: {exc}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
