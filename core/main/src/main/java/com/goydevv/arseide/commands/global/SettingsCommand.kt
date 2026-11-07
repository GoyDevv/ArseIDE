package com.goydevv.arseide.commands.global

import android.content.Intent
import android.view.KeyEvent
import com.goydevv.arseide.activities.settings.SettingsActivity
import com.goydevv.arseide.commands.ActionContext
import com.goydevv.arseide.commands.GlobalCommand
import com.goydevv.arseide.commands.KeyCombination
import com.goydevv.arseide.icons.Icon
import com.goydevv.arseide.resources.drawables
import com.goydevv.arseide.resources.getString
import com.goydevv.arseide.resources.strings

class SettingsCommand : GlobalCommand() {
    override val id: String = "global.settings"

    override fun getLabel(): String = strings.settings.getString()

    override fun action(actionContext: ActionContext) {
        val activity = actionContext.currentActivity
        activity.startActivity(Intent(activity, SettingsActivity::class.java))
    }

    override fun getIcon(): Icon = Icon.ResourceIcon(drawables.settings)

    override val defaultKeybinds: KeyCombination = KeyCombination(keyCode = KeyEvent.KEYCODE_COMMA, ctrl = true)
}
