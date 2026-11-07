package com.goydevv.arseide.commands.global

import android.view.KeyEvent
import com.goydevv.arseide.commands.ActionContext
import com.goydevv.arseide.commands.GlobalCommand
import com.goydevv.arseide.commands.KeyCombination
import com.goydevv.arseide.components.fileSearchDialog
import com.goydevv.arseide.filetree.FileTreeTab
import com.goydevv.arseide.icons.Icon
import com.goydevv.arseide.resources.drawables
import com.goydevv.arseide.resources.getString
import com.goydevv.arseide.resources.strings

class SearchFileFolderCommand : GlobalCommand() {
    override val id: String = "global.search_file_folder"

    override fun getLabel(): String = strings.search_file_folder.getString()

    override fun action(actionContext: ActionContext) {
        fileSearchDialog = true
    }

    override fun isEnabled(): Boolean {
        return commandContext.drawerViewModel.currentDrawerTab is FileTreeTab
    }

    override fun getIcon(): Icon = Icon.ResourceIcon(drawables.search)

    override val defaultKeybinds: KeyCombination = KeyCombination(keyCode = KeyEvent.KEYCODE_P, ctrl = true)
}
