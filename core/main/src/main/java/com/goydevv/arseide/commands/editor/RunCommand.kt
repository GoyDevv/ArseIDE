package com.goydevv.arseide.commands.editor

import android.view.KeyEvent
import com.goydevv.arseide.DefaultScope
import com.goydevv.arseide.commands.CommandProvider
import com.goydevv.arseide.commands.EditorActionContext
import com.goydevv.arseide.commands.EditorCommand
import com.goydevv.arseide.commands.EditorNonActionContext
import com.goydevv.arseide.commands.KeyCombination
import com.goydevv.arseide.icons.Icon
import com.goydevv.arseide.resources.drawables
import com.goydevv.arseide.resources.getString
import com.goydevv.arseide.resources.strings
import com.goydevv.arseide.runner.ProjectRunner
import com.goydevv.arseide.runner.RunOutputState
import com.goydevv.arseide.settings.Settings
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.launch

/**
 * The editor "Run" (play) button. It is **project-aware**: visibility and behaviour are driven by
 * the type of the project the open file belongs to (see [ProjectRunner]).
 *
 *  - Shown for Python, Node, Rust, Go, static Web, Android, and Fabric/Forge/Gradle projects.
 *  - Hidden only for projects the editor can't identify.
 *
 * Clicking it runs (or builds) the project from its own folder in the sandbox (in the background),
 * or opens the in-app HTML preview for static web projects.
 *
 * **Single build / Stop:** only one build started by this button may run at a time. While such a
 * build is running the button turns into a Stop button (across every editor tab) and clicking it
 * kills the running build instead of starting another. Manually opened terminals are unaffected.
 */
@OptIn(DelicateCoroutinesApi::class)
class RunCommand : EditorCommand() {
    override val id: String = "editor.run"

    override fun getLabel(): String =
        if (RunOutputState.isRunning) strings.stop.getString() else strings.run.getString()

    override fun action(editorActionContext: EditorActionContext) {
        // A build is already running → this button acts as Stop and kills it instantly.
        if (RunOutputState.isRunning) {
            RunOutputState.stop()
            return
        }

        val editorTab = editorActionContext.editorTab
        val activity = editorActionContext.currentActivity
        CommandProvider.SaveCommand.action(editorActionContext)
        DefaultScope.launch {
            Settings.runs += 1
            ProjectRunner.run(activity = activity, projectRoot = editorTab.projectRoot, file = editorTab.file)
        }
    }

    override fun isSupported(editorNonActionContext: EditorNonActionContext): Boolean {
        // Keep the (Stop) button available on any editor tab while a build is running so it can be
        // stopped from anywhere.
        if (RunOutputState.isRunning) return true
        val tab = editorNonActionContext.editorTab
        val rootPath = ProjectRunner.resolveProjectRootPath(tab.projectRoot, tab.file)
        return ProjectRunner.canRun(rootPath)
    }

    override fun getIcon(): Icon =
        if (RunOutputState.isRunning) Icon.ResourceIcon(drawables.stop) else Icon.ResourceIcon(drawables.run)

    override val defaultKeybinds: KeyCombination = KeyCombination(keyCode = KeyEvent.KEYCODE_F5)
}
