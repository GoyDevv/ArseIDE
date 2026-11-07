{
    let _ = game;
    if mod_id == "veinminer_port" {
        let state = if crate::game::veinminer_port_runtime::is_enabled() { "ON" } else { "OFF" };
        let limit = crate::game::veinminer_port_runtime::max_blocks();
        buttons.push(format!("Veinminer: {state}"));
        buttons.push(format!("Max Connected Blocks: {limit}"));
    }
}
