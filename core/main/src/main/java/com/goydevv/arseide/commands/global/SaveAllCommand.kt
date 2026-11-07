package com.goydevv.arseide.commands.global

import android.view.KeyEvent
import com.goydevv.arseide.commands.ActionContext
import com.goydevv.arseide.commands.GlobalCommand
import com.goydevv.arseide.commands.KeyCombination
import com.goydevv.arseide.icons.Icon
import com.goydevv.arseide.resources.drawables
import com.goydevv.arseide.resources.getString
import com.goydevv.arseide.resources.strings
import com.goydevv.arseide.tabs.editor.EditorTab
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

class SaveAllCommand : GlobalCommand() {
    override val id: String = "global.save_all"

    override fun getLabel(): String = strings.save_all.getString()

    override fun action(actionContext: ActionContext) {
        commandContext.mainViewModel.tabs.filterIsInstance<EditorTab>().forEach {
            GlobalScope.launch(Dispatchers.IO) { it.save() }
        }
    }

    override fun isEnabled(): Boolean {
        return commandContext.mainViewModel.tabs.isNotEmpty()
    }

    override fun getIcon(): Icon = Icon.ResourceIcon(drawables.save)

    override val defaultKeybinds: KeyCombination =
        KeyCombination(keyCode = KeyEvent.KEYCODE_S, ctrl = true, shift = true)
}
