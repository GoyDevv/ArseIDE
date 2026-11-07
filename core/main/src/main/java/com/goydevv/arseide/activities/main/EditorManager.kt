package com.goydevv.arseide.activities.main

import androidx.lifecycle.viewModelScope
import com.goydevv.arseide.file.FileObject
import com.goydevv.arseide.resources.getString
import com.goydevv.arseide.resources.strings
import com.goydevv.arseide.settings.Settings
import com.goydevv.arseide.tabs.base.TabRegistry
import com.goydevv.arseide.tabs.editor.EditorTab
import com.goydevv.arseide.utils.dialogRes
import com.goydevv.arseide.utils.expectOOM
import io.github.rosemoe.sora.event.SelectionChangeEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class EditorManager(private val viewModel: MainViewModel) {

    fun createEditorTab(file: FileObject, projectRoot: FileObject?): EditorTab {
        return EditorTab(file = file, projectRoot = projectRoot, viewModel = viewModel)
    }

    fun addEditorTab(file: FileObject, projectRoot: FileObject?, switchToTab: Boolean, checkDuplicate: Boolean = true) {
        val editorTab = createEditorTab(file, projectRoot)
        viewModel.tabManager.addTab(editorTab, switchToTab, checkDuplicate)
    }

    suspend fun jumpToPosition(
        file: FileObject,
        projectRoot: FileObject?,
        lineStart: Int,
        charStart: Int,
        lineEnd: Int,
        charEnd: Int,
    ) {
        withContext(Dispatchers.Main) { openFile(file, projectRoot = projectRoot, switchToTab = true) }

        val targetTab = viewModel.tabs.filterIsInstance<EditorTab>().find { it.file == file } ?: return

        // Wait until editor content is loaded
        targetTab.editorState.contentRendered.await()

        withContext(Dispatchers.Main) {
            targetTab.editorState.editor
                .get()
                ?.setSelectionRegion(lineStart, charStart, lineEnd, charEnd, SelectionChangeEvent.CAUSE_SEARCH)

            targetTab.editorState.editor.get()?.ensureSelectionVisible()
        }
    }

    suspend fun openFile(
        fileObject: FileObject,
        projectRoot: FileObject?,
        switchToTab: Boolean,
        checkDuplicate: Boolean = true,
    ) {
        val function = suspend {
            val tab = TabRegistry.getTab(fileObject, projectRoot, viewModel)
            withContext(Dispatchers.Main) { viewModel.tabManager.addTab(tab, switchToTab, checkDuplicate) }
        }

        if (Settings.oom_prediction && expectOOM(fileObject.length())) {
            dialogRes(
                title = strings.attention.getString(),
                msg = strings.tab_memory_warning.getString(),
                okRes = strings.continue_action,
                onOk = { viewModel.viewModelScope.launch { function.invoke() } },
            )
        } else {
            function.invoke()
        }
    }
}
