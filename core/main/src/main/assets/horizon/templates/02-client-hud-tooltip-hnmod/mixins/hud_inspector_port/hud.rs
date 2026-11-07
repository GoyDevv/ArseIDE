{
    if crate::game::hud_inspector_runtime::is_shown() && !game.native_in_menu() {
        if let Some(p) = game.native_player() {
            shapes.push(crate::game::native_hooks::NativeShape::Rect {
                x: 6.0,
                y: 6.0,
                w: 152.0,
                h: 22.0,
                color: 0x9010141F,
            });
            shapes.push(crate::game::native_hooks::NativeShape::Text {
                text: format!("XYZ: {:.1}, {:.1}, {:.1}", p.x, p.y, p.z),
                x: 12.0,
                y: 13.0,
                color: 0xFFE2E8F0,
                scale: 1.0,
                centered: false,
                shadow: true,
            });
        }
    }
}
