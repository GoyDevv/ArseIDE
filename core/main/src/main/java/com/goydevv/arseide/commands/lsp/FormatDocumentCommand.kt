package com.goydevv.arseide.commands.lsp

import com.goydevv.arseide.DefaultScope
import com.goydevv.arseide.commands.LspActionContext
import com.goydevv.arseide.commands.LspCommand
import com.goydevv.arseide.commands.LspNonActionContext
import com.goydevv.arseide.icons.Icon
import com.goydevv.arseide.lsp.formatDocument
import com.goydevv.arseide.resources.drawables
import com.goydevv.arseide.resources.getString
import com.goydevv.arseide.resources.strings

class FormatDocumentCommand : LspCommand() {
    override val id: String = "lsp.format_document"

    override fun getLabel(): String = strings.format_document.getString()

    override fun action(lspActionContext: LspActionContext) {
        formatDocument(DefaultScope, lspActionContext.editorTab)
    }

    override fun isSupported(lspNonActionContext: LspNonActionContext): Boolean {
        return lspNonActionContext.lspConnector.isFormattingSupported()
    }

    override fun getIcon(): Icon = Icon.ResourceIcon(drawables.auto_fix)
}
