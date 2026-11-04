package com.rk.activities.main

import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material3.Button
import androidx.compose.material3.DrawerState
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mohamedrejeb.compose.dnd.reorder.ReorderContainer
import com.mohamedrejeb.compose.dnd.reorder.ReorderState
import com.mohamedrejeb.compose.dnd.reorder.ReorderableItem
import com.mohamedrejeb.compose.dnd.reorder.rememberReorderState
import com.rk.DefaultScope
import com.rk.activities.settings.SettingsActivity
import com.rk.commands.CommandPalette
import com.rk.commands.CommandProvider
import com.rk.components.GlassBottomNavigationBar
import com.rk.components.GlassCard
import com.rk.components.GlassNavDestination
import com.rk.components.addDialog
import com.rk.components.backdropBlur
import com.rk.components.compose.utils.addIf
import com.rk.components.isPermanentDrawer
import com.rk.drawer.DrawerViewModel
import com.rk.drawer.showAddProjectSheetGlobal
import com.rk.drawer.showCreateProjectDialogGlobal
import com.rk.drawer.showProjectsBrowserGlobal
import com.rk.editor.preloadSelectionColor
import com.rk.file.FileWrapper
import com.rk.filetree.FileAction
import com.rk.filetree.FileActionContext
import com.rk.filetree.FileActionDialogs
import com.rk.filetree.FileActionProvider
import com.rk.filetree.FileIcon
import com.rk.filetree.FileTreeTab
import com.rk.filetree.FileTreeViewModel
import com.rk.filetree.MultiFileAction
import com.rk.filetree.MultiFileActionContext
import com.rk.icons.XedIcon
import com.rk.resources.drawables
import com.rk.resources.getString
import com.rk.resources.strings
import com.rk.runner.ProjectRunner
import com.rk.runner.RunOutputState
import com.rk.runner.RunOutputView
import com.rk.settings.Settings
import com.rk.tabs.base.Tab
import com.rk.tabs.editor.EditorTab
import com.rk.utils.dialogRes
import com.rk.utils.drawErrorUnderline
import com.rk.utils.getGitColor
import com.rk.utils.getUnderlineColor
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MainContent(
    innerPadding: PaddingValues,
    mainViewModel: MainViewModel,
    drawerViewModel: DrawerViewModel,
    fileTreeViewModel: FileTreeViewModel,
    drawerState: DrawerState,
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val mainActivity = context as? MainActivity

    preloadSelectionColor()

    FileActionDialogs(drawerViewModel, fileTreeViewModel, scope, context)

    // First-launch Auto Setup prompt
    var showAutoSetup by rememberSaveable {
        mutableStateOf(!Settings.auto_setup_completed && !Settings.auto_setup_prompt_dismissed)
    }
    if (showAutoSetup) {
        AutoSetupDialog(
            onLaunch = { showAutoSetup = false },
            onDismissForever = {
                Settings.auto_setup_prompt_dismissed = true
                showAutoSetup = false
            },
        )
    }

    if (com.rk.projects.DependencyManagerState.visible) {
        com.rk.projects.DependencyManagerState.projectRoot?.let { root ->
            com.rk.projects.DependenciesView(
                projectRoot = root,
                onDismiss = { com.rk.projects.DependencyManagerState.close() },
            )
        }
    }

    if (com.rk.projects.IdeConfigState.visible) {
        com.rk.projects.IdeConfigState.projectRoot?.let { root ->
            com.rk.projects.IdeConfigView(
                projectRoot = root,
                onDismiss = { com.rk.projects.IdeConfigState.close() },
            )
        }
    }

    if (mainViewModel.isDraggingPalette || mainViewModel.showCommandPalette) {
        val lastUsedCommand = CommandProvider.getForId(Settings.last_used_command)

        CommandPalette(
            progress = if (mainViewModel.showCommandPalette) 1f else mainViewModel.draggingPaletteProgress.value,
            commands = CommandProvider.commandList,
            lastUsedCommand = lastUsedCommand,
            initialChildCommands = mainViewModel.commandPaletteInitialChildCommands,
            initialPlaceholder = mainViewModel.commandPaletteInitialPlaceholder,
            onDismissRequest = { scope.launch { mainViewModel.closeCommandPalette() } },
        )
    }

    val imeVisible = WindowInsets.isImeVisible
    val shouldBlurWorkspace =
        RunOutputState.expanded ||
            mainViewModel.showCommandPalette ||
            mainViewModel.isDraggingPalette ||
            (drawerState.isOpen && !isPermanentDrawer)
    val blurRadius by
        animateDpAsState(
            targetValue = if (shouldBlurWorkspace) 14.dp else 0.dp,
            animationSpec = tween(durationMillis = 220),
            label = "workspaceBackdropBlur",
        )

    val currentProjectObj = (drawerViewModel.currentDrawerTab as? FileTreeTab)?.root
    val currentProjectPath = currentProjectObj?.getAbsolutePath()
    val gitChangesCount =
        currentProjectPath?.let { gitViewModel.get()?.changes?.get(it)?.size } ?: 0
    val gitTab = drawerViewModel.serviceTabs.firstOrNull()
    val isGitSupported = gitTab?.isSupported() == true

    Box(Modifier.fillMaxSize().padding(innerPadding)) {
        Column(
            modifier =
                Modifier.fillMaxSize()
                    .padding(bottom = if (imeVisible) 0.dp else 72.dp)
                    .backdropBlur(blurRadius)
        ) {
            val visibleTabs = visibleTabsFor(mainViewModel, drawerViewModel)

            LaunchedEffect(Settings.show_all_files, currentProjectPath, visibleTabs.size) {
                val cur = mainViewModel.currentTab
                if (cur != null && visibleTabs.isNotEmpty() && cur !in visibleTabs) {
                    val idx = mainViewModel.tabs.indexOf(visibleTabs.first())
                    if (idx >= 0) mainViewModel.tabManager.setCurrentTab(idx)
                }
            }

            if (visibleTabs.isEmpty()) {
                EmptyEditorState(
                    projectPath = currentProjectPath,
                    onOpenTree = {
                        drawerViewModel.unselectServiceTab()
                        scope.launch { drawerState.open() }
                    },
                    onCreateProject = {
                        showCreateProjectDialogGlobal = true
                        scope.launch { drawerState.open() }
                    },
                    onBrowseProjects = {
                        showProjectsBrowserGlobal = true
                        scope.launch { drawerState.open() }
                    },
                    onNewFile = { addDialog = true },
                    onRunProject = {
                        if (currentProjectObj is FileWrapper && mainActivity != null) {
                            DefaultScope.launch {
                                ProjectRunner.run(mainActivity, currentProjectObj, currentProjectObj)
                            }
                        }
                    },
                )
            } else {
                val pagerState =
                    rememberPagerState(pageCount = { visibleTabsFor(mainViewModel, drawerViewModel).size })

                val selectedVisibleIndex =
                    visibleTabs.indexOf(mainViewModel.currentTab).let { if (it < 0) 0 else it }

                LaunchedEffect(selectedVisibleIndex, visibleTabs.size) {
                    if (
                        visibleTabs.isNotEmpty() &&
                            selectedVisibleIndex < visibleTabs.size &&
                            pagerState.currentPage != selectedVisibleIndex
                    ) {
                        if (Settings.smooth_tabs) {
                            pagerState.animateScrollToPage(selectedVisibleIndex)
                        } else {
                            pagerState.scrollToPage(selectedVisibleIndex)
                        }
                    }
                }

                val reorderState = rememberReorderState<Tab>(dragAfterLongPress = true)

                ReorderContainer(state = reorderState) {
                    PrimaryScrollableTabRow(
                        selectedTabIndex = selectedVisibleIndex,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                        edgePadding = 6.dp,
                        containerColor = MaterialTheme.colorScheme.surface,
                        indicator = {},
                        divider = {},
                    ) {
                        visibleTabs.forEach { tabState ->
                            val index = mainViewModel.tabs.indexOf(tabState)
                            key(tabState) {
                                TabItem(
                                    mainViewModel = mainViewModel,
                                    fileTreeViewModel = fileTreeViewModel,
                                    reorderState = reorderState,
                                    tabState = tabState,
                                    index = index,
                                    showIcon = Settings.show_tab_icons,
                                    onCloseThis = {
                                        val tabIndex = mainViewModel.tabs.indexOf(tabState)
                                        if (tabIndex == -1) return@TabItem

                                        if (tabState is EditorTab && tabState.editorState.isDirty) {
                                            dialogRes(
                                                title = strings.file_unsaved.getString(),
                                                msg = strings.ask_unsaved.getString(),
                                                onOk = { mainViewModel.tabManager.removeTab(tabIndex) },
                                                onCancel = {},
                                                okRes = strings.discard,
                                            )
                                        } else {
                                            mainViewModel.tabManager.removeTab(tabIndex)
                                        }
                                    },
                                    onCloseOthers = { idx ->
                                        mainViewModel.tabManager.setCurrentTab(idx)

                                        val unsavedOtherTabs =
                                            mainViewModel.tabs.filterIndexed { tabIndex, tab ->
                                                tabIndex != idx && (tab as? EditorTab)?.editorState?.isDirty == true
                                            }
                                        if (unsavedOtherTabs.isNotEmpty()) {
                                            dialogRes(
                                                title = strings.files_unsaved.getString(),
                                                msg = strings.ask_multiple_unsaved.getString(),
                                                onOk = { mainViewModel.tabManager.removeOtherTabs() },
                                                onCancel = {},
                                                okRes = strings.discard,
                                            )
                                        } else {
                                            mainViewModel.tabManager.removeOtherTabs()
                                        }
                                    },
                                    onCloseAll = {
                                        val unsavedTabs =
                                            mainViewModel.tabs.filter { tab ->
                                                (tab as? EditorTab)?.editorState?.isDirty == true
                                            }
                                        if (unsavedTabs.isNotEmpty()) {
                                            dialogRes(
                                                title = strings.files_unsaved.getString(),
                                                msg = strings.ask_multiple_unsaved.getString(),
                                                onOk = { mainViewModel.tabManager.removeAllTabs() },
                                                onCancel = {},
                                                okRes = strings.discard,
                                            )
                                        } else {
                                            mainViewModel.tabManager.removeAllTabs()
                                        }
                                    },
                                )
                            }
                        }
                    }
                }

                HorizontalDivider(
                    thickness = 0.5.dp,
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                )

                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize().clipToBounds(),
                    beyondViewportPageCount = visibleTabs.size,
                    userScrollEnabled = false,
                    key = { visibleTabs.getOrNull(it).hashCode() },
                ) { page ->
                    visibleTabs.getOrNull(page)?.Content()
                }
            }
        }

        // Always-visible floating build/run output — sits cleanly above the Glass Bottom Navigation Bar.
        RunOutputView(
            modifier =
                Modifier.align(Alignment.BottomCenter)
                    .padding(bottom = if (imeVisible) 0.dp else 72.dp)
        )

        // --- Main Workspace Glassmorphic Bottom Navigation Bar with Blur ---
        AnimatedVisibility(
            visible = !imeVisible,
            enter = slideInVertically { it } + fadeIn(),
            exit = slideOutVertically { it } + fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            val workspaceNavItems =
                buildList {
                    add(
                        GlassNavDestination(
                            id = "editor",
                            label = stringResource(strings.editor),
                            iconRes = drawables.edit_note,
                            selected = drawerState.isClosed && !mainViewModel.showCommandPalette,
                            onClick = {
                                scope.launch {
                                    if (drawerState.isOpen) drawerState.close()
                                    if (mainViewModel.showCommandPalette) mainViewModel.closeCommandPalette()
                                }
                            },
                        )
                    )
                    add(
                        GlassNavDestination(
                            id = "files",
                            label = stringResource(strings.files),
                            iconRes = drawables.outline_folder,
                            selected = drawerState.isOpen && drawerViewModel.currentServiceTab == null,
                            onClick = {
                                drawerViewModel.unselectServiceTab()
                                scope.launch {
                                    if (drawerState.isOpen && drawerViewModel.currentServiceTab == null) {
                                        drawerState.close()
                                    } else {
                                        drawerState.open()
                                    }
                                }
                            },
                        )
                    )
                    if (gitTab != null && isGitSupported) {
                        add(
                            GlassNavDestination(
                                id = "git",
                                label = stringResource(strings.git),
                                iconRes = drawables.git,
                                selected = drawerState.isOpen && drawerViewModel.currentServiceTab == gitTab,
                                badgeCount = gitChangesCount,
                                onClick = {
                                    drawerViewModel.selectServiceTab(gitTab)
                                    scope.launch { drawerState.open() }
                                },
                            )
                        )
                    }
                    add(
                        GlassNavDestination(
                            id = "projects",
                            label = stringResource(strings.add),
                            iconVector = Icons.Outlined.Add,
                            selected = false,
                            isAccentAction = drawerViewModel.drawerTabs.isEmpty(),
                            onClick = {
                                showAddProjectSheetGlobal = true
                                scope.launch { drawerState.open() }
                            },
                        )
                    )
                    add(
                        GlassNavDestination(
                            id = "palette",
                            label = "Palette",
                            iconRes = drawables.command_palette,
                            selected = mainViewModel.showCommandPalette,
                            onClick = {
                                if (mainViewModel.showCommandPalette) {
                                    scope.launch { mainViewModel.closeCommandPalette() }
                                } else {
                                    mainViewModel.openCommandPalette()
                                }
                            },
                        )
                    )
                    add(
                        GlassNavDestination(
                            id = "settings",
                            label = stringResource(strings.settings),
                            iconRes = drawables.settings,
                            selected = false,
                            onClick = {
                                context.startActivity(Intent(context, SettingsActivity::class.java))
                            },
                        )
                    )
                }

            GlassBottomNavigationBar(
                items = workspaceNavItems,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            )
        }
    }
}

private fun visibleTabsFor(mainViewModel: MainViewModel, drawerViewModel: DrawerViewModel): List<Tab> {
    val all = mainViewModel.tabs
    if (Settings.show_all_files) return all
    val projectPath = (drawerViewModel.currentDrawerTab as? FileTreeTab)?.root?.getAbsolutePath() ?: return all
    return all.filter { tab -> tab !is EditorTab || tab.projectRoot?.getAbsolutePath() == projectPath }
}

@Composable
private fun TabItem(
    mainViewModel: MainViewModel,
    fileTreeViewModel: FileTreeViewModel,
    reorderState: ReorderState<Tab>,
    tabState: Tab,
    index: Int,
    showIcon: Boolean,
    onCloseThis: (Int) -> Unit,
    onCloseOthers: (Int) -> Unit,
    onCloseAll: (Int) -> Unit,
) {
    var calculatedTabWidth by
        remember(
            tabState,
            tabState.tabTitle.value,
            tabState is EditorTab && tabState.editorState.isDirty,
            Settings.show_tab_icons,
        ) {
            mutableStateOf<Int?>(null)
        }

    ReorderableItem(
        state = reorderState,
        key = tabState,
        data = tabState,
        onDragEnter = { state ->
            val targetIdx = mainViewModel.tabs.indexOf(tabState)
            val oldIndex = mainViewModel.tabs.indexOf(state.data)

            mainViewModel.tabManager.moveTab(oldIndex, targetIdx)
        },
        draggableContent = {
            TabItemContent(
                mainViewModel = mainViewModel,
                fileTreeViewModel = fileTreeViewModel,
                index = index,
                calculatedTabWidth = calculatedTabWidth,
                tabState = tabState,
                onCloseThis = onCloseThis,
                onCloseOthers = onCloseOthers,
                onCloseAll = onCloseAll,
                showIcon = showIcon,
                isDraggableContent = true,
            )
        },
        modifier = Modifier.fillMaxWidth().onSizeChanged { size -> calculatedTabWidth = size.width },
    ) {
        TabItemContent(
            mainViewModel = mainViewModel,
            fileTreeViewModel = fileTreeViewModel,
            index = index,
            calculatedTabWidth = calculatedTabWidth,
            tabState = tabState,
            onCloseThis = onCloseThis,
            onCloseOthers = onCloseOthers,
            onCloseAll = onCloseAll,
            showIcon = showIcon,
        )
    }
}

@Composable
private fun TabItemContent(
    mainViewModel: MainViewModel,
    fileTreeViewModel: FileTreeViewModel,
    index: Int,
    calculatedTabWidth: Int?,
    tabState: Tab,
    onCloseThis: (Int) -> Unit,
    onCloseOthers: (Int) -> Unit,
    onCloseAll: (Int) -> Unit,
    showIcon: Boolean,
    isDraggableContent: Boolean = false,
) {
    var showTabMenu by remember { mutableStateOf(false) }
    var showFileActionMenu by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val density = LocalDensity.current

    val drawerViewModel = (context as MainActivity).drawerViewModel

    val isSelected = mainViewModel.currentTabIndex == index
    val isDirty = tabState is EditorTab && tabState.editorState.isDirty

    val tabModifier =
        Modifier.let { modifier ->
                calculatedTabWidth?.let { width -> modifier.width(with(density) { width.toDp() }) } ?: modifier
            }
            .padding(horizontal = 3.dp, vertical = 4.dp)

    val onClick: () -> Unit = {
        if (isSelected) {
            showTabMenu = true
        } else {
            mainViewModel.tabManager.setCurrentTab(index)
        }
    }

    val underlineColor = getUnderlineColor(context, fileTreeViewModel, tabState.file)
    val gitColor = getGitColor(tabState.file)
    val activeColor = gitColor ?: MaterialTheme.colorScheme.primary
    val inactiveColor = gitColor ?: MaterialTheme.colorScheme.onSurfaceVariant

    val pillContainerColor =
        when {
            isDraggableContent -> MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.65f)
            isSelected -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
            else -> MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.55f)
        }

    val pillBorder =
        if (isSelected) {
            BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.38f))
        } else {
            BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))
        }

    Surface(
        modifier = tabModifier.clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        color = pillContainerColor,
        border = pillBorder,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            CompositionLocalProvider(
                LocalContentColor provides if (isSelected) activeColor else inactiveColor
            ) {
                if (showIcon && tabState.file != null) {
                    FileIcon(file = tabState.file!!, iconTint = LocalContentColor.current)
                }

                if (isDirty) {
                    Box(
                        modifier =
                            Modifier.size(7.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary)
                    )
                }

                Text(
                    text = tabState.tabTitle.value,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                    color = if (isSelected) activeColor else inactiveColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.addIf(underlineColor != null) { drawErrorUnderline(underlineColor!!) },
                )

                if (isSelected) {
                    Box(
                        modifier =
                            Modifier.size(18.dp)
                                .clip(CircleShape)
                                .clickable { onCloseThis(index) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            painter = painterResource(drawables.close),
                            contentDescription = stringResource(strings.close),
                            tint = activeColor.copy(alpha = 0.78f),
                            modifier = Modifier.size(13.dp),
                        )
                    }
                }
            }

            DropdownMenu(expanded = showTabMenu, onDismissRequest = { showTabMenu = false }) {
                DropdownMenuItem(
                    text = { Text(stringResource(strings.close_this)) },
                    onClick = {
                        showTabMenu = false
                        onCloseThis(index)
                    },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(strings.close_others)) },
                    onClick = {
                        showTabMenu = false
                        onCloseOthers(index)
                    },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(strings.close_all)) },
                    onClick = {
                        showTabMenu = false
                        onCloseAll(index)
                    },
                )
                tabState.file?.let {
                    val fileExists by produceState(false) { value = it.exists() }
                    DropdownMenuItem(
                        text = { Text(stringResource(strings.file_actions)) },
                        enabled = fileExists,
                        trailingIcon = {
                            Icon(
                                painter = painterResource(drawables.chevron_right),
                                contentDescription = stringResource(strings.open),
                            )
                        },
                        onClick = {
                            showTabMenu = false
                            showFileActionMenu = true
                        },
                    )
                }
            }

            tabState.file?.let {
                DropdownMenu(expanded = showFileActionMenu, onDismissRequest = { showFileActionMenu = false }) {
                    val root = (tabState as? EditorTab)?.projectRoot
                    val actions = remember(it) { FileActionProvider.getActions(it, root) }

                    actions.forEach { action ->
                        when (action) {
                            is FileAction -> {
                                DropdownMenuItem(
                                    text = { Text(action.title) },
                                    leadingIcon = { XedIcon(action.icon, contentDescription = action.title) },
                                    enabled = action.isEnabled(it),
                                    onClick = {
                                        val actionCtx =
                                            FileActionContext(it, root, fileTreeViewModel, drawerViewModel, context)
                                        action.action(actionCtx)
                                        showFileActionMenu = false
                                    },
                                )
                            }
                            is MultiFileAction -> {
                                val files = listOf(it)
                                DropdownMenuItem(
                                    text = { Text(action.title) },
                                    leadingIcon = { XedIcon(action.icon, contentDescription = action.title) },
                                    enabled = action.isEnabled(files),
                                    onClick = {
                                        val actionCtx =
                                            MultiFileActionContext(
                                                files,
                                                root,
                                                fileTreeViewModel,
                                                drawerViewModel,
                                                context,
                                            )
                                        action.action(actionCtx)
                                        showFileActionMenu = false
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Glassmorphic Project Overview & Quick Launch Dashboard shown when no file is open.
 */
@Composable
private fun EmptyEditorState(
    projectPath: String?,
    onOpenTree: () -> Unit,
    onCreateProject: () -> Unit,
    onBrowseProjects: () -> Unit,
    onNewFile: () -> Unit,
    onRunProject: () -> Unit,
) {
    Column(
        modifier =
            Modifier.fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        GlassCard(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(26.dp),
            contentPadding = PaddingValues(24.dp),
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.85f),
                    modifier = Modifier.size(64.dp),
                ) {
                    Icon(
                        painter =
                            painterResource(
                                if (projectPath != null) drawables.folder_code else drawables.outline_folder
                            ),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(16.dp),
                    )
                }

                Spacer(Modifier.height(16.dp))

                if (projectPath != null) {
                    val root = remember(projectPath) { File(projectPath) }
                    val canRun = remember(projectPath) { ProjectRunner.canRun(projectPath) }

                    Text(
                        text = root.name,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = projectPath,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                    )

                    val language by
                        produceState<String?>(initialValue = null, projectPath) {
                            value = withContext(Dispatchers.IO) { detectMajorityLanguage(root) }
                        }
                    language?.let {
                        Spacer(Modifier.height(12.dp))
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
                            shape = RoundedCornerShape(50),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)),
                        ) {
                            Text(
                                text = "●  $it",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                            )
                        }
                    }

                    Spacer(Modifier.height(22.dp))

                    Button(
                        onClick = onOpenTree,
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                    ) {
                        Icon(
                            painterResource(drawables.outline_folder),
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("Open File Explorer")
                    }

                    Spacer(Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        OutlinedButton(
                            onClick = onNewFile,
                            modifier = Modifier.weight(1f).height(44.dp),
                        ) {
                            Icon(
                                painterResource(drawables.add),
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(strings.new_file), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        if (canRun) {
                            OutlinedButton(
                                onClick = onRunProject,
                                modifier = Modifier.weight(1f).height(44.dp),
                            ) {
                                Icon(
                                    painterResource(drawables.run),
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                )
                                Spacer(Modifier.width(6.dp))
                                Text("Run Project", maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        } else {
                            OutlinedButton(
                                onClick = onBrowseProjects,
                                modifier = Modifier.weight(1f).height(44.dp),
                            ) {
                                Icon(
                                    painterResource(drawables.folder),
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                )
                                Spacer(Modifier.width(6.dp))
                                Text("Switch Project", maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                } else {
                    Text(
                        text = "XED-PRO Workspace",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "Create a build-ready project, open an existing repository, or jump right into coding.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(22.dp))

                    Button(
                        onClick = onCreateProject,
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                    ) {
                        Icon(
                            painterResource(drawables.add),
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(strings.create_project))
                    }

                    Spacer(Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        OutlinedButton(
                            onClick = onBrowseProjects,
                            modifier = Modifier.weight(1f).height(44.dp),
                        ) {
                            Icon(
                                painterResource(drawables.folder_code),
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                            )
                            Spacer(Modifier.width(6.dp))
                            Text("Projects", maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        OutlinedButton(
                            onClick = onOpenTree,
                            modifier = Modifier.weight(1f).height(44.dp),
                        ) {
                            Icon(
                                painterResource(drawables.outline_folder),
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                            )
                            Spacer(Modifier.width(6.dp))
                            Text("Explorer", maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
    }
}

private fun detectMajorityLanguage(root: File): String? {
    val skip =
        setOf(
            ".git",
            "node_modules",
            "build",
            ".gradle",
            ".idea",
            ".cxx",
            "out",
            "dist",
            ".dart_tool",
            "vendor",
        )
    val counts = HashMap<String, Int>()
    runCatching {
        root
            .walkTopDown()
            .onEnter { it.name !in skip }
            .filter { it.isFile }
            .take(6000)
            .forEach { f ->
                val lang = languageForExtension(f.name.substringAfterLast('.', "").lowercase()) ?: return@forEach
                counts[lang] = (counts[lang] ?: 0) + 1
            }
    }
    val top = counts.maxByOrNull { it.value } ?: return null
    val total = counts.values.sum().coerceAtLeast(1)
    return "${top.key} · ${top.value * 100 / total}%"
}

private fun languageForExtension(ext: String): String? =
    when (ext) {
        "kt", "kts" -> "Kotlin"
        "java" -> "Java"
        "py" -> "Python"
        "js", "mjs", "cjs" -> "JavaScript"
        "ts" -> "TypeScript"
        "tsx", "jsx" -> "React"
        "c" -> "C"
        "cpp", "cc", "cxx", "hpp", "hh" -> "C++"
        "h" -> "C/C++"
        "rs" -> "Rust"
        "go" -> "Go"
        "rb" -> "Ruby"
        "php" -> "PHP"
        "swift" -> "Swift"
        "dart" -> "Dart"
        "html", "htm" -> "HTML"
        "css", "scss", "sass" -> "CSS"
        "json" -> "JSON"
        "xml" -> "XML"
        "md", "markdown" -> "Markdown"
        "sh", "bash" -> "Shell"
        "gradle" -> "Gradle"
        "yml", "yaml" -> "YAML"
        "sql" -> "SQL"
        else -> null
    }
