use std::sync::atomic::{AtomicBool, Ordering};

pub static ENABLED: AtomicBool = AtomicBool::new(true);

pub fn is_enabled() -> bool {
    ENABLED.load(Ordering::Relaxed)
}

pub fn toggle() -> bool {
    let next = !is_enabled();
    ENABLED.store(next, Ordering::Relaxed);
    next
}
