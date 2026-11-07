{
    let _ = args;
    if name == "hudport" {
        let on = crate::game::hud_inspector_runtime::toggle();
        game.native_chat(&format!("HUD Overlay: {}", if on { "ON" } else { "OFF" }));
        *ran = true;
    }
}
