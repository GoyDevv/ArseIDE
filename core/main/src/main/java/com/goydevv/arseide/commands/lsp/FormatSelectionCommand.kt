package com.goydevv.arseide.commands.lsp

import com.goydevv.arseide.DefaultScope
import com.goydevv.arseide.commands.LspActionContext
import com.goydevv.arseide.commands.LspCommand
import com.goydevv.arseide.commands.LspNonActionContext
import com.goydevv.arseide.icons.Icon
import com.goydevv.arseide.lsp.formatDocumentRange
import com.goydevv.arseide.resources.drawables
import com.goydevv.arseide.resources.getString
import com.goydevv.arseide.resources.strings

class FormatSelectionCommand : LspCommand() {
    override val id: String = "lsp.format_selection"

    override fun getLabel(): String = strings.format_selection.getString()

    override fun action(lspActionContext: LspActionContext) {
        formatDocumentRange(DefaultScope, lspActionContext.editorTab)
    }

    override fun isSupported(lspNonActionContext: LspNonActionContext): Boolean {
        return lspNonActionContext.lspConnector.isRangeFormattingSupported()
    }

    override fun getIcon(): Icon = Icon.ResourceIcon(drawables.auto_fix)
}
