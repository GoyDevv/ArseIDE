package com.goydevv.arseide.commands.editor

import android.view.KeyEvent
import com.goydevv.arseide.commands.EditorActionContext
import com.goydevv.arseide.commands.EditorCommand
import com.goydevv.arseide.commands.KeyCombination
import com.goydevv.arseide.icons.Icon
import com.goydevv.arseide.resources.drawables
import com.goydevv.arseide.resources.getString
import com.goydevv.arseide.resources.strings

class SelectWordCommand : EditorCommand() {
    override val id: String = "editor.select_word"

    override fun getLabel(): String = strings.select_word.getString()

    override fun action(editorActionContext: EditorActionContext) {
        editorActionContext.editor.selectCurrentWord()
    }

    override fun getIcon(): Icon = Icon.ResourceIcon(drawables.select)

    override val defaultKeybinds: KeyCombination = KeyCombination(keyCode = KeyEvent.KEYCODE_W, ctrl = true)
}
