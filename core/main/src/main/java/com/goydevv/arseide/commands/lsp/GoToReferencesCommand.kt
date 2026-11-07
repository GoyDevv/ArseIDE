package com.goydevv.arseide.commands.lsp

import com.goydevv.arseide.DefaultScope
import com.goydevv.arseide.commands.LspActionContext
import com.goydevv.arseide.commands.LspCommand
import com.goydevv.arseide.commands.LspNonActionContext
import com.goydevv.arseide.icons.Icon
import com.goydevv.arseide.lsp.goToReferences
import com.goydevv.arseide.resources.drawables
import com.goydevv.arseide.resources.getString
import com.goydevv.arseide.resources.strings

class GoToReferencesCommand : LspCommand() {
    override val id: String = "lsp.go_to_references"

    override fun getLabel(): String = strings.go_to_references.getString()

    override fun action(lspActionContext: LspActionContext) {
        goToReferences(
            scope = DefaultScope,
            context = lspActionContext.currentActivity,
            viewModel = commandContext.mainViewModel,
            editorTab = lspActionContext.editorTab,
        )
    }

    override fun isSupported(lspNonActionContext: LspNonActionContext): Boolean {
        return lspNonActionContext.lspConnector.isGoToReferencesSupported()
    }

    override fun getIcon(): Icon = Icon.ResourceIcon(drawables.manage_search)
}
