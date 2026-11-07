package com.goydevv.arseide.projects

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import android.text.format.DateUtils
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.goydevv.arseide.activities.main.MainActivity
import com.goydevv.arseide.activities.main.showAutoSetupDialogGlobal
import com.goydevv.arseide.exec.TerminalCommand
import com.goydevv.arseide.exec.isTerminalInstalled
import com.goydevv.arseide.exec.launchTerminal
import com.goydevv.arseide.file.sandboxHomeDir
import com.goydevv.arseide.horizon.HnmodConverterSheet
import com.goydevv.arseide.resources.drawables
import com.goydevv.arseide.resources.strings
import com.goydevv.arseide.runner.ProjectRunner
import com.goydevv.arseide.settings.Settings
import com.goydevv.arseide.terminal.AutoSetup
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * Scans the workspace home directory and returns recognized projects and repositories.
 */
object ProjectsBrowser {

    data class ProjectInfo(
        val dir: File,
        val name: String,
        val type: DetectedProjectType,
        val isGitRepo: Boolean,
        val createdAt: Long,
        val lastEdited: Long,
        val packageName: String?,
        val sizeBytes: Long,
    )

    private val HEAVY =
        setOf(".git", "node_modules", "build", ".gradle", ".idea", "dist", "out", ".dart_tool", "target")

    suspend fun scan(home: File = sandboxHomeDir()): List<ProjectInfo> =
        withContext(Dispatchers.IO) {
            val dirs = home.listFiles()?.filter { it.isDirectory && !it.name.startsWith(".") }.orEmpty()
            dirs
                .mapNotNull { dir ->
                    val type = ProjectTypeDetector.detect(dir)
                    val isGit = File(dir, ".git").exists()
                    if (type == DetectedProjectType.UNKNOWN && !isGit) return@mapNotNull null
                    ProjectInfo(
                        dir = dir,
                        name = dir.name,
                        type = type,
                        isGitRepo = isGit,
                        createdAt = creationTime(dir),
                        lastEdited = lastEditedTime(dir),
                        packageName = readPackageName(dir),
                        sizeBytes = dirSize(dir),
                    )
                }
                .sortedByDescending { it.lastEdited }
        }

    private fun creationTime(dir: File): Long =
        runCatching {
                java.nio.file.Files
                    .readAttributes(dir.toPath(), java.nio.file.attribute.BasicFileAttributes::class.java)
                    .creationTime()
                    .toMillis()
            }
            .getOrDefault(dir.lastModified())

    private fun lastEditedTime(dir: File): Long {
        var newest = dir.lastModified()
        dir.listFiles()?.forEach { child ->
            if (child.name in HEAVY) return@forEach
            newest = maxOf(newest, mostRecent(child, depth = 2))
        }
        return newest
    }

    private fun mostRecent(file: File, depth: Int): Long {
        var newest = file.lastModified()
        if (depth > 0 && file.isDirectory && file.name !in HEAVY) {
            file.listFiles()?.forEach { newest = maxOf(newest, mostRecent(it, depth - 1)) }
        }
        return newest
    }

    private fun readPackageName(dir: File): String? {
        File(dir, "horizon-native.json").takeIf { it.isFile }?.let { f ->
            runCatching {
                val obj = JSONObject(f.readText())
                val id = obj.optString("id", "")
                val hooks = obj.optInt("hooks", 1)
                if (id.isNotBlank()) return "hnmod:$id (hooks v$hooks)"
            }
        }
        val gradleFiles =
            listOf(
                File(dir, "app/build.gradle.kts"),
                File(dir, "app/build.gradle"),
                File(dir, "build.gradle.kts"),
                File(dir, "build.gradle"),
            )
        val appId = Regex("""applicationId\s*=?\s*["']([\w.]+)["']""")
        val namespace = Regex("""namespace\s*=?\s*["']([\w.]+)["']""")
        for (f in gradleFiles) {
            if (!f.isFile) continue
            val text = runCatching { f.readText() }.getOrNull() ?: continue
            appId.find(text)?.groupValues?.getOrNull(1)?.let { return it }
            namespace.find(text)?.groupValues?.getOrNull(1)?.let { return it }
        }
        val manifests =
            listOf(
                File(dir, "app/src/main/AndroidManifest.xml"),
                File(dir, "src/main/AndroidManifest.xml"),
                File(dir, "AndroidManifest.xml"),
            )
        val pkgAttr = Regex("""package\s*=\s*"([\w.]+)"""")
        for (m in manifests) {
            if (!m.isFile) continue
            val text = runCatching { m.readText() }.getOrNull() ?: continue
            pkgAttr.find(text)?.groupValues?.getOrNull(1)?.let { return it }
        }
        File(dir, "package.json").takeIf { it.isFile }?.let { pj ->
            runCatching { pj.readText() }.getOrNull()?.let { text ->
                Regex(""""name"\s*:\s*"([^"]+)"""").find(text)?.groupValues?.getOrNull(1)?.let { return it }
            }
        }
        return null
    }

    private fun dirSize(dir: File): Long =
        runCatching {
                var total = 0L
                dir.walkTopDown().onEnter { it.name !in setOf(".git", "build", ".gradle", "target", "node_modules") }.forEach {
                    if (it.isFile) total += it.length()
                }
                total
            }
            .getOrDefault(0L)
}

private fun formatSize(bytes: Long): String {
    val mib = bytes / 1048576.0
    return if (mib >= 0.1 || bytes == 0L) String.format(Locale.US, "%.2f MiB", mib)
    else String.format(Locale.US, "%.0f KiB", bytes / 1024.0)
}

private fun formatDate(millis: Long): String =
    if (millis <= 0L) "—" else SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(Date(millis))

private fun formatRelative(millis: Long): String =
    if (millis <= 0L) "—"
    else DateUtils.getRelativeTimeSpanString(millis, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS).toString()

/**
 * Projects tab fragment (`WorkspaceTab.PROJECTS`).
 *
 * - Clean header card with instant search/filter and an Auto-Setup banner if initial setup was skipped.
 * - Tapping any project card opens the interactive Project Action Sheet (Open in Editor, Build/Run,
 *   Convert to `.hnmod`, or Open Terminal Here).
 * - Tapping the `+` FAB opens the fast `+` action menu with all classic `+` options in one place:
 *   Create New Project, Convert `.jar` / Mod to `.hnmod`, Open Directory, Clone Git Repository,
 *   Open Internal Storage, Open Terminal Home (`~`), and Open Sandbox Terminal.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProjectsFragment(
    activeProjectPath: String?,
    onOpenProject: (File) -> Unit,
    onRunBuildProject: (File) -> Unit,
    onCreateProject: (ProjectTemplate) -> Unit,
    onCloneGitRepo: () -> Unit,
    onOpenFolderSheet: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val mainActivity = context as? MainActivity

    var loading by remember { mutableStateOf(true) }
    var refreshTrigger by remember { mutableIntStateOf(0) }
    var searchQuery by remember { mutableStateOf("") }
    val projects = remember { mutableStateListOf<ProjectsBrowser.ProjectInfo>() }

    var clickedProject by remember { mutableStateOf<ProjectsBrowser.ProjectInfo?>(null) }
    var showFabActionSheet by remember { mutableStateOf(false) }
    var converterTargetProject by remember { mutableStateOf<File?>(null) }
    var showConverterSheet by remember { mutableStateOf(false) }

    val setupNeeded = remember(refreshTrigger, Settings.auto_setup_completed) { AutoSetup.isSetupNeeded() }

    LaunchedEffect(refreshTrigger, activeProjectPath) {
        loading = true
        val result = ProjectsBrowser.scan()
        projects.clear()
        projects.addAll(result)
        loading = false
    }

    val filteredProjects =
        remember(projects.toList(), searchQuery) {
            if (searchQuery.isBlank()) projects.toList()
            else {
                val q = searchQuery.trim().lowercase()
                projects.filter {
                    it.name.lowercase().contains(q) ||
                        it.type.label.lowercase().contains(q) ||
                        (it.packageName?.lowercase()?.contains(q) == true)
                }
            }
        }

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier =
                Modifier.fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 14.dp)
                    .padding(bottom = 92.dp)
                    .animateContentSize(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Clean Workspace Header Card
            ElevatedCard(
                shape = RoundedCornerShape(22.dp),
                colors =
                    CardDefaults.elevatedCardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    ),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.fillMaxWidth().padding(18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(44.dp),
                        ) {
                            Icon(
                                painter = painterResource(drawables.folder_code),
                                contentDescription = null,
                                modifier = Modifier.padding(10.dp),
                            )
                        }
                        Spacer(Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Projects",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                            )
                            val plural = if (projects.size == 1) "" else "s"
                            Text(
                                text =
                                    if (loading) "Scanning workspace…"
                                    else "${projects.size} project$plural in workspace · Tap + for actions",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f),
                            )
                        }
                        IconButton(onClick = { refreshTrigger++ }) {
                            Icon(painterResource(drawables.refresh), contentDescription = "Refresh")
                        }
                    }

                    if (projects.size > 1) {
                        Spacer(Modifier.height(12.dp))
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            singleLine = true,
                            leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                            placeholder = { Text("Filter projects by name, type, or ID…") },
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }

            // If Auto-Setup was skipped on first launch, always provide a 1-tap setup banner
            if (setupNeeded) {
                OutlinedCard(
                    shape = RoundedCornerShape(18.dp),
                    colors =
                        CardDefaults.outlinedCardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                        ),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(38.dp),
                        ) {
                            Icon(
                                painter = painterResource(drawables.terminal),
                                contentDescription = null,
                                modifier = Modifier.padding(9.dp),
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text =
                                    if (!isTerminalInstalled()) "Linux Sandbox Not Installed"
                                    else "Toolchain Auto-Setup Incomplete",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                text = "Install Ubuntu 24.04 + JDK, Python, Node, Git & C/C++ compilers automatically.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        FilledTonalButton(onClick = { showAutoSetupDialogGlobal = true }) {
                            Text("Auto-Setup")
                        }
                    }
                }
            }

            when {
                loading ->
                    Box(modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(modifier = Modifier.size(32.dp), strokeWidth = 3.dp)
                    }
                filteredProjects.isEmpty() ->
                    OutlinedCard(
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(28.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Icon(
                                painter = painterResource(drawables.folder),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(42.dp),
                            )
                            Spacer(Modifier.height(10.dp))
                            Text(
                                text = if (searchQuery.isNotBlank()) "No matching projects" else "No projects yet",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = "Tap the + button below to create a project, open a folder, clone a Git repo, or convert a .jar mod.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.height(14.dp))
                            Button(onClick = { showFabActionSheet = true }) {
                                Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Open + Menu")
                            }
                        }
                    }
                else ->
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        filteredProjects.forEach { project ->
                            val isActive = activeProjectPath == project.dir.absolutePath
                            ProjectCard(
                                project = project,
                                isActive = isActive,
                                onClick = { clickedProject = project },
                            )
                        }
                    }
            }
        }

        ExtendedFloatingActionButton(
            onClick = { showFabActionSheet = true },
            icon = { Icon(Icons.Outlined.Add, contentDescription = "Quick Project Actions") },
            text = { Text("Add / Open", fontWeight = FontWeight.SemiBold) },
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
        )
    }

    // Interactive sheet when clicking a project card
    clickedProject?.let { proj ->
        val isMcJavaMod =
            proj.type == DetectedProjectType.FABRIC_MOD || proj.type == DetectedProjectType.FORGE_MOD
        ModalBottomSheet(
            onDismissRequest = { clickedProject = null },
            shape = RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp),
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(42.dp),
                    ) {
                        Icon(
                            painter = painterResource(iconForProjectType(proj.type)),
                            contentDescription = null,
                            modifier = Modifier.padding(9.dp),
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = proj.name,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = proj.type.label,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }

                Spacer(Modifier.height(4.dp))

                ActionSheetRow(
                    icon = drawables.folder_code,
                    title = "Open in Editor",
                    subtitle = "Browse files and edit source code",
                    highlight = true,
                    onClick = {
                        val dir = proj.dir
                        clickedProject = null
                        onOpenProject(dir)
                    },
                )

                ActionSheetRow(
                    icon = drawables.run,
                    title =
                        if (proj.type == DetectedProjectType.HORIZON_MIXIN_MOD)
                            "Compile & Package .hnmod"
                        else "Build / Run Project",
                    subtitle = "Run background build in sandbox",
                    onClick = {
                        val dir = proj.dir
                        clickedProject = null
                        onRunBuildProject(dir)
                    },
                )

                if (isMcJavaMod) {
                    ActionSheetRow(
                        icon = drawables.extension,
                        title = "Convert to Horizon .hnmod",
                        subtitle = "Port this ${proj.type.label} into a native .hnmod bundle",
                        onClick = {
                            val dir = proj.dir
                            clickedProject = null
                            converterTargetProject = dir
                            showConverterSheet = true
                        },
                    )
                }

                ActionSheetRow(
                    icon = drawables.terminal,
                    title = "Open Terminal Here",
                    subtitle = proj.dir.name,
                    onClick = {
                        val dir = proj.dir
                        clickedProject = null
                        if (mainActivity != null) {
                            launchTerminal(
                                activity = mainActivity,
                                terminalCommand =
                                    TerminalCommand(
                                        sandbox = true,
                                        exe = "/bin/bash",
                                        args = arrayOf("-l"),
                                        id = "proj_${dir.name}",
                                        terminatePreviousSession = false,
                                        workingDir = ProjectRunner.toSandboxPath(dir.absolutePath),
                                    ),
                            )
                        }
                    },
                )
            }
        }
    }

    // Fast `+` FAB Sheet containing ALL classic `+` options + .hnmod converter in one fast menu
    if (showFabActionSheet) {
        val is11Plus = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
        val isManager = is11Plus && Environment.isExternalStorageManager()
        val legacyGranted =
            !is11Plus &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) ==
                    PackageManager.PERMISSION_GRANTED
        val storageRoot = Environment.getExternalStorageDirectory()
        val canOpenInternalStorage = (isManager || legacyGranted) && storageRoot.canRead()

        ModalBottomSheet(
            onDismissRequest = { showFabActionSheet = false },
            shape = RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp),
        ) {
            Column(
                modifier =
                    Modifier.fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp)
                        .padding(bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = "Quick Actions (+)",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(2.dp))

                ActionSheetRow(
                    icon = drawables.folder_code,
                    title = stringResource(strings.create_project),
                    subtitle = stringResource(strings.create_project_desc),
                    highlight = true,
                    onClick = {
                        showFabActionSheet = false
                        onCreateProject(ProjectTemplate.MINECRAFT_MOD)
                    },
                )

                ActionSheetRow(
                    icon = drawables.extension,
                    title = "Convert Fabric / Forge Mod or .jar to .hnmod",
                    subtitle = "Pick a .jar file with the system picker or convert a workspace mod",
                    onClick = {
                        showFabActionSheet = false
                        converterTargetProject = null
                        showConverterSheet = true
                    },
                )

                ActionSheetRow(
                    icon = drawables.file_symlink,
                    title = stringResource(strings.open_directory),
                    subtitle = stringResource(strings.open_dir_desc),
                    onClick = {
                        showFabActionSheet = false
                        onOpenFolderSheet()
                    },
                )

                ActionSheetRow(
                    icon = drawables.git,
                    title = stringResource(strings.clone_repo),
                    subtitle = stringResource(strings.clone_repo_desc),
                    onClick = {
                        showFabActionSheet = false
                        onCloneGitRepo()
                    },
                )

                if (canOpenInternalStorage) {
                    ActionSheetRow(
                        icon = drawables.android,
                        title = stringResource(strings.internal_storage),
                        subtitle = stringResource(strings.open_internal_storage),
                        onClick = {
                            showFabActionSheet = false
                            onOpenProject(storageRoot)
                        },
                    )
                }

                ActionSheetRow(
                    icon = drawables.terminal,
                    title = stringResource(strings.terminal_home),
                    subtitle = stringResource(strings.terminal_home_desc),
                    onClick = {
                        showFabActionSheet = false
                        onOpenProject(sandboxHomeDir())
                    },
                )

                if (setupNeeded) {
                    ActionSheetRow(
                        icon = drawables.bolt,
                        title = "Run Sandbox & Toolchain Auto-Setup",
                        subtitle = "Download Ubuntu 24.04 rootfs and install compilers automatically",
                        onClick = {
                            showFabActionSheet = false
                            showAutoSetupDialogGlobal = true
                        },
                    )
                }
            }
        }
    }

    if (showConverterSheet) {
        HnmodConverterSheet(
            initialProjectDir = converterTargetProject,
            onDismiss = { showConverterSheet = false },
            onOpenConvertedProject = { dir ->
                showConverterSheet = false
                refreshTrigger++
                onOpenProject(dir)
            },
            onBuildConvertedProject = { dir ->
                showConverterSheet = false
                refreshTrigger++
                onRunBuildProject(dir)
            },
        )
    }
}

@Composable
private fun ActionSheetRow(
    icon: Int,
    title: String,
    subtitle: String,
    highlight: Boolean = false,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color =
            if (highlight) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor =
            if (highlight) MaterialTheme.colorScheme.onPrimaryContainer
            else MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painter = painterResource(icon),
                contentDescription = null,
                modifier = Modifier.size(22.dp),
            )
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color =
                        if (highlight) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(
                painter = painterResource(drawables.chevron_right),
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProjectsBrowserSheet(onDismiss: () -> Unit, onOpenProject: (File) -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var loading by remember { mutableStateOf(true) }
    val projects = remember { mutableStateListOf<ProjectsBrowser.ProjectInfo>() }

    LaunchedEffect(Unit) {
        loading = true
        val result = ProjectsBrowser.scan()
        projects.clear()
        projects.addAll(result)
        loading = false
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 24.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(44.dp),
                ) {
                    Icon(
                        painter = painterResource(drawables.folder_code),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(10.dp),
                    )
                }
                Spacer(Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Projects",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    val pluralSuffix = if (projects.size == 1) "" else "s"
                    Text(
                        text =
                            if (loading) "Scanning…"
                            else "${projects.size} project$pluralSuffix",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            when {
                loading ->
                    Box(modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(modifier = Modifier.size(32.dp), strokeWidth = 3.dp)
                    }
                projects.isEmpty() ->
                    Box(modifier = Modifier.fillMaxWidth().padding(vertical = 40.dp), contentAlignment = Alignment.Center) {
                        Text(
                            "No projects found.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                else ->
                    Column(
                        modifier = Modifier.fillMaxWidth().heightIn(max = 560.dp).verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        projects.forEach { project ->
                            ProjectCard(project = project, isActive = false) { onOpenProject(project.dir) }
                        }
                    }
            }
        }
    }
}

@Composable
private fun ProjectCard(
    project: ProjectsBrowser.ProjectInfo,
    isActive: Boolean,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by
        animateFloatAsState(
            targetValue = if (isPressed) 0.98f else 1f,
            animationSpec = spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessMedium),
            label = "projectCardScale",
        )

    OutlinedCard(
        onClick = onClick,
        interactionSource = interactionSource,
        shape = RoundedCornerShape(18.dp),
        colors =
            CardDefaults.outlinedCardColors(
                containerColor =
                    if (isActive) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                    else MaterialTheme.colorScheme.surfaceContainerLow,
            ),
        modifier =
            Modifier.fillMaxWidth().graphicsLayer {
                scaleX = scale
                scaleY = scale
            },
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color =
                        if (isActive) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.secondaryContainer,
                    contentColor =
                        if (isActive) MaterialTheme.colorScheme.onPrimary
                        else MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.size(40.dp),
                ) {
                    Icon(
                        painter = painterResource(iconForProjectType(project.type)),
                        contentDescription = null,
                        modifier = Modifier.padding(9.dp),
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = project.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (isActive) {
                            Spacer(Modifier.width(8.dp))
                            StatusPill("Open", PillTone.SUCCESS)
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = if (project.type == DetectedProjectType.UNKNOWN) "Repository" else project.type.label,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        if (project.isGitRepo) {
                            Icon(
                                painter = painterResource(drawables.git),
                                contentDescription = "Git repository",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(start = 8.dp).size(14.dp),
                            )
                        }
                    }
                }
                StatusPill(formatSize(project.sizeBytes), PillTone.NEUTRAL)
            }

            Spacer(Modifier.height(10.dp))
            project.packageName?.let { MetaLine(icon = drawables.folder_code, label = "ID / Pkg", value = it) }
            MetaLine(icon = drawables.info, label = "Created", value = formatDate(project.createdAt))
            MetaLine(icon = drawables.edit, label = "Modified", value = formatRelative(project.lastEdited))
        }
    }
}

@Composable
private fun MetaLine(icon: Int, label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(
            painter = painterResource(icon),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(15.dp),
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(76.dp),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
