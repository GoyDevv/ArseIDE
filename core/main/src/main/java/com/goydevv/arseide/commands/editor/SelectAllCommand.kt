package com.goydevv.arseide.commands.editor

import android.view.KeyEvent
import com.goydevv.arseide.commands.EditorActionContext
import com.goydevv.arseide.commands.EditorCommand
import com.goydevv.arseide.commands.KeyCombination
import com.goydevv.arseide.icons.Icon
import com.goydevv.arseide.resources.drawables
import com.goydevv.arseide.resources.getString
import com.goydevv.arseide.resources.strings

class SelectAllCommand : EditorCommand() {
    override val id: String = "editor.select_all"

    override fun getLabel(): String = strings.select_all.getString()

    override fun action(editorActionContext: EditorActionContext) {
        editorActionContext.editor.selectAll()
    }

    override fun getIcon(): Icon = Icon.ResourceIcon(drawables.select_all)

    override val defaultKeybinds: KeyCombination = KeyCombination(keyCode = KeyEvent.KEYCODE_A, ctrl = true)
}
