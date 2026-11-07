package com.goydevv.arseide.activities.main

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DrawerState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.goydevv.arseide.components.GlobalToolbarActions
import com.goydevv.arseide.components.isPermanentDrawer
import com.goydevv.arseide.drawer.DrawerViewModel
import com.goydevv.arseide.filetree.FileTreeTab
import com.goydevv.arseide.resources.drawables
import kotlinx.coroutines.launch

/**
 * Clean Material You TopAppBar for Arse IDE:
 * - Top-left navigation icon is a dedicated **File Tree** button with a file/folder icon (`folder_code`)
 *   that opens/closes the File Tree drawer exclusively.
 * - Title shows the active project or "Arse IDE" without crowding or clipping the action icons.
 * - Actions row is cleanly separated between global Run/Sync actions and bounded Editor actions so
 *   icons never glitch or overlap.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun XedTopBar(
    drawerState: DrawerState,
    viewModel: MainViewModel,
    drawerViewModel: DrawerViewModel,
    fullScreen: Boolean,
    onDrag: (Float) -> Unit = {},
    onDragEnd: () -> Unit = {},
) {
    val scope = rememberCoroutineScope()
    val projectName = (drawerViewModel.currentDrawerTab as? FileTreeTab)?.getName()

    AnimatedVisibility(visible = viewModel.showTopBar, enter = expandVertically(), exit = shrinkVertically()) {
        Column(modifier = Modifier.fillMaxWidth()) {
            TopAppBar(
                windowInsets = if (fullScreen) WindowInsets() else TopAppBarDefaults.windowInsets,
                colors =
                    TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                        titleContentColor = MaterialTheme.colorScheme.onSurface,
                        actionIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    ),
                modifier =
                    Modifier.pointerInput(Unit) {
                        detectVerticalDragGestures(
                            onVerticalDrag = { _, dragAmount -> onDrag(dragAmount) },
                            onDragEnd = { onDragEnd() },
                            onDragCancel = { onDragEnd() },
                        )
                    },
                navigationIcon = {
                    if (!isPermanentDrawer) {
                        FilledTonalIconButton(
                            onClick = {
                                drawerViewModel.unselectServiceTab()
                                scope.launch {
                                    if (drawerState.isClosed) drawerState.open() else drawerState.close()
                                }
                            },
                            colors =
                                IconButtonDefaults.filledTonalIconButtonColors(
                                    containerColor =
                                        if (drawerState.isOpen) MaterialTheme.colorScheme.primaryContainer
                                        else MaterialTheme.colorScheme.secondaryContainer,
                                    contentColor =
                                        if (drawerState.isOpen) MaterialTheme.colorScheme.onPrimaryContainer
                                        else MaterialTheme.colorScheme.onSecondaryContainer,
                                ),
                            modifier = Modifier.padding(start = 6.dp),
                        ) {
                            Icon(
                                painter = painterResource(drawables.folder_code),
                                contentDescription = "File Tree",
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                },
                title = {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        modifier =
                            Modifier.clip(RoundedCornerShape(12.dp)).clickable {
                                drawerViewModel.unselectServiceTab()
                                scope.launch {
                                    if (drawerState.isClosed) drawerState.open() else drawerState.close()
                                }
                            },
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                painter = painterResource(drawables.outline_folder),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp),
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = projectName ?: "Arse IDE",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                },
                actions = {
                    GlobalToolbarActions(viewModel, drawerViewModel)

                    if (viewModel.tabs.isNotEmpty() && activeWorkspaceTab == WorkspaceTab.EDITOR) {
                        val tab = viewModel.currentTab
                        if (tab != null) {
                            tab.apply { Actions() }
                        }
                    }
                },
            )
            HorizontalDivider(
                thickness = 0.5.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
            )
        }
    }
}
