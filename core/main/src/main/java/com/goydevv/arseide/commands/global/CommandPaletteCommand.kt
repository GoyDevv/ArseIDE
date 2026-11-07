package com.goydevv.arseide.commands.global

import android.view.KeyEvent
import com.goydevv.arseide.commands.ActionContext
import com.goydevv.arseide.commands.GlobalCommand
import com.goydevv.arseide.commands.KeyCombination
import com.goydevv.arseide.icons.Icon
import com.goydevv.arseide.resources.drawables
import com.goydevv.arseide.resources.getString
import com.goydevv.arseide.resources.strings

class CommandPaletteCommand : GlobalCommand() {
    override val id: String = "global.command_palette"

    override fun getLabel(): String = strings.command_palette.getString()

    override fun action(actionContext: ActionContext) {
        commandContext.mainViewModel.showCommandPalette()
    }

    override fun getIcon(): Icon = Icon.ResourceIcon(drawables.command_palette)

    override val defaultKeybinds: KeyCombination =
        KeyCombination(keyCode = KeyEvent.KEYCODE_P, ctrl = true, shift = true)
}
