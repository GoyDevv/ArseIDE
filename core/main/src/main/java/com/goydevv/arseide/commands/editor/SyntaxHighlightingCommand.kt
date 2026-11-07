package com.goydevv.arseide.commands.editor

import com.goydevv.arseide.commands.Command
import com.goydevv.arseide.commands.EditorActionContext
import com.goydevv.arseide.commands.EditorCommand
import com.goydevv.arseide.file.FileTypeManager
import com.goydevv.arseide.icons.Icon
import com.goydevv.arseide.resources.drawables
import com.goydevv.arseide.resources.getString
import com.goydevv.arseide.resources.strings

class SyntaxHighlightingCommand : EditorCommand() {
    override val id: String = "editor.syntax_highlighting"

    override fun getLabel(): String = strings.highlighting.getString()

    override fun action(editorActionContext: EditorActionContext) {}

    override fun getIcon(): Icon = Icon.ResourceIcon(drawables.edit_note)

    override val childCommands: List<Command> by lazy {
        FileTypeManager.allTypes()
            .filter { it.textmateScope != null }
            .map { fileType ->
                object : EditorCommand() {
                    override val id: String = "editor.syntax_highlighting.${fileType.name.lowercase()}"

                    override fun getLabel(): String = fileType.title

                    override fun action(editorActionContext: EditorActionContext) {
                        editorActionContext.editorTab.editorState.textmateScope = fileType.textmateScope!!
                    }

                    override fun getIcon(): Icon = fileType.getResolvedIcon()
                }
            }
    }

    override fun getChildSearchPlaceholder(): String = strings.select_language.getString()
}
