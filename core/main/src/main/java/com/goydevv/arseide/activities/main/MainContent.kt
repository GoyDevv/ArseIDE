package com.goydevv.arseide.activities.main

import android.content.Intent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.material3.Button
import androidx.compose.material3.DrawerState
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.goydevv.arseide.DefaultScope
import androidx.navigation.compose.rememberNavController
import com.goydevv.arseide.activities.settings.SettingsActivity
import com.goydevv.arseide.activities.settings.SettingsNavHost
import com.goydevv.arseide.projects.AndroidBuildTab
import com.goydevv.arseide.projects.DetectedProjectType
import com.goydevv.arseide.commands.CommandPalette
import com.goydevv.arseide.commands.CommandProvider
import com.goydevv.arseide.components.ExpressiveBottomNavigationBar
import com.goydevv.arseide.components.ExpressiveNavDestination
import com.goydevv.arseide.components.addDialog
import com.goydevv.arseide.components.compose.utils.addIf
import com.goydevv.arseide.components.isPermanentDrawer
import com.goydevv.arseide.drawer.DrawerViewModel
import com.goydevv.arseide.drawer.createProjectInitialTemplateGlobal
import com.goydevv.arseide.drawer.showAddProjectSheetGlobal
import com.goydevv.arseide.drawer.showCreateProjectDialogGlobal
import com.goydevv.arseide.drawer.showGitCloneDialogGlobal
import com.goydevv.arseide.editor.preloadSelectionColor
import com.goydevv.arseide.exec.PhantomKillerBypass
import com.goydevv.arseide.exec.TerminalCommand
import com.goydevv.arseide.exec.launchTerminal
import com.goydevv.arseide.file.FileWrapper
import com.goydevv.arseide.file.sandboxHomeDir
import com.goydevv.arseide.filetree.FileAction
import com.goydevv.arseide.filetree.FileActionContext
import com.goydevv.arseide.filetree.FileActionDialogs
import com.goydevv.arseide.filetree.FileActionProvider
import com.goydevv.arseide.filetree.FileIcon
import com.goydevv.arseide.filetree.FileTreeTab
import com.goydevv.arseide.filetree.FileTreeViewModel
import com.goydevv.arseide.filetree.MultiFileAction
import com.goydevv.arseide.filetree.MultiFileActionContext
import com.goydevv.arseide.icons.XedIcon
import com.goydevv.arseide.projects.ProjectTemplate
import com.goydevv.arseide.projects.ProjectsFragment
import com.goydevv.arseide.resources.drawables
import com.goydevv.arseide.resources.getString
import com.goydevv.arseide.resources.strings
import com.goydevv.arseide.runner.ProjectRunner
import com.goydevv.arseide.runner.RunOutputState
import com.goydevv.arseide.runner.RunOutputView
import com.goydevv.arseide.settings.Settings
import com.goydevv.arseide.tabs.base.Tab
import com.goydevv.arseide.tabs.editor.EditorTab
import com.goydevv.arseide.editor.ThemeManager
import com.goydevv.arseide.settings.editor.refreshEditors
import com.goydevv.arseide.theme.amoled
import com.goydevv.arseide.theme.builtInThemes
import com.goydevv.arseide.theme.currentTheme
import com.goydevv.arseide.theme.dynamicTheme
import com.goydevv.arseide.theme.updateThemes
import com.goydevv.arseide.utils.dialogRes
import com.goydevv.arseide.utils.drawErrorUnderline
import com.goydevv.arseide.utils.findGitRoot
import com.goydevv.arseide.utils.getGitColor
import com.goydevv.arseide.utils.getUnderlineColor
import com.goydevv.arseide.utils.toast
import com.mohamedrejeb.compose.dnd.reorder.ReorderContainer
import com.mohamedrejeb.compose.dnd.reorder.ReorderState
import com.mohamedrejeb.compose.dnd.reorder.ReorderableItem
import com.mohamedrejeb.compose.dnd.reorder.rememberReorderState
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.eclipse.jgit.api.Git

enum class WorkspaceTab(val index: Int, val label: String, val iconRes: Int) {
    EDITOR(0, "Editor", drawables.edit_note),
    BUILD(1, "Build", drawables.apk_document),
    PROJECTS(2, "Projects", drawables.folder_code),
    GIT(3, "Git", drawables.git),
    SETTINGS(4, "Settings", drawables.settings),
}

var activeWorkspaceTab by mutableStateOf(WorkspaceTab.EDITOR)

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

    var showAutoSetup by rememberSaveable {
        mutableStateOf(!Settings.auto_setup_completed && !Settings.auto_setup_prompt_dismissed)
    }
    if (showAutoSetup || showAutoSetupDialogGlobal) {
        AutoSetupDialog(
            onLaunch = {
                showAutoSetup = false
                showAutoSetupDialogGlobal = false
            },
            onDismissForever = {
                Settings.auto_setup_prompt_dismissed = true
                showAutoSetup = false
                showAutoSetupDialogGlobal = false
            },
        )
    }

    if (com.goydevv.arseide.projects.DependencyManagerState.visible) {
        com.goydevv.arseide.projects.DependencyManagerState.projectRoot?.let { root ->
            com.goydevv.arseide.projects.DependenciesView(
                projectRoot = root,
                onDismiss = { com.goydevv.arseide.projects.DependencyManagerState.close() },
            )
        }
    }

    if (com.goydevv.arseide.projects.IdeConfigState.visible) {
        com.goydevv.arseide.projects.IdeConfigState.projectRoot?.let { root ->
            com.goydevv.arseide.projects.IdeConfigView(
                projectRoot = root,
                onDismiss = { com.goydevv.arseide.projects.IdeConfigState.close() },
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
    val settingsNavController = rememberNavController()

    val currentProjectObj = (drawerViewModel.currentDrawerTab as? FileTreeTab)?.root
    val currentProjectPath = currentProjectObj?.getAbsolutePath()
    val isAndroidProject =
        currentProjectObj is FileWrapper &&
            ProjectRunner.detect(currentProjectObj.getAbsolutePath()) == DetectedProjectType.ANDROID
    LaunchedEffect(isAndroidProject) {
        if (!isAndroidProject && activeWorkspaceTab == WorkspaceTab.BUILD) {
            activeWorkspaceTab = WorkspaceTab.EDITOR
        }
    }
    val gitChangesCount =
        currentProjectPath?.let { gitViewModel.get()?.changes?.get(it)?.size } ?: 0
    val gitTab = drawerViewModel.serviceTabs.firstOrNull()

    LaunchedEffect(mainViewModel.tabs.size, mainViewModel.currentTabIndex) {
        if (mainViewModel.tabs.isNotEmpty() && activeWorkspaceTab != WorkspaceTab.EDITOR) {
            activeWorkspaceTab = WorkspaceTab.EDITOR
        }
    }

    Box(Modifier.fillMaxSize().padding(innerPadding)) {
        Box(
            modifier =
                Modifier.fillMaxSize()
                    .padding(bottom = if (imeVisible) 0.dp else 74.dp)
        ) {
            AnimatedContent(
                targetState = activeWorkspaceTab,
                transitionSpec = {
                    val direction = if (targetState.index >= initialState.index) 1 else -1
                    (slideInHorizontally(
                        animationSpec = tween(durationMillis = 250, easing = FastOutSlowInEasing),
                        initialOffsetX = { fullWidth -> direction * (fullWidth / 4) },
                    ) + fadeIn(animationSpec = tween(200))) togetherWith
                        (slideOutHorizontally(
                            animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing),
                            targetOffsetX = { fullWidth -> -direction * (fullWidth / 4) },
                        ) + fadeOut(animationSpec = tween(160)))
                },
                label = "workspaceFragmentSlide",
            ) { currentTabFragment ->
                when (currentTabFragment) {
                    WorkspaceTab.EDITOR -> {
                        EditorFragmentContent(
                            mainViewModel = mainViewModel,
                            drawerViewModel = drawerViewModel,
                            fileTreeViewModel = fileTreeViewModel,
                            drawerState = drawerState,
                            currentProjectPath = currentProjectPath,
                            onOpenProjectsTab = { activeWorkspaceTab = WorkspaceTab.PROJECTS },
                            onCreateProject = { tmpl ->
                                createProjectInitialTemplateGlobal = tmpl
                                showCreateProjectDialogGlobal = true
                            },
                            onRunProject = {
                                if (currentProjectObj is FileWrapper && mainActivity != null) {
                                    DefaultScope.launch {
                                        ProjectRunner.run(mainActivity, currentProjectObj, currentProjectObj)
                                    }
                                }
                            },
                        )
                    }

                    WorkspaceTab.PROJECTS -> {
                        ProjectsFragment(
                            activeProjectPath = currentProjectPath,
                            onOpenProject = { dir ->
                                scope.launch {
                                    drawerViewModel.addFileTreeTab(FileWrapper(dir), save = true)
                                    activeWorkspaceTab = WorkspaceTab.EDITOR
                                    drawerState.open()
                                }
                            },
                            onRunBuildProject = { dir ->
                                scope.launch {
                                    val fw = FileWrapper(dir)
                                    drawerViewModel.addFileTreeTab(fw, save = true)
                                    if (mainActivity != null) {
                                        ProjectRunner.run(mainActivity, fw, fw)
                                    }
                                }
                            },
                            onCreateProject = { tmpl ->
                                createProjectInitialTemplateGlobal = tmpl
                                showCreateProjectDialogGlobal = true
                            },
                            onCloneGitRepo = { showGitCloneDialogGlobal = true },
                            onOpenFolderSheet = { showAddProjectSheetGlobal = true },
                        )
                    }

                    WorkspaceTab.GIT -> {
                        GitFragmentView(
                            currentProjectPath = currentProjectPath,
                            gitTab = gitTab,
                            onCloneRepo = { showGitCloneDialogGlobal = true },
                            onOpenProjects = { activeWorkspaceTab = WorkspaceTab.PROJECTS },
                        )
                    }

                    WorkspaceTab.BUILD -> {
                        if (currentProjectObj is FileWrapper) {
                            val rootFile = File(currentProjectObj.getAbsolutePath())
                            AndroidBuildTab(
                                projectRoot = rootFile,
                                onBuildApk = {
                                    if (mainActivity != null) {
                                        DefaultScope.launch {
                                            ProjectRunner.run(mainActivity, currentProjectObj, currentProjectObj)
                                        }
                                    }
                                },
                                onSyncGradle = {
                                    if (mainActivity != null) {
                                        DefaultScope.launch {
                                            ProjectRunner.sync(mainActivity, currentProjectObj, currentProjectObj)
                                        }
                                    }
                                },
                                onOpenIdeConfig = {
                                    com.goydevv.arseide.projects.IdeConfigState.open(rootFile)
                                },
                            )
                        }
                    }

                    WorkspaceTab.SETTINGS -> {
                        if (mainActivity != null) {
                            SettingsNavHost(
                                navController = settingsNavController,
                                activity = mainActivity,
                                fileManager = mainActivity.fileManager,
                                showRootBackArrow = false,
                            )
                        }
                    }
                }
            }
        }

        RunOutputView(
            modifier =
                Modifier.align(Alignment.BottomCenter)
                    .padding(bottom = if (imeVisible) 0.dp else 74.dp)
        )

        AnimatedVisibility(
            visible = !imeVisible,
            enter = slideInVertically { it } + fadeIn(),
            exit = slideOutVertically { it } + fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            val visibleTabsList =
                WorkspaceTab.entries.filter { tab ->
                    tab != WorkspaceTab.BUILD || isAndroidProject
                }
            val workspaceNavItems =
                visibleTabsList.map { tab ->
                    ExpressiveNavDestination(
                        id = tab.name.lowercase(),
                        label = tab.label,
                        iconRes = tab.iconRes,
                        selected = activeWorkspaceTab == tab,
                        badgeCount = if (tab == WorkspaceTab.GIT) gitChangesCount else 0,
                        onClick = {
                            if (drawerState.isOpen && !isPermanentDrawer) {
                                scope.launch { drawerState.close() }
                            }
                            activeWorkspaceTab = tab
                        },
                    )
                }

            ExpressiveBottomNavigationBar(items = workspaceNavItems)
        }
    }
}

@Composable
private fun EditorFragmentContent(
    mainViewModel: MainViewModel,
    drawerViewModel: DrawerViewModel,
    fileTreeViewModel: FileTreeViewModel,
    drawerState: DrawerState,
    currentProjectPath: String?,
    onOpenProjectsTab: () -> Unit,
    onCreateProject: (ProjectTemplate) -> Unit,
    onRunProject: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val visibleTabs = visibleTabsFor(mainViewModel, drawerViewModel)

    LaunchedEffect(Settings.show_all_files, currentProjectPath, visibleTabs.size) {
        val cur = mainViewModel.currentTab
        if (cur != null && visibleTabs.isNotEmpty() && cur !in visibleTabs) {
            val idx = mainViewModel.tabs.indexOf(visibleTabs.first())
            if (idx >= 0) mainViewModel.tabManager.setCurrentTab(idx)
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        if (visibleTabs.isEmpty()) {
            EmptyEditorState(
                projectPath = currentProjectPath,
                onOpenTree = {
                    drawerViewModel.unselectServiceTab()
                    scope.launch { drawerState.open() }
                },
                onCreateProject = { onCreateProject(ProjectTemplate.MINECRAFT_MOD) },
                onBrowseProjects = onOpenProjectsTab,
                onNewFile = { addDialog = true },
                onRunProject = onRunProject,
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
}

@Composable
private fun GitFragmentView(
    currentProjectPath: String?,
    gitTab: com.goydevv.arseide.drawer.DrawerTab?,
    onCloneRepo: () -> Unit,
    onOpenProjects: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var refreshGit by remember { mutableIntStateOf(0) }
    var gitRoot by remember(currentProjectPath, refreshGit) { mutableStateOf<String?>(null) }

    LaunchedEffect(currentProjectPath, refreshGit) {
        gitRoot = currentProjectPath?.let { findGitRoot(it) }
    }

    LaunchedEffect(gitRoot) {
        gitRoot?.let { root ->
            gitViewModel.get()?.loadRepository(root)
        }
    }

    if (gitRoot != null && gitTab != null) {
        Box(modifier = Modifier.fillMaxSize()) {
            gitTab.Content(modifier = Modifier.fillMaxSize())
        }
    } else {
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            ElevatedCard(
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.size(56.dp),
                    ) {
                        Icon(
                            painter = painterResource(drawables.git),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(14.dp),
                        )
                    }
                    Spacer(Modifier.height(14.dp))
                    Text(
                        text =
                            if (currentProjectPath != null) "Not a Git repository"
                            else "No Git repository open",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text =
                            if (currentProjectPath != null)
                                "Initialize Git in \"${File(currentProjectPath).name}\" or clone a remote repository."
                            else "Open a project from the Projects tab or clone a repository.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(18.dp))

                    if (currentProjectPath != null) {
                        Button(
                            onClick = {
                                scope.launch(Dispatchers.IO) {
                                    runCatching {
                                        val dir = File(currentProjectPath)
                                        Git.init().setDirectory(dir).call().use { git ->
                                            git.add().addFilepattern(".").call()
                                            git.commit()
                                                .setAuthor("GoyDevv", "noreply@arseide.dev")
                                                .setMessage("Initial commit")
                                                .call()
                                        }
                                        withContext(Dispatchers.Main) {
                                            refreshGit++
                                            toast("Initialized Git repository in ${dir.name}")
                                        }
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("Initialize Git (git init)")
                        }
                        Spacer(Modifier.height(8.dp))
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        FilledTonalButton(onClick = onCloneRepo, modifier = Modifier.weight(1f)) {
                            Text("Clone Repo")
                        }
                        OutlinedButton(onClick = onOpenProjects, modifier = Modifier.weight(1f)) {
                            Text("Projects")
                        }
                    }
                }
            }
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
    val activeColor = gitColor ?: MaterialTheme.colorScheme.onSecondaryContainer
    val inactiveColor = gitColor ?: MaterialTheme.colorScheme.onSurfaceVariant

    val pillContainerColor =
        when {
            isDraggableContent -> MaterialTheme.colorScheme.surfaceContainerHighest
            isSelected -> MaterialTheme.colorScheme.secondaryContainer
            else -> MaterialTheme.colorScheme.surfaceContainerLow
        }

    val pillBorder =
        if (isSelected) {
            BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.45f))
        } else {
            BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
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
        ElevatedCard(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(26.dp),
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(60.dp),
                ) {
                    Icon(
                        painter =
                            painterResource(
                                if (projectPath != null) drawables.folder_code else drawables.outline_folder
                            ),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(14.dp),
                    )
                }

                Spacer(Modifier.height(14.dp))

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
                        Spacer(Modifier.height(10.dp))
                        Surface(
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                            shape = RoundedCornerShape(50),
                        ) {
                            Text(
                                text = it,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                            )
                        }
                    }

                    Spacer(Modifier.height(20.dp))

                    Button(
                        onClick = onOpenTree,
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                    ) {
                        Icon(
                            painterResource(drawables.folder_code),
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("Open File Tree")
                    }

                    Spacer(Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        FilledTonalButton(
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
                                Text("Build / Run", maxLines = 1, overflow = TextOverflow.Ellipsis)
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
                                Text("Projects", maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                } else {
                    Text(
                        text = "Arse IDE",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "Open a file from the File Tree or select a project.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(20.dp))

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
                        FilledTonalButton(
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
                            Text("File Tree", maxLines = 1, overflow = TextOverflow.Ellipsis)
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
            "target",
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
        "toml" -> "TOML"
        else -> null
    }
