{
    if crate::game::custom_native_port_runtime::is_enabled() && !game.native_in_menu() {
        shapes.push(crate::game::native_hooks::NativeShape::Text {
            text: "Native Port Active".to_string(),
            x: 8.0,
            y: 8.0,
            color: 0xFF7DD3FC,
            scale: 1.0,
            centered: false,
            shadow: true,
        });
    }
}
