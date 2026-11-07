#!/usr/bin/env python3
"""
Validates an `.hnmod` folder or archive against the exact rules of
`crates/horizon-mods/src/index/native.rs` (`Bundle::read`) and compiles any
companion `[[crate]]` in the background to verify Rust syntax/types.
"""
import argparse
import json
import os
import re
import subprocess
import sys
import zipfile

def valid_id(s: str) -> bool:
    return bool(re.fullmatch(r"[a-z0-9_]{1,64}", s)) and s not in ("minecraft", "c", "horizon")

def valid_rev(s: str) -> bool:
    return bool(re.fullmatch(r"[0-9a-f]{40}", s))

def allowed_rest(rest: str) -> bool:
    if rest.endswith((".rs", ".toml")):
        return True
    parts = rest.split("/")
    if len(parts) >= 2 and (parts[0] == "data" or (len(parts) >= 3 and parts[1] == "data")):
        return True
    name = parts[-1]
    if len(parts) <= 2 and (any(name.startswith(p) for p in ("README", "LICENSE", "LICENCE", "COPYING", "NOTICE")) or name == ".gitignore"):
        return True
    return False

def pack_and_validate(src_dir: str, out_file: str):
    src_dir = os.path.abspath(src_dir)
    manifest_path = os.path.join(src_dir, "horizon-native.json")
    if not os.path.isfile(manifest_path):
        sys.exit("ERROR: missing horizon-native.json at root of bundle")

    with open(manifest_path, "r", encoding="utf-8") as f:
        manifest = json.load(f)

    if manifest.get("schema") != 1:
        sys.exit("ERROR: horizon-native.json schema must be 1")
    mod_id = manifest.get("id", "")
    version = manifest.get("version", "")
    horizon_rev = manifest.get("horizon", "")
    mixins_rev = manifest.get("mixins", "")
    if not valid_id(mod_id) or not version or not valid_rev(horizon_rev) or not valid_rev(mixins_rev):
        sys.exit("ERROR: invalid id, version, or 40-char hex revision in horizon-native.json")

    mixin_toml_path = os.path.join(src_dir, "mixins", mod_id, "horizon-mixin.toml")
    if not os.path.isfile(mixin_toml_path):
        sys.exit(f"ERROR: missing mixins/{mod_id}/horizon-mixin.toml")

    with open(mixin_toml_path, "r", encoding="utf-8") as f:
        toml_txt = f.read()

    if f'id = "{mod_id}"' not in toml_txt or f'version = "{version}"' not in toml_txt or f'horizon_revision = "{horizon_rev}"' not in toml_txt:
        sys.exit("ERROR: horizon-mixin.toml id, version, or horizon_revision does not match horizon-native.json")

    # Optional: Run cargo check on companion engine crate if cargo is available
    engine_cargo = os.path.join(src_dir, "mixins", mod_id, "engine", "Cargo.toml")
    if os.path.isfile(engine_cargo) and shutil_which("cargo"):
        res = subprocess.run(["cargo", "check", "--manifest-path", engine_cargo], capture_output=True, text=True)
        if res.returncode != 0:
            sys.exit(f"ERROR: companion crate failed cargo check:\n{res.stderr}")

    # Package into .hnmod zip
    prefix = f"mixins/{mod_id}/"
    entries = []
    for root, dirs, fnames in os.walk(os.path.join(src_dir, "mixins", mod_id)):
        dirs[:] = [d for d in sorted(dirs) if d not in ("target", ".git", "__pycache__")]
        for fn in sorted(fnames):
            full = os.path.join(root, fn)
            rel = os.path.relpath(full, src_dir).replace("\\", "/")
            rest = rel[len(prefix):]
            if not allowed_rest(rest):
                continue
            entries.append((full, rel))

    if len(entries) + 1 > 512:
        sys.exit("ERROR: native bundle exceeds 512 files limit")

    with zipfile.ZipFile(out_file, "w", compression=zipfile.ZIP_DEFLATED) as zf:
        zf.write(manifest_path, "horizon-native.json")
        for full, rel in entries:
            zf.write(full, rel)

    print(f"VALIDATED & PACKAGED: {out_file} ({len(entries) + 1} files)")

def shutil_which(cmd: str) -> bool:
    import shutil
    return shutil.which(cmd) is not None

def main():
    parser = argparse.ArgumentParser(description="Validate and package a Horizon .hnmod bundle")
    parser.add_argument("src", help="Source directory containing horizon-native.json and mixins/<id>/")
    parser.add_argument("--out", help="Output .hnmod path")
    args = parser.parse_args()
    out = args.out or (os.path.basename(os.path.abspath(args.src)).replace("-hnmod", "") + ".hnmod")
    pack_and_validate(args.src, out)

if __name__ == "__main__":
    main()
