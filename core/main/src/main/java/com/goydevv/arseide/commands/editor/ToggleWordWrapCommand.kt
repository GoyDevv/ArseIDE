package com.goydevv.arseide.commands.editor

import android.view.KeyEvent
import com.goydevv.arseide.commands.EditorActionContext
import com.goydevv.arseide.commands.EditorCommand
import com.goydevv.arseide.commands.KeyCombination
import com.goydevv.arseide.icons.Icon
import com.goydevv.arseide.resources.drawables
import com.goydevv.arseide.resources.getString
import com.goydevv.arseide.resources.strings

class ToggleWordWrapCommand : EditorCommand() {
    override val id: String = "editor.toggle_word_wrap"

    override fun getLabel(): String = strings.toggle_word_wrap.getString()

    override fun action(editorActionContext: EditorActionContext) {
        val editor = editorActionContext.editor
        editor.setWordwrap(!editor.isWordwrap, true, true)
    }

    override fun getIcon(): Icon = Icon.ResourceIcon(drawables.edit_note)

    override val defaultKeybinds: KeyCombination = KeyCombination(keyCode = KeyEvent.KEYCODE_Z, alt = true)
}
