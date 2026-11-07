//! Starter template for porting a client-side or hybrid Java mod to Horizon WASM (`.hmod`).
//!
//! Capabilities demonstrated:
//! - `input`: Custom keybindings (`register_key`) and claiming right-click on modded items (`claim_items`).
//! - `game-read`: Reading player coords/health/dimension and raycasting (`game::raycast`) for WTHIT/Jade-style block inspection.
//! - `hud` & `render`: Drawing a corner text panel and custom 2D HUD shapes/item icons.
//! - `ui`: Opening an interactive mod screen (`ui::open_screen`) and Options > Mods settings page (`ui::set_settings`).
//! - `storage`: Persisting mod config across sessions (`storage::get` / `storage::set`).

use horizon_mod_sdk::{
    Mod, Registration, chat, game,
    hud::{self, Corner, Line},
    input::{self, Event as InputEvent},
    log,
    render::{self, FillShape, ItemShape, Point, Rect, Shape, TextShape},
    storage,
    ui::{self, ButtonWidget, ListWidget, Screen, TextWidget, Widget},
};

const KEY_TOGGLE_HUD: &str = "toggle_inspector";
const KEY_OPEN_MENU: &str = "open_port_menu";
const STORAGE_HUD_KEY: &str = "hud_enabled";

struct PortedClientMod {
    hud_enabled: bool,
    selected_mode: u32,
}

impl PortedClientMod {
    fn refresh_settings_page(&self) {
        let status = if self.hud_enabled { "ON" } else { "OFF" };
        ui::set_settings(&[
            Widget::Text(TextWidget {
                id: 1,
                text: format!("Block Inspector HUD: {status}"),
                color: 0xFFFFFF,
            }),
            Widget::Button(ButtonWidget {
                id: 2,
                text: "Toggle Inspector HUD".into(),
            }),
            Widget::List(ListWidget {
                id: 3,
                rows: vec![
                    "Mode: Compact".into(),
                    "Mode: Detailed (with Coords)".into(),
                ],
                selected: Some(self.selected_mode),
            }),
        ]);
    }

    fn open_custom_screen(&self) {
        ui::open_screen(&Screen {
            title: "Ported Mod Control Panel".into(),
            widgets: vec![
                Widget::Text(TextWidget {
                    id: 10,
                    text: "Select an action below:".into(),
                    color: 0xA0E0FF,
                }),
                Widget::Button(ButtonWidget {
                    id: 11,
                    text: "Toggle Block Inspector".into(),
                }),
                Widget::Button(ButtonWidget {
                    id: 12,
                    text: "Close Screen".into(),
                }),
            ],
        });
    }
}

impl Mod for PortedClientMod {
    fn init() -> Result<(Self, Registration), String> {
        log::info("Initializing PortedClientMod...");

        // Register custom keybinds (shown in Controls menu)
        input::register_key(KEY_TOGGLE_HUD, "Toggle Block Inspector", "key.keyboard.i");
        input::register_key(KEY_OPEN_MENU, "Open Mod Menu", "key.keyboard.o");

        // Claim right-click use on specific items (e.g. a modded wand or compass)
        input::claim_items(&["minecraft:compass".into()]);

        let hud_enabled = storage::get(STORAGE_HUD_KEY)
            .map(|b| b != b"0")
            .unwrap_or(true);

        let instance = Self {
            hud_enabled,
            selected_mode: 1,
        };
        instance.refresh_settings_page();

        let reg = Registration::new()
            .on_tick()
            .command("inspector");

        Ok((instance, reg))
    }

    fn tick(&mut self, _tick: u64) {
        // 1. Poll keybind & item-use events
        for ev in input::poll() {
            match ev {
                InputEvent::KeyPressed(k) if k == KEY_TOGGLE_HUD => {
                    self.hud_enabled = !self.hud_enabled;
                    let _ = storage::set(
                        STORAGE_HUD_KEY,
                        if self.hud_enabled { b"1" } else { b"0" },
                    );
                    self.refresh_settings_page();
                    chat::show(&format!(
                        "Block Inspector HUD: {}",
                        if self.hud_enabled { "Enabled" } else { "Disabled" }
                    ));
                }
                InputEvent::KeyPressed(k) if k == KEY_OPEN_MENU => {
                    self.open_custom_screen();
                }
                InputEvent::ItemUsed(use_ev) => {
                    chat::show(&format!("Used claimed item: {}", use_ev.item.item));
                }
                _ => {}
            }
        }

        if !self.hud_enabled {
            hud::clear();
            render::set_hud(&[]);
            return;
        }

        // 2. Read player & raycast (like Jade / WTHIT in Java mods)
        let Some(player) = game::get_player() else {
            hud::clear();
            render::set_hud(&[]);
            return;
        };

        let mut lines = vec![Line {
            text: format!(
                "XYZ: {:.1}, {:.1}, {:.1} ({})",
                player.x, player.y, player.z, player.dimension
            ),
            color: 0xE2E8F0,
        }];

        if let Some(hit) = game::raycast(6.0) {
            lines.push(Line {
                text: format!("Looking at: {}", hit.block),
                color: 0x7DD3FC,
            });
            if self.selected_mode == 1 {
                lines.push(Line {
                    text: format!("Block Pos: {}, {}, {}", hit.block_x, hit.block_y, hit.block_z),
                    color: 0x94A3B8,
                });
            }

            // Draw a top-center Jade/WTHIT-style tooltip box using capability `render`
            let (gui_w, _) = render::gui_size();
            let box_w = 150.0;
            let box_x = (gui_w - box_w) * 0.5;
            render::set_hud(&[
                Shape::Fill(FillShape {
                    rect: Rect {
                        x: box_x,
                        y: 6.0,
                        w: box_w,
                        h: 24.0,
                    },
                    color: 0xB012131C,
                }),
                Shape::Item(ItemShape {
                    item: hit.block.clone(),
                    at: Point {
                        x: box_x + 4.0,
                        y: 10.0,
                    },
                    scale: 1.0,
                }),
                Shape::Text(TextShape {
                    text: hit.block,
                    at: Point {
                        x: box_x + 26.0,
                        y: 14.0,
                    },
                    color: 0xFFFFFF,
                    centered: false,
                    shadow: true,
                }),
            ]);
        } else {
            render::set_hud(&[]);
        }

        hud::set_panel(Corner::TopLeft, &lines);
    }

    fn command(&mut self, name: &str, _args: &str) -> Result<Option<String>, String> {
        if name == "inspector" {
            self.open_custom_screen();
            return Ok(None);
        }
        Ok(None)
    }

    fn ui_select(&mut self, widget_id: u32, row: u32) {
        match widget_id {
            2 | 11 => {
                self.hud_enabled = !self.hud_enabled;
                let _ = storage::set(
                    STORAGE_HUD_KEY,
                    if self.hud_enabled { b"1" } else { b"0" },
                );
                self.refresh_settings_page();
            }
            3 => {
                self.selected_mode = row;
                self.refresh_settings_page();
            }
            12 => {
                ui::close_screen();
            }
            _ => {}
        }
    }
}

horizon_mod_sdk::export_mod!(PortedClientMod);
