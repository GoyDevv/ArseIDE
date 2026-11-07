package com.goydevv.arseide.commands.editor

import com.goydevv.arseide.commands.EditorActionContext
import com.goydevv.arseide.commands.EditorCommand
import com.goydevv.arseide.icons.Icon
import com.goydevv.arseide.resources.drawables
import com.goydevv.arseide.resources.getString
import com.goydevv.arseide.resources.strings

class UpperCaseCommand : EditorCommand() {
    override val id: String = "editor.uppercase"

    override fun getLabel(): String = strings.transform_uppercase.getString()

    override fun action(editorActionContext: EditorActionContext) {
        val editor = editorActionContext.editor
        if (editor.isTextSelected) {
            val selectionStart = editor.cursorRange.startIndex
            val selectionEnd = editor.cursorRange.endIndex
            val selectionText = editor.text.substring(selectionStart, selectionEnd)
            editor.text.replace(selectionStart, selectionEnd, selectionText.uppercase())
        }
    }

    override fun getIcon(): Icon = Icon.ResourceIcon(drawables.letters)
}
