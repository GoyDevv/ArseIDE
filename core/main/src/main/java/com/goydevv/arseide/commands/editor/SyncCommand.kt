package com.goydevv.arseide.commands.editor

import com.goydevv.arseide.DefaultScope
import com.goydevv.arseide.commands.EditorActionContext
import com.goydevv.arseide.commands.EditorCommand
import com.goydevv.arseide.commands.EditorNonActionContext
import com.goydevv.arseide.icons.Icon
import com.goydevv.arseide.projects.DetectedProjectType
import com.goydevv.arseide.resources.drawables
import com.goydevv.arseide.resources.getString
import com.goydevv.arseide.resources.strings
import com.goydevv.arseide.runner.ProjectRunner
import com.goydevv.arseide.runner.RunOutputState
import java.io.File
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.launch

/**
 * Android-only "Gradle Sync" button, shown right beside Run. Downloads dependencies and configures
 * the build — the one-time step Android Studio calls "Sync". Runs in the background via
 * [ProjectRunner.sync], surfaced in the floating build view. Hidden while a build/sync is running.
 */
@OptIn(DelicateCoroutinesApi::class)
class SyncCommand : EditorCommand() {
    override val id: String = "editor.sync"

    override fun getLabel(): String = strings.gradle_sync.getString()

    override fun action(editorActionContext: EditorActionContext) {
        if (RunOutputState.isRunning) return
        val editorTab = editorActionContext.editorTab
        val activity = editorActionContext.currentActivity
        DefaultScope.launch { ProjectRunner.sync(activity, editorTab.projectRoot, editorTab.file) }
    }

    override fun isSupported(editorNonActionContext: EditorNonActionContext): Boolean {
        if (RunOutputState.isRunning) return false
        val tab = editorNonActionContext.editorTab
        val rootPath = ProjectRunner.resolveProjectRootPath(tab.projectRoot, tab.file) ?: return false
        if (!File(rootPath).isDirectory) return false
        return ProjectRunner.detect(rootPath) == DetectedProjectType.ANDROID
    }

    override fun getIcon(): Icon = Icon.ResourceIcon(drawables.refresh)
}
