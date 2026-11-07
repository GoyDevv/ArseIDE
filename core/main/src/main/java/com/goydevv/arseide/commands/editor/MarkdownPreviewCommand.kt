package com.goydevv.arseide.commands.editor

import com.goydevv.arseide.commands.CommandProvider
import com.goydevv.arseide.commands.EditorActionContext
import com.goydevv.arseide.commands.EditorCommand
import com.goydevv.arseide.commands.EditorNonActionContext
import com.goydevv.arseide.icons.Icon
import com.goydevv.arseide.resources.drawables
import com.goydevv.arseide.resources.getString
import com.goydevv.arseide.resources.strings
import com.goydevv.arseide.runner.runners.web.markdown.MarkdownRunner
import com.goydevv.arseide.tabs.markdown.MarkdownPreviewTab

/**
 * Opens a native, themed Markdown preview ([MarkdownPreviewTab]) for the current file in a new tab.
 *
 * Only shown for Markdown files. Saves first so the preview reflects unsaved edits. Reuses an
 * already-open preview for the same file instead of stacking duplicates.
 */
class MarkdownPreviewCommand : EditorCommand() {
    override val id: String = "editor.markdown_preview"

    override fun getLabel(): String = strings.markdown_preview.getString()

    override fun action(editorActionContext: EditorActionContext) {
        CommandProvider.SaveCommand.action(editorActionContext)
        val file = editorActionContext.editorTab.file
        val viewModel = commandContext.mainViewModel
        val existing = viewModel.tabs.indexOfFirst { it is MarkdownPreviewTab && it.file == file }
        if (existing != -1) {
            viewModel.tabManager.setCurrentTab(existing)
        } else {
            viewModel.tabManager.addTab(MarkdownPreviewTab(file), switchToTab = true, checkDuplicate = false)
        }
    }

    override fun isSupported(editorNonActionContext: EditorNonActionContext): Boolean {
        return MarkdownRunner.matcher(editorNonActionContext.editorTab.file)
    }

    override fun getIcon(): Icon = Icon.ResourceIcon(drawables.eye)
}
