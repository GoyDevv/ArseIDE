{
    if pressed && key.eq_ignore_ascii_case("key.keyboard.v") && !game.native_in_menu() {
        let on = crate::game::custom_native_port_runtime::toggle();
        game.native_chat(&format!(
            "Custom Native Port: {}",
            if on { "Enabled" } else { "Disabled" }
        ));
    }
}
