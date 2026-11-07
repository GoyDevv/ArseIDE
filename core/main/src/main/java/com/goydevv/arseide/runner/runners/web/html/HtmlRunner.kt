package com.goydevv.arseide.runner.runners.web.html

import android.app.Activity
import android.content.Context
import android.content.Intent
import androidx.browser.customtabs.CustomTabsIntent
import androidx.core.net.toUri
import com.goydevv.arseide.activities.settings.SettingsRoutes
import com.goydevv.arseide.activities.settings.settingsNavController
import com.goydevv.arseide.file.BuiltinFileType
import com.goydevv.arseide.file.FileObject
import com.goydevv.arseide.icons.Icon
import com.goydevv.arseide.resources.getFilledString
import com.goydevv.arseide.resources.getString
import com.goydevv.arseide.resources.strings
import com.goydevv.arseide.runner.Runner
import com.goydevv.arseide.runner.runners.web.HttpServer
import com.goydevv.arseide.settings.Settings
import com.goydevv.arseide.utils.toast
import java.net.BindException
import java.net.URLEncoder

object HtmlRunner : Runner() {

    var httpServer: HttpServer? = null

    override val id = "html_preview"
    override val label = strings.html_preview.getString()
    override val description = strings.html_preview_desc.getString()

    override fun matcher(fileObject: FileObject): Boolean {
        val htmlExtensions = BuiltinFileType.HTML.extensions.joinToString("|")
        return Regex(".*\\.($htmlExtensions|svg)$").matches(fileObject.getName())
    }

    override val onConfigure: () -> Unit = { settingsNavController.get()?.navigate(SettingsRoutes.HtmlRunner.route) }

    override suspend fun run(activity: Activity, fileObject: FileObject) {
        stop()

        val port = Settings.http_server_port
        try {
            httpServer = HttpServer(activity, port, fileObject.getParentFile() ?: fileObject)
        } catch (_: BindException) {
            toast(strings.http_server_port_error.getFilledString(port))
            return
        }

        val address = "http://localhost:$port"
        toast(strings.http_server_at.getFilledString(address))

        val encodedName = URLEncoder.encode(fileObject.getName(), "UTF-8").replace("+", "%20")
        val url = "$address/$encodedName"
        if (Settings.launch_in_browser) {
            val intent = Intent(Intent.ACTION_VIEW, url.toUri())
            activity.startActivity(intent)
            return
        }
        CustomTabsIntent.Builder()
            .setShowTitle(true)
            .setShareState(CustomTabsIntent.SHARE_STATE_OFF)
            .build()
            .launchUrl(activity, url.toUri())
    }

    override fun getIcon(context: Context): Icon? {
        return BuiltinFileType.HTML.icon
    }

    override suspend fun isRunning(): Boolean = httpServer?.isAlive == true

    override suspend fun stop() {
        if (isRunning()) {
            httpServer?.closeAllConnections()
            httpServer?.stop()
        }
        httpServer = null
    }
}
