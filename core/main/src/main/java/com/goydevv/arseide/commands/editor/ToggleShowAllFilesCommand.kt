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
 * Toggles whether the editor shows **all** open files together (on) or only the files that belong
 * to the project/directory currently selected in the drawer (off, the default).
 *
 * Off behaves like an IDE: each project shows only its own open files, so files from different
 * directories don't get mixed up. On restores the classic "everything open at once" behaviour.
 *
 * Lives in the editor toolbar (and its top-right overflow menu) as a toggle.
 */
class ToggleShowAllFilesCommand : EditorCommand(), ToggleableCommand {
    override val id: String = "editor.show_all_files"

    override fun getLabel(): String = strings.show_all_files.getString()

    override fun action(editorActionContext: EditorActionContext) {
        Settings.show_all_files = !Settings.show_all_files
    }

    override fun isOn(): Boolean = Settings.show_all_files

    override fun getIcon(): Icon = Icon.ResourceIcon(drawables.filter)
}
