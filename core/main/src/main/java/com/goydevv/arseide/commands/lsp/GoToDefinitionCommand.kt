package com.goydevv.arseide.commands.lsp

import com.goydevv.arseide.DefaultScope
import com.goydevv.arseide.commands.LspActionContext
import com.goydevv.arseide.commands.LspCommand
import com.goydevv.arseide.commands.LspNonActionContext
import com.goydevv.arseide.icons.Icon
import com.goydevv.arseide.lsp.goToDefinition
import com.goydevv.arseide.resources.drawables
import com.goydevv.arseide.resources.getString
import com.goydevv.arseide.resources.strings

class GoToDefinitionCommand : LspCommand() {
    override val id: String = "lsp.go_to_definition"

    override fun getLabel(): String = strings.go_to_definition.getString()

    override fun action(lspActionContext: LspActionContext) {
        goToDefinition(
            scope = DefaultScope,
            context = lspActionContext.currentActivity,
            viewModel = commandContext.mainViewModel,
            editorTab = lspActionContext.editorTab,
        )
    }

    override fun isSupported(lspNonActionContext: LspNonActionContext): Boolean {
        return lspNonActionContext.lspConnector.isGoToDefinitionSupported()
    }

    override fun getIcon(): Icon = Icon.ResourceIcon(drawables.jump_to_element)
}
