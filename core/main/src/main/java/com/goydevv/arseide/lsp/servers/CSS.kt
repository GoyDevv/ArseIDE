package com.goydevv.arseide.lsp.servers

import android.content.Context
import com.goydevv.arseide.exec.NpmUtils
import com.goydevv.arseide.exec.isTerminalInstalled
import com.goydevv.arseide.file.BuiltinFileType
import com.goydevv.arseide.file.child
import com.goydevv.arseide.file.localBinDir
import com.goydevv.arseide.file.sandboxDir
import com.goydevv.arseide.lsp.LspConnectionConfig
import com.goydevv.arseide.lsp.ScriptedLspServer

object CSS : ScriptedLspServer() {
    override val id: String = "css"
    override val languageName: String = "CSS"
    override val serverName = "vscode-css-language-server"
    override val supportedExtensions =
        BuiltinFileType.CSS.extensions + BuiltinFileType.SCSS.extensions + BuiltinFileType.LESS.extensions
    override val icon = BuiltinFileType.CSS.icon

    override val installScript = localBinDir().child("lsp/css")
    override val installId = "CSS language server"

    override suspend fun isInstalled(context: Context): Boolean {
        if (!isTerminalInstalled()) {
            return false
        }

        return sandboxDir().child("/usr/bin/$serverName").exists()
    }

    override suspend fun isUpdatable(context: Context): Boolean {
        return NpmUtils.hasUpdate("vscode-langservers-extracted")
    }

    override fun getConnectionConfig(): LspConnectionConfig {
        return LspConnectionConfig.Process(arrayOf("/usr/bin/node", "/usr/bin/$serverName", "--stdio"))
    }
}
