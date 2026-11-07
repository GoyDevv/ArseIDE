package com.goydevv.arseide.tabs.base

import com.goydevv.arseide.activities.main.MainViewModel
import com.goydevv.arseide.extension.api.XedExtensionPoint
import com.goydevv.arseide.file.BuiltinFileType
import com.goydevv.arseide.file.FileObject
import com.goydevv.arseide.file.FileTypeManager
import com.goydevv.arseide.tabs.image.ImageTab

@XedExtensionPoint
fun interface TabFactory {
    fun createTab(file: FileObject, projectRoot: FileObject?, viewModel: MainViewModel): Tab
}

object TabRegistry {
    private val registeredTabs = mutableMapOf<String, TabFactory>()

    @XedExtensionPoint
    fun registerTab(tabFactory: TabFactory, fileExtensions: List<String>) {
        fileExtensions.forEach { registeredTabs[it] = tabFactory }
    }

    @XedExtensionPoint
    fun unregisterTab(tabFactory: TabFactory) {
        registeredTabs.values.remove(tabFactory)
    }

    fun getTab(file: FileObject, projectRoot: FileObject?, viewModel: MainViewModel): Tab {
        val ext = file.getExtension()
        val type = FileTypeManager.fromExtension(ext)

        if (registeredTabs.containsKey(ext)) {
            return registeredTabs[ext]!!.createTab(file, projectRoot, viewModel)
        }

        return when (type) {
            BuiltinFileType.IMAGE -> ImageTab(file)
            else -> viewModel.editorManager.createEditorTab(file, projectRoot)
        }
    }
}
