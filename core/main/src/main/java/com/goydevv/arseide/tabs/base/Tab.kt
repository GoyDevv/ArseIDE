package com.goydevv.arseide.tabs.base

import androidx.compose.foundation.layout.RowScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.ui.graphics.vector.ImageVector
import com.goydevv.arseide.activities.main.TabState
import com.goydevv.arseide.file.FileObject

abstract class Tab {
    var refreshKey: Int = 0
    abstract val name: String
    abstract val icon: ImageVector

    /** Can be null if tab is not file-related. */
    open val file: FileObject? = null

    /** Can be null if tab should not be restored. */
    open fun getState(): TabState? = null

    abstract val tabTitle: MutableState<String>

    open fun onTabRemoved() {}

    open fun onTabAdded() {}

    open fun onTabSelected() {}

    open fun onTabUnselected() {}

    @Composable abstract fun Content()

    @Composable open fun RowScope.Actions() {}

    open val showGlobalActions: Boolean = true
}
