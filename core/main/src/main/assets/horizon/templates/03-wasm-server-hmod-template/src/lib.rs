//! Server-side WASM mod template (`api = "0.3"`, `side = "server"`).
//! Great for porting server/gameplay mods like TreeCapitator, VeinMiner,
//! spawn utilities, land claims, or custom block interaction rules.

use horizon_mod_sdk::{
    Argument, ArgumentKind, ArgumentValue, BlockChange, BlockPos, ServerCommand, ServerMod,
    ServerPlayer, ServerRegistration, players, world,
};

struct PortedServerMod {
    timber_enabled: bool,
}

impl ServerMod for PortedServerMod {
    fn init() -> Result<(Self, ServerRegistration), String> {
        let mut reg = ServerRegistration::new()
            .on_block_break()
            .on_block_use()
            .on_player_join();

        reg.commands.push(ServerCommand {
            name: "timber".into(),
            description: "Toggle automatic tree chopping when breaking logs".into(),
            arguments: vec![Argument {
                name: "enabled".into(),
                kind: ArgumentKind::Bool,
                required: false,
            }],
        });

        Ok((Self { timber_enabled: true }, reg))
    }

    fn player_join(&mut self, player: ServerPlayer) {
        players::tell(
            &player.name,
            "Welcome! Server mod loaded (use /timber to toggle tree chopping).",
        );
    }

    fn block_break(&mut self, player: ServerPlayer, pos: BlockPos, block: String) -> bool {
        if !self.timber_enabled || !block.ends_with("_log") {
            return true; // Allow normal break
        }
        // Simple vertical tree-chopper example: replace contiguous logs above with air
        let mut changes = Vec::new();
        for dy in 1..=16 {
            let up = BlockPos {
                x: pos.x,
                y: pos.y + dy,
                z: pos.z,
            };
            if let Some(above) = world::get_block(&player.dimension, up) {
                if above == block {
                    changes.push(BlockChange {
                        pos: up,
                        block: "minecraft:air".into(),
                    });
                } else {
                    break;
                }
            }
        }
        if !changes.is_empty() {
            let _ = world::set_blocks(&player.dimension, &changes);
        }
        true
    }

    fn command(
        &mut self,
        player: Option<ServerPlayer>,
        name: &str,
        args: Vec<ArgumentValue>,
    ) -> Result<Option<String>, String> {
        if name == "timber" {
            self.timber_enabled = match args.first() {
                Some(ArgumentValue::Bool(b)) => *b,
                _ => !self.timber_enabled,
            };
            let msg = format!(
                "Timber mode is now {}",
                if self.timber_enabled { "ON" } else { "OFF" }
            );
            if let Some(p) = player {
                players::tell(&p.name, &msg);
            }
            return Ok(Some(msg));
        }
        Ok(None)
    }
}

horizon_mod_sdk::export_server_mod!(PortedServerMod);
