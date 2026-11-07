package com.goydevv.arseide.commands.editor

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.goydevv.arseide.DefaultScope
import com.goydevv.arseide.commands.EditorActionContext
import com.goydevv.arseide.commands.EditorCommand
import com.goydevv.arseide.file.FileWrapper
import com.goydevv.arseide.icons.Icon
import com.goydevv.arseide.resources.drawables
import com.goydevv.arseide.resources.getString
import com.goydevv.arseide.resources.strings
import com.goydevv.arseide.utils.toast
import kotlinx.coroutines.launch

class ShareCommand : EditorCommand() {
    override val id: String = "editor.share"

    override fun getLabel(): String = strings.share.getString()

    override fun action(editorActionContext: EditorActionContext) {
        val activity = editorActionContext.currentActivity
        val file = editorActionContext.editorTab.file

        DefaultScope.launch {
            if (file.getAbsolutePath().contains(activity.filesDir.parentFile!!.absolutePath)) {
                toast(strings.permission_denied)
                return@launch
            }

            val fileUri =
                if (file is FileWrapper) {
                    FileProvider.getUriForFile(activity as Context, "${activity.packageName}.fileprovider", file.file)
                } else {
                    file.toUri()
                }

            val intent =
                Intent(Intent.ACTION_SEND).apply {
                    type = activity.contentResolver.getType(fileUri) ?: "*/*"
                    setDataAndType(fileUri, activity.contentResolver.getType(fileUri) ?: "*/*")
                    putExtra(Intent.EXTRA_STREAM, fileUri)
                    flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
                }

            activity.startActivity(Intent.createChooser(intent, "Share file"))
        }
    }

    override fun getIcon(): Icon = Icon.ResourceIcon(drawables.send)
}
