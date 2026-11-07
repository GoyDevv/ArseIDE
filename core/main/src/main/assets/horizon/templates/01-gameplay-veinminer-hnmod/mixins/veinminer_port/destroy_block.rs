{
    if !*canceled
        && crate::game::veinminer_port_runtime::is_enabled()
        && !crate::game::veinminer_port_runtime::IN_VEIN.load(std::sync::atomic::Ordering::Relaxed)
    {
        let block_id = state.split('[').next().unwrap_or(state);
        if crate::game::veinminer_port_runtime::is_vein_target(block_id) {
            crate::game::veinminer_port_runtime::IN_VEIN.store(true, std::sync::atomic::Ordering::Relaxed);
            let limit = crate::game::veinminer_port_runtime::max_blocks();
            let mut visited = std::collections::BTreeSet::new();
            let mut queue = std::collections::VecDeque::new();
            visited.insert(pos);
            queue.push_back(pos);

            while let Some([x, y, z]) = queue.pop_front() {
                if visited.len() >= limit {
                    break;
                }
                for dx in -1..=1 {
                    for dy in -1..=1 {
                        for dz in -1..=1 {
                            if dx == 0 && dy == 0 && dz == 0 {
                                continue;
                            }
                            let next = [x + dx, y + dy, z + dz];
                            if visited.contains(&next) {
                                continue;
                            }
                            if let Some(nb) = game.native_block(next) {
                                let nb_id = nb.split('[').next().unwrap_or(&nb);
                                if nb_id == block_id {
                                    visited.insert(next);
                                    queue.push_back(next);
                                    let _ = game.native_set_block(
                                        next,
                                        "minecraft:air",
                                        crate::game::native_hooks::flags::UPDATE_ALL,
                                    );
                                    if visited.len() >= limit {
                                        break;
                                    }
                                }
                            }
                        }
                    }
                }
            }
            crate::game::veinminer_port_runtime::IN_VEIN.store(false, std::sync::atomic::Ordering::Relaxed);
        }
    }
}
