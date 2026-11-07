{
    if pressed && key.eq_ignore_ascii_case("key.keyboard.grave.accent") && !game.native_in_menu() {
        let on = crate::game::veinminer_port_runtime::toggle_enabled();
        game.native_chat(&format!(
            "Veinminer: {}",
            if on { "ON" } else { "OFF" }
        ));
    }
}
