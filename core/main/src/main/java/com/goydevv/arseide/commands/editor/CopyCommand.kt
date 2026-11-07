package com.goydevv.arseide.commands.editor

import android.view.KeyEvent
import com.goydevv.arseide.commands.EditorActionContext
import com.goydevv.arseide.commands.EditorCommand
import com.goydevv.arseide.commands.KeyCombination
import com.goydevv.arseide.icons.Icon
import com.goydevv.arseide.resources.drawables
import com.goydevv.arseide.resources.getString
import com.goydevv.arseide.resources.strings

class CopyCommand : EditorCommand() {
    override val id: String = "editor.copy"

    override fun getLabel(): String = strings.copy.getString()

    override fun action(editorActionContext: EditorActionContext) {
        editorActionContext.editor.copyText()
    }

    override fun getIcon(): Icon = Icon.ResourceIcon(drawables.copy)

    override val defaultKeybinds: KeyCombination = KeyCombination(keyCode = KeyEvent.KEYCODE_C, ctrl = true)
}
