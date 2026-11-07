package com.goydevv.arseide.commands.global

import android.view.KeyEvent
import com.goydevv.arseide.commands.ActionContext
import com.goydevv.arseide.commands.GlobalCommand
import com.goydevv.arseide.commands.KeyCombination
import com.goydevv.arseide.components.addDialog
import com.goydevv.arseide.icons.Icon
import com.goydevv.arseide.resources.drawables
import com.goydevv.arseide.resources.getString
import com.goydevv.arseide.resources.strings

class NewFileCommand : GlobalCommand() {
    override val id: String = "global.new_file"

    override fun getLabel(): String = strings.new_file.getString()

    override fun action(actionContext: ActionContext) {
        addDialog = true
    }

    override fun getIcon(): Icon = Icon.ResourceIcon(drawables.add)

    override val defaultKeybinds: KeyCombination = KeyCombination(keyCode = KeyEvent.KEYCODE_N, ctrl = true)
}
