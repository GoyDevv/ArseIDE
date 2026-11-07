use std::sync::atomic::{AtomicBool, AtomicUsize, Ordering};

pub static ENABLED: AtomicBool = AtomicBool::new(true);
pub static MAX_BLOCKS: AtomicUsize = AtomicUsize::new(32);
pub static IN_VEIN: AtomicBool = AtomicBool::new(false);

pub fn is_enabled() -> bool {
    ENABLED.load(Ordering::Relaxed)
}

pub fn toggle_enabled() -> bool {
    let next = !is_enabled();
    ENABLED.store(next, Ordering::Relaxed);
    next
}

pub fn max_blocks() -> usize {
    MAX_BLOCKS.load(Ordering::Relaxed)
}

pub fn cycle_max_blocks() -> usize {
    let next = match max_blocks() {
        16 => 32,
        32 => 64,
        _ => 16,
    };
    MAX_BLOCKS.store(next, Ordering::Relaxed);
    next
}

pub fn is_vein_target(block_id: &str) -> bool {
    block_id.ends_with("_ore") || block_id.ends_with("_log") || block_id.ends_with("_stem")
}
