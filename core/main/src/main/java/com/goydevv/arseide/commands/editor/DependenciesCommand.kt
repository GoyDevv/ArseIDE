package com.goydevv.arseide.commands.editor

import com.goydevv.arseide.commands.EditorActionContext
import com.goydevv.arseide.commands.EditorCommand
import com.goydevv.arseide.commands.EditorNonActionContext
import com.goydevv.arseide.icons.Icon
import com.goydevv.arseide.resources.drawables
import com.goydevv.arseide.resources.getString
import com.goydevv.arseide.resources.strings

/**
 * Opens the project [com.goydevv.arseide.projects.DependenciesDialog] for the current file's project, which
 * detects the project type and lets the user download required tools (JDKs, Node, Python, …).
 *
 * Shown whenever an editor tab is open. The dialog itself handles detection and installation.
 */
class DependenciesCommand : EditorCommand() {
    override val id: String = "editor.dependencies"

    override fun getLabel(): String = strings.dependencies.getString()

    override fun action(editorActionContext: EditorActionContext) {
        val root =
            (com.goydevv.arseide.activities.main.MainActivity.instance?.drawerViewModel?.currentDrawerTab as? com.goydevv.arseide.filetree.FileTreeTab)
                ?.root
                ?.getAbsolutePath()
                ?: return
        com.goydevv.arseide.projects.DependencyManagerState.open(java.io.File(root))
    }

    override fun isSupported(editorNonActionContext: EditorNonActionContext): Boolean = true

    override fun getIcon(): Icon = Icon.ResourceIcon(drawables.download)
}
