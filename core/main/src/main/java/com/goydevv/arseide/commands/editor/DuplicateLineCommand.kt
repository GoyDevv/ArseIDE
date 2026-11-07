package com.goydevv.arseide.commands.editor

import android.view.KeyEvent
import com.goydevv.arseide.commands.EditorActionContext
import com.goydevv.arseide.commands.EditorCommand
import com.goydevv.arseide.commands.EditorNonActionContext
import com.goydevv.arseide.commands.KeyCombination
import com.goydevv.arseide.icons.Icon
import com.goydevv.arseide.resources.drawables
import com.goydevv.arseide.resources.getString
import com.goydevv.arseide.resources.strings

class DuplicateLineCommand : EditorCommand() {
    override val id: String = "editor.duplicate_line"

    override fun getLabel(): String = strings.duplicate_line.getString()

    override fun action(editorActionContext: EditorActionContext) {
        editorActionContext.editor.duplicateLine()
    }

    override fun isEnabled(editorNonActionContext: EditorNonActionContext): Boolean {
        return editorNonActionContext.editorTab.editorState.editable
    }

    override fun getIcon(): Icon = Icon.ResourceIcon(drawables.duplicate_line)

    override val defaultKeybinds: KeyCombination = KeyCombination(keyCode = KeyEvent.KEYCODE_D, ctrl = true)
}
