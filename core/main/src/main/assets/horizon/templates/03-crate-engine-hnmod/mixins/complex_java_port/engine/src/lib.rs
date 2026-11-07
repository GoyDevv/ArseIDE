use std::sync::atomic::{AtomicU64, Ordering};

static TICKS: AtomicU64 = AtomicU64::new(0);

pub fn mod_banner() -> &'static str {
    "Multi-module Java mod ported with companion Cargo crate"
}

pub fn on_client_tick() -> u64 {
    TICKS.fetch_add(1, Ordering::Relaxed) + 1
}
