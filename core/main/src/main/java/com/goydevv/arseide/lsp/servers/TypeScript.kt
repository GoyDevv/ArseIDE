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

object TypeScript : ScriptedLspServer() {
    override val id: String = "typescript"
    override val languageName: String = "TypeScript"
    override val serverName = "typescript-language-server"
    override val supportedExtensions =
        BuiltinFileType.JAVASCRIPT.extensions +
            BuiltinFileType.TYPESCRIPT.extensions +
            BuiltinFileType.JSX.extensions +
            BuiltinFileType.TSX.extensions
    override val icon = BuiltinFileType.TYPESCRIPT.icon

    override val installScript = localBinDir().child("lsp/typescript")
    override val installId = "TypeScript language server"

    override suspend fun isInstalled(context: Context): Boolean {
        if (!isTerminalInstalled()) {
            return false
        }

        return sandboxDir().child("/usr/bin/$serverName").exists()
    }

    override suspend fun isUpdatable(context: Context): Boolean {
        return NpmUtils.hasUpdate(serverName)
    }

    override fun getConnectionConfig(): LspConnectionConfig {
        return LspConnectionConfig.Process(arrayOf("/usr/bin/node", "/usr/bin/$serverName", "--stdio"))
    }
}
