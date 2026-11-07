package com.goydevv.arseide.commands.editor

import android.view.KeyEvent
import com.goydevv.arseide.commands.EditorActionContext
import com.goydevv.arseide.commands.EditorCommand
import com.goydevv.arseide.commands.KeyCombination
import com.goydevv.arseide.icons.Icon
import com.goydevv.arseide.resources.drawables
import com.goydevv.arseide.resources.getString
import com.goydevv.arseide.resources.strings
import com.goydevv.arseide.utils.dialogRes

class RefreshCommand : EditorCommand() {
    override val id: String = "editor.refresh"

    override fun getLabel(): String = strings.refresh.getString()

    override fun action(editorActionContext: EditorActionContext) {
        val currentTab = editorActionContext.editorTab
        if (currentTab.editorState.isDirty) {
            dialogRes(
                activity = editorActionContext.currentActivity,
                title = strings.attention.getString(),
                msg = strings.ask_refresh.getString(),
                okRes = strings.refresh,
                onCancel = {},
                onOk = { currentTab.refresh() },
            )
        } else {
            currentTab.refresh()
        }
    }

    override fun getIcon(): Icon = Icon.ResourceIcon(drawables.refresh)

    override val defaultKeybinds: KeyCombination =
        KeyCombination(keyCode = KeyEvent.KEYCODE_R, ctrl = true, shift = true)
}
