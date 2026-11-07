package com.goydevv.arseide.drawer

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
import androidx.compose.material3.ElevatedCard
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
import com.goydevv.arseide.activities.main.MainActivity
import com.goydevv.arseide.activities.main.gitViewModel
import com.goydevv.arseide.components.DoubleInputDialog
import com.goydevv.arseide.components.addDialog
import com.goydevv.arseide.components.fileSearchDialog
import com.goydevv.arseide.components.isDrawerExpanded
import com.goydevv.arseide.components.isPermanentDrawer
import com.goydevv.arseide.exec.isTerminalInstalled
import com.goydevv.arseide.exec.launchTerminal
import com.goydevv.arseide.file.FileWrapper
import com.goydevv.arseide.file.sandboxHomeDir
import com.goydevv.arseide.file.toFileObject
import com.goydevv.arseide.filetree.FileTreeTab
import com.goydevv.arseide.filetree.ProjectCloseConfirmationDialog
import com.goydevv.arseide.git.ProgressCoordinator
import com.goydevv.arseide.icons.XedIcon
import com.goydevv.arseide.projects.CreateProjectDialog
import com.goydevv.arseide.projects.ProjectDependencies
import com.goydevv.arseide.projects.ProjectScaffolder
import com.goydevv.arseide.projects.ProjectTemplate
import com.goydevv.arseide.resources.drawables
import com.goydevv.arseide.resources.getString
import com.goydevv.arseide.resources.strings
import com.goydevv.arseide.settings.Settings
import com.goydevv.arseide.utils.StorageUtils
import com.goydevv.arseide.utils.dialogRes
import com.goydevv.arseide.utils.toast
import java.io.File
import kotlinx.coroutines.launch

var showAddProjectSheetGlobal by mutableStateOf(false)
var showCreateProjectDialogGlobal by mutableStateOf(false)
var createProjectInitialTemplateGlobal by mutableStateOf(ProjectTemplate.ANDROID_COMPOSE)
var showProjectsBrowserGlobal by mutableStateOf(false)
var showGitCloneDialogGlobal by mutableStateOf(false)

private fun validateValue(value: String): String? =
    if (value.isBlank()) strings.value_empty_err.getString() else null

/**
 * Pure Material You **File Tree Drawer** (opened from the top-left file logo button).
 *
 * Dedicated strictly to the active project's File Tree — no duplicate bottom navigation bar inside
 * the drawer. Project management, Horizon Mod Studio, Git, and Settings live in the main bottom
 * navigation fragments.
 */
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
            var showGitCloneDialog by remember { mutableStateOf(false) }
            var showCreateProjectDialog by remember { mutableStateOf(false) }
            var showProjectsBrowser by remember { mutableStateOf(false) }

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
            if (showGitCloneDialogGlobal) {
                showGitCloneDialogGlobal = false
                showGitCloneDialog = true
            }

            // Ensure the drawer always displays the FileTreeTab, not a service tab
            if (viewModel.currentServiceTab != null) {
                viewModel.unselectServiceTab()
            }

            var pendingDeps by remember { mutableStateOf<List<ProjectDependencies.Tool>>(emptyList()) }
            var repoURL by remember { mutableStateOf("") }
            var repoBranch by remember { mutableStateOf("main") }
            var repoURLError by remember { mutableStateOf<String?>(null) }
            var repoBranchError by remember { mutableStateOf<String?>(null) }

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

            Column(modifier = Modifier.fillMaxSize()) {
                // --- Material You File Explorer Header ---
                DrawerFileTreeHeader(
                    viewModel = viewModel,
                    showProjectSwitcher = showProjectSwitcher,
                    onToggleProjectSwitcher = { showProjectSwitcher = !showProjectSwitcher },
                    onDismissProjectSwitcher = { showProjectSwitcher = false },
                    onCloseProject = { tab -> closeProjectTarget = tab },
                    onOpenAddSheet = { showAddDialog = true },
                    onNewFileInProject = { addDialog = true },
                    onSearchFiles = { fileSearchDialog = true },
                )

                HorizontalDivider(
                    thickness = 0.5.dp,
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
                )

                // --- 100% Full-Height File Tree Viewport ---
                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    Crossfade(targetState = viewModel.currentDrawerTab, label = "file_tree_drawer") { tab ->
                        if (tab != null) {
                            tab.Content(modifier = Modifier.fillMaxSize())
                        } else {
                            DrawerEmptyProjectState(
                                onCreateProject = {
                                    createProjectInitialTemplateGlobal = ProjectTemplate.ANDROID_COMPOSE
                                    showCreateProjectDialog = true
                                },
                                onBrowseProjects = { showProjectsBrowser = true },
                                onOpenMore = { showAddDialog = true },
                            )
                        }
                    }
                }
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
                    initialTemplate = createProjectInitialTemplateGlobal,
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
                                        com.goydevv.arseide.runner.ProjectRunner.sync(mainActivity, fo, fo)
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
                com.goydevv.arseide.projects.ProjectsBrowserSheet(
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
private fun DrawerFileTreeHeader(
    viewModel: DrawerViewModel,
    showProjectSwitcher: Boolean,
    onToggleProjectSwitcher: () -> Unit,
    onDismissProjectSwitcher: () -> Unit,
    onCloseProject: (DrawerTab) -> Unit,
    onOpenAddSheet: () -> Unit,
    onNewFileInProject: () -> Unit,
    onSearchFiles: () -> Unit,
) {
    val currentTab = viewModel.currentDrawerTab
    val supportedTabs = viewModel.drawerTabs.filter { it.isSupported() }

    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            // Project / Folder Switcher Pill
            Box(modifier = Modifier.weight(1f)) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier =
                        Modifier.fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .combinedClickable(
                                onClick = {
                                    if (supportedTabs.isEmpty()) onOpenAddSheet()
                                    else onToggleProjectSwitcher()
                                },
                                onLongClick = { currentTab?.let { onCloseProject(it) } },
                            ),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            painter = painterResource(drawables.folder_code),
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = currentTab?.getName() ?: "File Explorer",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text =
                                    if (supportedTabs.isEmpty()) "Tap to open folder"
                                    else "File Tree · ${supportedTabs.size} open",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.78f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Spacer(Modifier.width(4.dp))
                        Icon(
                            painter = painterResource(drawables.chevron_down),
                            contentDescription = null,
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
                        text = { Text("Open / Add Folder…") },
                        onClick = {
                            onDismissProjectSwitcher()
                            onOpenAddSheet()
                        },
                    )
                }
            }

            if (currentTab is FileTreeTab) {
                IconButton(onClick = onSearchFiles, modifier = Modifier.size(36.dp)) {
                    Icon(
                        imageVector = Icons.Outlined.Search,
                        contentDescription = stringResource(strings.search),
                    )
                }
                IconButton(onClick = onNewFileInProject, modifier = Modifier.size(36.dp)) {
                    Icon(
                        imageVector = Icons.Outlined.Add,
                        contentDescription = stringResource(strings.new_file),
                    )
                }
            }

            if (!isPermanentDrawer) {
                val expandRotation by
                    animateFloatAsState(
                        targetValue = if (isDrawerExpanded) 180f else 0f,
                        animationSpec = spring(dampingRatio = 0.65f, stiffness = Spring.StiffnessMediumLow),
                        label = "expandRotation",
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
                        modifier = Modifier.graphicsLayer { rotationZ = expandRotation },
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
        modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp),
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
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
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
                    text = "Open a project from the Projects tab or create a new Android, Fabric, or Horizon (.hmod) project.",
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
                        Text("Open Folder", maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}
