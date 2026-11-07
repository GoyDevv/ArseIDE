package com.goydevv.arseide.commands.global

import android.content.Intent
import android.view.KeyEvent
import com.goydevv.arseide.activities.main.MainActivity
import com.goydevv.arseide.activities.terminal.Terminal
import com.goydevv.arseide.commands.ActionContext
import com.goydevv.arseide.commands.GlobalCommand
import com.goydevv.arseide.commands.KeyCombination
import com.goydevv.arseide.file.FileWrapper
import com.goydevv.arseide.filetree.FileTreeTab
import com.goydevv.arseide.icons.Icon
import com.goydevv.arseide.resources.drawables
import com.goydevv.arseide.resources.getString
import com.goydevv.arseide.resources.strings
import com.goydevv.arseide.runner.ProjectRunner
import com.goydevv.arseide.settings.app.InbuiltFeatures
import com.goydevv.arseide.tabs.editor.EditorTab

class TerminalCommand : GlobalCommand() {
    override val id: String = "global.terminal"

    override fun getLabel(): String = strings.terminal.getString()

    override fun action(actionContext: ActionContext) {
        val activity = actionContext.currentActivity
        val intent = Intent(activity, Terminal::class.java)
        // When opening a brand-new terminal, start it in the current project/file directory using
        // the same resolution the Run button uses, instead of always defaulting to the sandbox
        // home. If a terminal session is already open it is reused as before and this is ignored.
        currentProjectDir()?.let { intent.putExtra("cwd", it) }
        activity.startActivity(intent)
    }

    /** The current editor tab's project directory, or the drawer-selected folder, as a sandbox path. */
    private fun currentProjectDir(): String? {
        val activity = MainActivity.instance ?: return null
        // 1. An editor tab is open — use its project/file directory (same as the Run button).
        (activity.viewModel.tabManager?.currentTab as? EditorTab)?.let { tab ->
            ProjectRunner.resolveProjectRootPath(tab.projectRoot, tab.file)?.let {
                return ProjectRunner.toSandboxPath(it)
            }
        }
        // 2. No file open — fall back to the directory selected in the drawer's file tree, so the
        //    terminal still opens in the project instead of the sandbox home.
        val drawerRoot = (activity.drawerViewModel.currentDrawerTab as? FileTreeTab)?.root
        if (drawerRoot is FileWrapper) {
            return ProjectRunner.toSandboxPath(drawerRoot.getAbsolutePath())
        }
        return null
    }

    override fun isSupported(): Boolean = InbuiltFeatures.terminal.state.value

    override fun getIcon(): Icon = Icon.ResourceIcon(drawables.terminal)

    override val defaultKeybinds: KeyCombination = KeyCombination(keyCode = KeyEvent.KEYCODE_J, ctrl = true)
}
