package com.goydevv.arseide.commands.global

import android.view.KeyEvent
import com.goydevv.arseide.commands.ActionContext
import com.goydevv.arseide.commands.GlobalCommand
import com.goydevv.arseide.commands.KeyCombination
import com.goydevv.arseide.components.codeSearchDialog
import com.goydevv.arseide.icons.Icon
import com.goydevv.arseide.resources.drawables
import com.goydevv.arseide.resources.getString
import com.goydevv.arseide.resources.strings

class SearchCodeCommand : GlobalCommand() {
    override val id: String = "global.search_code"

    override fun getLabel(): String = strings.search_code.getString()

    override fun action(actionContext: ActionContext) {
        codeSearchDialog = true
    }

    override fun isEnabled(): Boolean {
        return commandContext.drawerViewModel.currentDrawerTab != null
    }

    override fun getIcon(): Icon = Icon.ResourceIcon(drawables.search)

    override val defaultKeybinds: KeyCombination =
        KeyCombination(keyCode = KeyEvent.KEYCODE_F, ctrl = true, shift = true)
}
