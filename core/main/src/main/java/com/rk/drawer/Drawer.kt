package com.rk.drawer

import android.content.Intent
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.rk.activities.main.MainActivity
import com.rk.activities.main.gitViewModel
import com.rk.activities.settings.SettingsActivity
import com.rk.components.DoubleInputDialog
import com.rk.components.GlassBottomNavigationBar
import com.rk.components.GlassCard
import com.rk.components.GlassNavDestination
import com.rk.components.GlassSurface
import com.rk.components.codeSearchDialog
import com.rk.components.fileSearchDialog
import com.rk.components.isDrawerExpanded
import com.rk.components.isPermanentDrawer
import com.rk.exec.isTerminalInstalled
import com.rk.exec.launchTerminal
import com.rk.file.FileWrapper
import com.rk.file.sandboxHomeDir
import com.rk.file.toFileObject
import com.rk.filetree.FileTreeTab
import com.rk.filetree.ProjectCloseConfirmationDialog
import com.rk.git.ProgressCoordinator
import com.rk.icons.XedIcon
import com.rk.projects.CreateProjectDialog
import com.rk.projects.ProjectDependencies
import com.rk.projects.ProjectScaffolder
import com.rk.projects.ProjectTemplate
import com.rk.resources.drawables
import com.rk.resources.getString
import com.rk.resources.strings
import com.rk.settings.Settings
import com.rk.utils.StorageUtils
import com.rk.utils.dialogRes
import com.rk.utils.toast
import java.io.File
import kotlinx.coroutines.launch

/**
 * Shared trigger so the Workspace Glass Bottom Bar (or empty state) can open the Add/Create Project
 * sheet directly.
 */
var showAddProjectSheetGlobal by mutableStateOf(false)
var showCreateProjectDialogGlobal by mutableStateOf(false)
var showProjectsBrowserGlobal by mutableStateOf(false)

private fun validateValue(value: String): String? {
    return when {
        value.isBlank() -> strings.value_empty_err.getString()
        else -> null
    }
}

@Composable
fun DrawerContent(fullscreen: Boolean) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val mainActivity = LocalActivity.current as MainActivity
    val viewModel = mainActivity.drawerViewModel

    val openFolder =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.OpenDocumentTree(),
            onResult = { uri ->
                uri?.let {
                    runCatching {
                            context.contentResolver.takePersistableUriPermission(
                                it,
                                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                            )
                        }
                        .onFailure { it.printStackTrace() }

                    scope.launch { viewModel.addFileTreeTab(it.toFileObject(expectedIsFile = false), save = true) }
                }
            },
        )

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        if (viewModel.isLoading) {
            CircularProgressIndicator()
        } else {
            var showAddDialog by rememberSaveable { mutableStateOf(false) }
            var closeProjectTarget by remember { mutableStateOf<DrawerTab?>(null) }
            var showOpenDirWarning by remember { mutableStateOf(false) }
            var showProjectSwitcher by remember { mutableStateOf(false) }

            // Git clone dialog
            var showGitCloneDialog by remember { mutableStateOf(false) }

            // Create project dialog
            var showCreateProjectDialog by remember { mutableStateOf(false) }

            // Projects / Repositories browser
            var showProjectsBrowser by remember { mutableStateOf(false) }

            // Sync with global triggers from main workspace bottom navigation bar
            if (showAddProjectSheetGlobal) {
                showAddProjectSheetGlobal = false
                showAddDialog = true
            }
            if (showCreateProjectDialogGlobal) {
                showCreateProjectDialogGlobal = false
                showCreateProjectDialog = true
            }
            if (showProjectsBrowserGlobal) {
                showProjectsBrowserGlobal = false
                showProjectsBrowser = true
            }

            // Missing dependencies prompt after project creation
            var pendingDeps by remember { mutableStateOf<List<ProjectDependencies.Tool>>(emptyList()) }

            var repoURL by remember { mutableStateOf("") }
            var repoBranch by remember { mutableStateOf("main") }

            var repoURLError by remember { mutableStateOf<String?>(null) }
            var repoBranchError by remember { mutableStateOf<String?>(null) }

            // Git clone progress dialog
            var showCloneProgressDialog by remember { mutableStateOf(false) }
            var progress by remember { mutableIntStateOf(0) }
            var maxProgress by remember { mutableIntStateOf(0) }
            var message by remember { mutableStateOf(strings.cloning.getString()) }

            val monitor = remember {
                object : ProgressCoordinator {
                    private var cancelled = false

                    override fun start(totalTasks: Int) {}

                    override fun beginTask(title: String?, totalWork: Int) {
                        message = title ?: strings.cloning.getString()
                        maxProgress = totalWork
                        progress = 0
                    }

                    override fun update(completed: Int) {
                        progress += completed
                    }

                    override fun cancel() {
                        cancelled = true
                        hideDialog()
                    }

                    override fun endTask() {}

                    override fun isCancelled(): Boolean = cancelled || Thread.currentThread().isInterrupted

                    override fun showDialog() {
                        showCloneProgressDialog = true
                        progress = 0
                        maxProgress = 0
                        message = strings.cloning.getString()
                    }

                    override fun hideDialog() {
                        showCloneProgressDialog = false
                    }
                }
            }

            val cloneIntoSandbox: () -> Unit = {
                val repoName =
                    repoURL.substringAfterLast("/").substringBeforeLast(".").ifBlank { "repo" }
                scope.launch {
                    val baseDir = sandboxHomeDir()
                    var target = File(baseDir, repoName)
                    var suffix = 1
                    while (target.exists()) {
                        target = File(baseDir, "$repoName-$suffix")
                        suffix++
                    }
                    val fileObject = FileWrapper(target)
                    gitViewModel
                        .get()
                        ?.cloneRepository(
                            repoURL = repoURL,
                            repoBranch = repoBranch,
                            targetDir = target,
                            progressCoordinator = monitor,
                            onComplete = { success ->
                                repoURL = ""
                                repoBranch = "main"
                                repoURLError = null
                                repoBranchError = null
                                if (success) {
                                    viewModel.addFileTreeTab(fileObject, save = true)
                                }
                            },
                        )
                }
            }

            val activeProjectRoot = (viewModel.currentDrawerTab as? FileTreeTab)?.root?.getAbsolutePath()
            val gitChangesCount =
                activeProjectRoot?.let { gitViewModel.get()?.changes?.get(it)?.size } ?: 0
            val gitTab = viewModel.serviceTabs.firstOrNull()
            val isGitSupported = gitTab?.isSupported() == true
            val isGitSelected = viewModel.currentServiceTab != null && viewModel.currentServiceTab == gitTab

            Box(modifier = Modifier.fillMaxSize()) {
                Column(modifier = Modifier.fillMaxSize()) {
                    // --- Top Glass Project Header (replaces cramped left rail project stack) ---
                    DrawerProjectHeader(
                        viewModel = viewModel,
                        showProjectSwitcher = showProjectSwitcher,
                        onToggleProjectSwitcher = { showProjectSwitcher = !showProjectSwitcher },
                        onDismissProjectSwitcher = { showProjectSwitcher = false },
                        onCloseProject = { tab -> closeProjectTarget = tab },
                        onOpenAddSheet = { showAddDialog = true },
                    )

                    // --- Full-Width Drawer Viewport (100% width for File Tree & Git) ---
                    Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                        Crossfade(targetState = viewModel.currentDrawerTab, label = "file tree") { tab ->
                            if (viewModel.currentServiceTab == null) {
                                if (tab != null) {
                                    tab.Content(modifier = Modifier.fillMaxSize())
                                } else {
                                    DrawerEmptyProjectState(
                                        onCreateProject = { showCreateProjectDialog = true },
                                        onBrowseProjects = { showProjectsBrowser = true },
                                        onOpenMore = { showAddDialog = true },
                                    )
                                }
                            }
                        }

                        Crossfade(targetState = viewModel.currentServiceTab, label = "service tab") { tab ->
                            tab?.Content(modifier = Modifier.fillMaxSize())
                        }
                    }
                }

                // --- Floating Glassmorphic Bottom Navigation Bar with Blur ---
                val navItems =
                    buildList {
                        add(
                            GlassNavDestination(
                                id = "files",
                                label = stringResource(strings.files),
                                iconRes = drawables.outline_folder,
                                selected = viewModel.currentServiceTab == null,
                                onClick = {
                                    if (viewModel.currentServiceTab != null) {
                                        viewModel.unselectServiceTab()
                                    } else if (viewModel.currentDrawerTab == null) {
                                        showAddDialog = true
                                    }
                                },
                            )
                        )
                        if (gitTab != null && isGitSupported) {
                            add(
                                GlassNavDestination(
                                    id = "git",
                                    label = gitTab.getName(),
                                    iconRes = drawables.git,
                                    selected = isGitSelected,
                                    enabled = gitTab.isEnabled(),
                                    badgeCount = gitChangesCount,
                                    onClick = {
                                        if (isGitSelected) {
                                            viewModel.unselectServiceTab()
                                        } else {
                                            viewModel.selectServiceTab(gitTab)
                                        }
                                    },
                                )
                            )
                        }
                        add(
                            GlassNavDestination(
                                id = "add",
                                label = stringResource(strings.add),
                                iconVector = Icons.Outlined.Add,
                                selected = showAddDialog,
                                isAccentAction = viewModel.drawerTabs.isEmpty(),
                                onClick = { showAddDialog = true },
                            )
                        )
                        if (viewModel.currentDrawerTab is FileTreeTab) {
                            add(
                                GlassNavDestination(
                                    id = "search",
                                    label = stringResource(strings.search),
                                    iconVector = Icons.Outlined.Search,
                                    selected = false,
                                    onClick = { fileSearchDialog = true },
                                    onLongClick = { codeSearchDialog = true },
                                )
                            )
                        }
                        add(
                            GlassNavDestination(
                                id = "settings",
                                label = stringResource(strings.settings),
                                iconRes = drawables.settings,
                                selected = false,
                                onClick = {
                                    mainActivity.startActivity(Intent(mainActivity, SettingsActivity::class.java))
                                },
                            )
                        )
                    }

                GlassBottomNavigationBar(
                    items = navItems,
                    compact = true,
                    modifier =
                        Modifier.align(Alignment.BottomCenter)
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                )
            }

            if (showAddDialog) {
                AddProjectSheet(
                    onDismiss = { showAddDialog = false },
                    openFolder = openFolder,
                    onOpenDirectory = {
                        if (Settings.open_dir_warning_dismissed) {
                            openFolder.launch(null)
                        } else {
                            showOpenDirWarning = true
                        }
                    },
                    onAddProject = { fileObject -> scope.launch { viewModel.addFileTreeTab(fileObject, true) } },
                    showPrivateFileWarning = { callback ->
                        dialogRes(
                            title = strings.attention.getString(),
                            msg = strings.warning_private_dir.getString(),
                            onOk = { callback.invoke() },
                        )
                    },
                    showGitCloneDialog = {
                        showAddDialog = false
                        showGitCloneDialog = true
                    },
                    showCreateProject = {
                        showAddDialog = false
                        showCreateProjectDialog = true
                    },
                    showProjects = {
                        showAddDialog = false
                        showProjectsBrowser = true
                    },
                )
            }

            if (showOpenDirWarning) {
                OpenDirectoryWarningDialog(
                    onConfirm = {
                        showOpenDirWarning = false
                        openFolder.launch(null)
                    },
                    onCancel = { showOpenDirWarning = false },
                )
            }

            if (showCloneProgressDialog) {
                AlertDialog(
                    title = { Text(stringResource(strings.cloning)) },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            Text(
                                text = "$message ($progress/$maxProgress)",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            LinearProgressIndicator(
                                progress = { if (maxProgress > 0) progress.toFloat() / maxProgress else 0f }
                            )
                        }
                    },
                    onDismissRequest = {},
                    confirmButton = {},
                    dismissButton = { TextButton({ monitor.cancel() }) { Text(stringResource(strings.cancel)) } },
                )
            }

            if (showGitCloneDialog) {
                DoubleInputDialog(
                    title = stringResource(strings.clone_repo),
                    firstInputLabel = stringResource(strings.repo_url),
                    firstInputValue = repoURL,
                    onFirstInputValueChange = {
                        repoURL = it
                        repoURLError = validateValue(repoURL)
                    },
                    secondInputLabel = stringResource(strings.branch),
                    secondInputValue = repoBranch,
                    onSecondInputValueChange = {
                        repoBranch = it
                        repoBranchError = validateValue(repoBranch)
                    },
                    firstErrorMessage = repoURLError,
                    secondErrorMessage = repoBranchError,
                    onConfirm = {
                        showGitCloneDialog = false
                        cloneIntoSandbox()
                    },
                    onDismiss = {
                        showGitCloneDialog = false
                        repoURL = ""
                        repoBranch = "main"
                        repoURLError = null
                        repoBranchError = null
                    },
                    confirmText = stringResource(strings.ok),
                    confirmEnabled = repoURLError == null && repoBranchError == null && repoURL.isNotBlank(),
                )
            }

            if (showCreateProjectDialog) {
                CreateProjectDialog(
                    projectsDir = sandboxHomeDir(),
                    onDismiss = { showCreateProjectDialog = false },
                    onCreate = { config ->
                        showCreateProjectDialog = false
                        scope.launch {
                            when (val result = ProjectScaffolder.scaffold(config)) {
                                is ProjectScaffolder.Result.Success -> {
                                    viewModel.addFileTreeTab(FileWrapper(result.projectRoot), save = true)
                                    result.warning?.let { toast(it) }
                                    if (
                                        config.template.recommendsSandbox &&
                                            StorageUtils.isOnSharedStorage(result.projectRoot)
                                    ) {
                                        toast(strings.shared_storage_build_warning)
                                    }
                                    if (config.template == ProjectTemplate.ANDROID_COMPOSE && isTerminalInstalled()) {
                                        val fo = FileWrapper(result.projectRoot)
                                        com.rk.runner.ProjectRunner.sync(mainActivity, fo, fo)
                                    } else if (isTerminalInstalled()) {
                                        val tools = ProjectDependencies.requiredTools(config)
                                        if (tools.isNotEmpty()) {
                                            val missing = ProjectDependencies.missingTools(tools)
                                            if (missing.isNotEmpty()) pendingDeps = missing
                                        }
                                    }
                                }
                                is ProjectScaffolder.Result.Failure -> toast(result.message)
                            }
                        }
                    },
                )
            }

            if (showProjectsBrowser) {
                com.rk.projects.ProjectsBrowserSheet(
                    onDismiss = { showProjectsBrowser = false },
                    onOpenProject = { dir ->
                        showProjectsBrowser = false
                        scope.launch { viewModel.addFileTreeTab(FileWrapper(dir), save = true) }
                    },
                )
            }

            if (pendingDeps.isNotEmpty()) {
                val deps = pendingDeps
                AlertDialog(
                    onDismissRequest = { pendingDeps = emptyList() },
                    title = { Text(stringResource(strings.install_dependencies)) },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(stringResource(strings.install_dependencies_msg))
                            deps.forEach { Text("•  ${it.name}") }
                        }
                    },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                pendingDeps = emptyList()
                                launchTerminal(mainActivity, ProjectDependencies.installCommand(deps))
                            }
                        ) {
                            Text(stringResource(strings.install_action))
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { pendingDeps = emptyList() }) {
                            Text(stringResource(strings.cancel))
                        }
                    },
                )
            }

            closeProjectTarget?.let { targetTab ->
                ProjectCloseConfirmationDialog(
                    projectName = targetTab.getName(),
                    onConfirm = {
                        closeProjectTarget = null
                        viewModel.removeDrawerTab(targetTab, save = true)
                    },
                    onDismiss = { closeProjectTarget = null },
                )
            }
        }
    }
}

@Composable
private fun DrawerProjectHeader(
    viewModel: DrawerViewModel,
    showProjectSwitcher: Boolean,
    onToggleProjectSwitcher: () -> Unit,
    onDismissProjectSwitcher: () -> Unit,
    onCloseProject: (DrawerTab) -> Unit,
    onOpenAddSheet: () -> Unit,
) {
    val currentTab = viewModel.currentDrawerTab
    val supportedTabs = viewModel.drawerTabs.filter { it.isSupported() }

    GlassSurface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
        shape = RoundedCornerShape(18.dp),
        shadowElevation = 4.dp,
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // Active project selector pill
            Box(modifier = Modifier.weight(1f)) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.65f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.22f)),
                    modifier =
                        Modifier.fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .combinedClickable(
                                onClick = {
                                    if (supportedTabs.isEmpty()) onOpenAddSheet()
                                    else onToggleProjectSwitcher()
                                },
                                onLongClick = { currentTab?.let { onCloseProject(it) } },
                            ),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (currentTab != null) {
                            XedIcon(currentTab.getIcon(), modifier = Modifier.size(18.dp))
                        } else {
                            Icon(
                                painter = painterResource(drawables.folder_code),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                        Spacer(Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = currentTab?.getName() ?: stringResource(strings.no_folder_opened),
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            if (supportedTabs.size > 1) {
                                Text(
                                    text = "${supportedTabs.size} workspaces open · tap to switch",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                        Spacer(Modifier.width(6.dp))
                        Icon(
                            painter = painterResource(drawables.chevron_down),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }

                DropdownMenu(
                    expanded = showProjectSwitcher,
                    onDismissRequest = onDismissProjectSwitcher,
                ) {
                    supportedTabs.forEach { tab ->
                        val isSelected = viewModel.currentDrawerTab == tab
                        DropdownMenuItem(
                            leadingIcon = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    if (isSelected) {
                                        Box(
                                            modifier =
                                                Modifier.size(6.dp)
                                                    .clip(CircleShape)
                                                    .background(MaterialTheme.colorScheme.primary)
                                        )
                                        Spacer(Modifier.width(6.dp))
                                    }
                                    XedIcon(tab.getIcon())
                                }
                            },
                            text = {
                                Text(
                                    text = tab.getName(),
                                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                    color =
                                        if (isSelected) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            },
                            trailingIcon = {
                                IconButton(
                                    onClick = {
                                        onDismissProjectSwitcher()
                                        onCloseProject(tab)
                                    },
                                    modifier = Modifier.size(28.dp),
                                ) {
                                    Icon(
                                        painter = painterResource(drawables.close),
                                        contentDescription = stringResource(strings.close),
                                        modifier = Modifier.size(15.dp),
                                    )
                                }
                            },
                            onClick = {
                                onDismissProjectSwitcher()
                                viewModel.selectDrawerTab(tab)
                            },
                        )
                    }
                    if (supportedTabs.isNotEmpty()) {
                        HorizontalDivider()
                    }
                    DropdownMenuItem(
                        leadingIcon = { Icon(Icons.Outlined.Add, contentDescription = null) },
                        text = { Text(stringResource(strings.add)) },
                        onClick = {
                            onDismissProjectSwitcher()
                            onOpenAddSheet()
                        },
                    )
                }
            }

            // Quick Add Project button
            IconButton(
                onClick = onOpenAddSheet,
                modifier = Modifier.size(36.dp),
            ) {
                Icon(
                    imageVector = Icons.Outlined.Add,
                    contentDescription = stringResource(strings.add),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }

            // Expand / collapse drawer width toggle (for modal drawer)
            if (!isPermanentDrawer) {
                val expandRotation by
                    animateFloatAsState(
                        targetValue = if (isDrawerExpanded) 180f else 0f,
                        animationSpec = spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessLow),
                        label = "expandRotation",
                    )
                val expandScale by
                    animateFloatAsState(
                        targetValue = if (isDrawerExpanded) 1.15f else 1f,
                        animationSpec = spring(dampingRatio = 0.4f, stiffness = Spring.StiffnessMedium),
                        label = "expandScale",
                    )
                IconButton(
                    onClick = { isDrawerExpanded = !isDrawerExpanded },
                    modifier = Modifier.size(36.dp),
                ) {
                    Icon(
                        painter = painterResource(drawables.chevron_right),
                        contentDescription =
                            stringResource(
                                if (isDrawerExpanded) strings.collapse_drawer else strings.expand_drawer
                            ),
                        modifier =
                            Modifier.graphicsLayer {
                                rotationZ = expandRotation
                                scaleX = expandScale
                                scaleY = expandScale
                            },
                    )
                }
            }
        }
    }
}

@Composable
private fun DrawerEmptyProjectState(
    onCreateProject: () -> Unit,
    onBrowseProjects: () -> Unit,
    onOpenMore: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp).padding(bottom = 84.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        GlassCard(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(24.dp),
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.8f),
                    modifier = Modifier.size(56.dp),
                ) {
                    Icon(
                        painter = painterResource(drawables.folder_code),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(14.dp),
                    )
                }
                Spacer(modifier = Modifier.height(14.dp))
                Text(
                    text = stringResource(strings.no_folder_opened),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Create a new project from a build-ready template, browse your sandbox repositories, or open a folder.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                Spacer(modifier = Modifier.height(18.dp))
                Button(
                    onClick = onCreateProject,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(
                        painter = painterResource(drawables.add),
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(strings.create_project))
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedButton(
                        onClick = onBrowseProjects,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("Projects", maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    OutlinedButton(
                        onClick = onOpenMore,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("More…", maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}
