package com.goydevv.arseide.commands.global

import android.view.KeyEvent
import com.goydevv.arseide.commands.ActionContext
import com.goydevv.arseide.commands.GlobalCommand
import com.goydevv.arseide.commands.KeyCombination
import com.goydevv.arseide.icons.Icon
import com.goydevv.arseide.icons.Menu_book
import com.goydevv.arseide.icons.XedIcons
import com.goydevv.arseide.resources.getString
import com.goydevv.arseide.resources.strings
import com.goydevv.arseide.utils.openUrl

class DocumentationCommand : GlobalCommand() {
    override val id: String = "global.documentation"

    override fun getLabel(): String = strings.docs.getString()

    override fun getIcon(): Icon = Icon.VectorIcon(XedIcons.Menu_book)

    override fun action(actionContext: ActionContext) {
        val url = "https://xed-editor.github.io/Xed-Docs/"
        actionContext.currentActivity.openUrl(url)
    }

    override val defaultKeybinds: KeyCombination = KeyCombination(keyCode = KeyEvent.KEYCODE_F1)
}
