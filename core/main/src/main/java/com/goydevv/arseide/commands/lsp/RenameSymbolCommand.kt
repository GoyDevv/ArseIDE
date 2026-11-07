package com.goydevv.arseide.commands.lsp

import com.goydevv.arseide.DefaultScope
import com.goydevv.arseide.commands.LspActionContext
import com.goydevv.arseide.commands.LspCommand
import com.goydevv.arseide.commands.LspNonActionContext
import com.goydevv.arseide.icons.Icon
import com.goydevv.arseide.lsp.renameSymbol
import com.goydevv.arseide.resources.drawables
import com.goydevv.arseide.resources.getString
import com.goydevv.arseide.resources.strings

class RenameSymbolCommand : LspCommand() {
    override val id: String = "lsp.rename_symbol"

    override fun getLabel(): String = strings.rename_symbol.getString()

    override fun action(lspActionContext: LspActionContext) {
        renameSymbol(DefaultScope, lspActionContext.editorTab)
    }

    override fun isSupported(lspNonActionContext: LspNonActionContext): Boolean {
        return lspNonActionContext.lspConnector.isRenameSymbolSupported()
    }

    override fun getIcon(): Icon = Icon.ResourceIcon(drawables.manage_search)
}
