#!/usr/bin/env bash
set -euo pipefail

# 1. Ensure wasm32-unknown-unknown target is installed:
#    rustup target add wasm32-unknown-unknown
cargo build --target wasm32-unknown-unknown --release

# 2. If cargo-horizon-mod is installed (`cargo install --path ../../crates/cargo-horizon-mod`),
#    it automatically componentizes the WASM module and packs the .hmod:
if command -v cargo-horizon-mod >/dev/null 2>&1; then
    cargo horizon-mod build
else
    cp target/wasm32-unknown-unknown/release/java_port_client_mod.wasm mod.wasm
    python3 ../../tools/package_hmod.py . --out java_port_client.hmod
fi
