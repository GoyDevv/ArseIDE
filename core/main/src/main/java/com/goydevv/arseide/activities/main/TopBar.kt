package com.goydevv.arseide.activities.main

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
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
import com.goydevv.arseide.file.FileWrapper
import com.goydevv.arseide.filetree.FileTreeTab
import com.goydevv.arseide.projects.DetectedProjectType
import com.goydevv.arseide.projects.GradleConfig
import com.goydevv.arseide.resources.drawables
import com.goydevv.arseide.runner.ProjectRunner
import java.io.File
import kotlinx.coroutines.launch

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
    val projectRoot = (drawerViewModel.currentDrawerTab as? FileTreeTab)?.root
    val projectName = (drawerViewModel.currentDrawerTab as? FileTreeTab)?.getName()
    val isAndroidProject =
        projectRoot is FileWrapper &&
            ProjectRunner.detect(projectRoot.getAbsolutePath()) == DetectedProjectType.ANDROID
    val androidBuildType =
        if (isAndroidProject) GradleConfig.buildType(File((projectRoot as FileWrapper).getAbsolutePath()))
        else null

    AnimatedVisibility(visible = viewModel.showTopBar, enter = expandVertically(), exit = shrinkVertically()) {
        Column(modifier = Modifier.fillMaxWidth()) {
            TopAppBar(
                windowInsets = if (fullScreen) WindowInsets() else TopAppBarDefaults.windowInsets,
                colors =
                    TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer,
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
                            modifier = Modifier.padding(start = 6.dp).size(38.dp),
                        ) {
                            Icon(
                                painter = painterResource(drawables.folder_code),
                                contentDescription = "File Tree",
                                modifier = Modifier.size(19.dp),
                            )
                        }
                    }
                },
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            modifier =
                                Modifier.weight(1f, fill = false)
                                    .clip(RoundedCornerShape(14.dp))
                                    .clickable {
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
                                Text(
                                    text = projectName ?: "Arse IDE",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }

                        if (androidBuildType != null && activeWorkspaceTab != WorkspaceTab.BUILD) {
                            Surface(
                                shape = RoundedCornerShape(50),
                                color =
                                    if (androidBuildType == GradleConfig.BuildType.RELEASE) {
                                        MaterialTheme.colorScheme.tertiaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.secondaryContainer
                                    },
                                contentColor =
                                    if (androidBuildType == GradleConfig.BuildType.RELEASE) {
                                        MaterialTheme.colorScheme.onTertiaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.onSecondaryContainer
                                    },
                                modifier =
                                    Modifier.clip(RoundedCornerShape(50)).clickable {
                                        activeWorkspaceTab = WorkspaceTab.BUILD
                                    },
                            ) {
                                Text(
                                    text = androidBuildType.label,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                )
                            }
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
        }
    }
}
