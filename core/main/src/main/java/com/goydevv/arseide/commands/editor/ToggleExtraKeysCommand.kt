package com.goydevv.arseide.commands.editor

import com.goydevv.arseide.commands.EditorActionContext
import com.goydevv.arseide.commands.EditorCommand
import com.goydevv.arseide.commands.ToggleableCommand
import com.goydevv.arseide.icons.Icon
import com.goydevv.arseide.resources.drawables
import com.goydevv.arseide.resources.getString
import com.goydevv.arseide.resources.strings
import com.goydevv.arseide.settings.Settings

/**
 * Toggles the editor's bottom extra-keys bar (Tab / ← → ↑ ↓ / symbols). Off by default; lives in
 * the editor toolbar's overflow (the 3-dot menu) as a toggle so it's easy to turn on/off per taste.
 */
class ToggleExtraKeysCommand : EditorCommand(), ToggleableCommand {
    override val id: String = "editor.toggle_extra_keys"

    override fun getLabel(): String = strings.extra_keys.getString()

    override fun action(editorActionContext: EditorActionContext) {
        Settings.show_extra_keys = !Settings.show_extra_keys
    }

    override fun isOn(): Boolean = Settings.show_extra_keys

    override fun getIcon(): Icon = Icon.ResourceIcon(drawables.keyboard)
}
