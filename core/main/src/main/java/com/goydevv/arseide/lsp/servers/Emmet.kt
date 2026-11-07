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

object Emmet : ScriptedLspServer() {
    override val id: String = "emmet"
    override val languageName: String = "Emmet"
    override val serverName = "emmet-language-server"
    override val supportedExtensions = BuiltinFileType.HTML.extensions + BuiltinFileType.HTMX.extensions
    override val icon = BuiltinFileType.HTML.icon

    override val installScript = localBinDir().child("lsp/emmet")
    override val installId = "Emmet language server"

    override suspend fun isInstalled(context: Context): Boolean {
        if (!isTerminalInstalled()) {
            return false
        }

        return sandboxDir().child("/usr/bin/$serverName").exists()
    }

    override suspend fun isUpdatable(context: Context): Boolean {
        return NpmUtils.hasUpdate("@olrtg/emmet-language-server")
    }

    override fun getConnectionConfig(): LspConnectionConfig {
        return LspConnectionConfig.Process(arrayOf("/usr/bin/node", "/usr/bin/$serverName", "--stdio"))
    }
}
