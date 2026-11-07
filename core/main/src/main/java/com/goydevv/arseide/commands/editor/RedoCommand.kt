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

class RedoCommand : EditorCommand() {
    override val id: String = "editor.redo"

    override fun getLabel(): String = strings.redo.getString()

    override fun action(editorActionContext: EditorActionContext) {
        val editor = editorActionContext.editor
        if (editor.canRedo()) editor.redo()
        editorActionContext.editorTab.editorState.updateUndoRedo()
    }

    override fun isEnabled(editorNonActionContext: EditorNonActionContext): Boolean {
        val editorState = editorNonActionContext.editorTab.editorState
        return editorState.editable && editorState.canRedo
    }

    override fun getIcon(): Icon = Icon.ResourceIcon(drawables.redo)

    override val defaultKeybinds: KeyCombination = KeyCombination(keyCode = KeyEvent.KEYCODE_Y, ctrl = true)
}
