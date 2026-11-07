package com.goydevv.arseide.drawer

import com.goydevv.arseide.activities.main.fileTreeViewModel
import com.goydevv.arseide.file.FileObject
import com.goydevv.arseide.file.FileWrapper
import com.goydevv.arseide.file.child
import com.goydevv.arseide.resources.strings
import com.goydevv.arseide.utils.application
import com.goydevv.arseide.utils.readObject
import com.goydevv.arseide.utils.toast
import com.goydevv.arseide.utils.writeObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

object DrawerPersistence {
    private val saveMutex = Mutex()

    private const val DRAWER_TABS = "drawerTabs"
    private const val CURRENT_DRAWER_TAB = "currentDrawerTab"
    private const val EXPANDED_FILE_TREE_NODES = "expandedFileTree"

    suspend fun saveState(viewModel: DrawerViewModel) {
        saveMutex.withLock {
            val file = FileWrapper(application!!.filesDir.child(DRAWER_TABS))
            val serializableList = ArrayList(viewModel.drawerTabs)
            file.writeObject(serializableList)

            val currentTabFile = FileWrapper(application!!.filesDir.child(CURRENT_DRAWER_TAB))
            if (viewModel.currentDrawerTab != null) {
                currentTabFile.writeObject(viewModel.currentDrawerTab!!)
            } else {
                currentTabFile.delete()
            }

            val expandedNodeFile = FileWrapper(application!!.filesDir.child(EXPANDED_FILE_TREE_NODES))
            fileTreeViewModel.get()?.getExpandedNodes()?.let { expandedNodeFile.writeObject(it) }
        }
    }

    suspend fun restoreState(viewModel: DrawerViewModel) {
        saveMutex.withLock {
            runCatching {
                    val loadedTabs =
                        withContext(Dispatchers.IO) {
                            val file = FileWrapper(application!!.filesDir.child(DRAWER_TABS))

                            if (file.exists() && file.canRead()) {
                                file.readObject() as? ArrayList<DrawerTab> ?: emptyList()
                            } else {
                                emptyList()
                            }
                        }

                    // Update the existing state list on Main thread
                    withContext(Dispatchers.Main) { viewModel.forcePushDrawerTabs(loadedTabs) }

                    val currentTabFile = FileWrapper(application!!.filesDir.child(CURRENT_DRAWER_TAB))
                    if (currentTabFile.exists() && currentTabFile.canRead()) {
                        viewModel.selectDrawerTab(currentTabFile.readObject() as DrawerTab)
                    }

                    val expandedNodeFile = FileWrapper(application!!.filesDir.child(EXPANDED_FILE_TREE_NODES))
                    if (expandedNodeFile.exists() && expandedNodeFile.canRead()) {
                        fileTreeViewModel
                            .get()
                            ?.setExpandedNodes(expandedNodeFile.readObject() as Map<FileObject, Set<FileObject>>)
                    }
                }
                .onFailure {
                    it.printStackTrace()
                    toast(strings.project_restore_failed)
                }
        }
    }
}
