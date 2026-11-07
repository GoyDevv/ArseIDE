use std::sync::atomic::{AtomicBool, Ordering};

pub static SHOW_HUD: AtomicBool = AtomicBool::new(true);

pub fn is_shown() -> bool {
    SHOW_HUD.load(Ordering::Relaxed)
}

pub fn toggle() -> bool {
    let next = !is_shown();
    SHOW_HUD.store(next, Ordering::Relaxed);
    next
}
