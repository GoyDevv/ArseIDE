package com.goydevv.arseide.projects

import android.text.format.DateUtils
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.SmallFloatingActionButton
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.goydevv.arseide.file.sandboxHomeDir
import com.goydevv.arseide.horizon.HorizonModValidator
import com.goydevv.arseide.resources.drawables
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Scans the sandbox home — where the app creates projects, ports Horizon `.hmod` mods, and clones
 * GitHub repositories — and returns every project with rich metadata.
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
        File(dir, "horizon-mod.toml").takeIf { it.isFile }?.let { f ->
            val parsed = HorizonModValidator.parseSimpleToml(runCatching { f.readText() }.getOrDefault(""))
            parsed.strings["id"]?.takeIf { it.isNotBlank() }?.let { id ->
                val api = parsed.strings["api"]?.ifBlank { "0.2" } ?: "0.2"
                return "horizon:$id (API $api)"
            }
        }
        File(dir, "horizon-mixin.toml").takeIf { it.isFile }?.let { f ->
            val parsed = HorizonModValidator.parseSimpleToml(runCatching { f.readText() }.getOrDefault(""))
            parsed.strings["id"]?.takeIf { it.isNotBlank() }?.let { id ->
                return "mixin:$id"
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
 * Full-screen Material You **Projects Fragment** for the bottom navigation `Projects` tab.
 *
 * Includes:
 * - Quick actions bar (Clone Git Repository, Open Folder, Port Java/Fabric Mod to `.hmod`)
 * - Live workspace project cards with active indicator
 * - Material 3 `ExtendedFloatingActionButton` (`+ New Project`) + secondary `SmallFloatingActionButton` (`Port Mod`)
 */
@Composable
fun ProjectsFragment(
    activeProjectPath: String?,
    onOpenProject: (File) -> Unit,
    onCreateProject: (ProjectTemplate) -> Unit,
    onCloneGitRepo: () -> Unit,
    onOpenFolderSheet: () -> Unit,
    onOpenHorizonPorter: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var loading by remember { mutableStateOf(true) }
    var refreshTrigger by remember { mutableIntStateOf(0) }
    val projects = remember { mutableStateListOf<ProjectsBrowser.ProjectInfo>() }

    LaunchedEffect(refreshTrigger, activeProjectPath) {
        loading = true
        val result = ProjectsBrowser.scan()
        projects.clear()
        projects.addAll(result)
        loading = false
    }

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier =
                Modifier.fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 14.dp)
                    .padding(bottom = 88.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // Header Banner
            ElevatedCard(
                shape = RoundedCornerShape(24.dp),
                colors =
                    CardDefaults.elevatedCardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    ),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.fillMaxWidth().padding(20.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(46.dp),
                        ) {
                            Icon(
                                painter = painterResource(drawables.folder_code),
                                contentDescription = null,
                                modifier = Modifier.padding(11.dp),
                            )
                        }
                        Spacer(Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Projects & Repositories",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                            )
                            val plural = if (projects.size == 1) "" else "s"
                            Text(
                                text =
                                    if (loading) "Scanning workspace…"
                                    else "${projects.size} project$plural in Arse IDE sandbox",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f),
                            )
                        }
                        IconButton(onClick = { refreshTrigger++ }) {
                            Icon(painterResource(drawables.refresh), contentDescription = "Refresh projects")
                        }
                    }

                    Spacer(Modifier.height(14.dp))

                    // Quick Action Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        FilledTonalButton(
                            onClick = onCloneGitRepo,
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                        ) {
                            Icon(painterResource(drawables.git), contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Clone Git", maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        FilledTonalButton(
                            onClick = onOpenHorizonPorter,
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                        ) {
                            Icon(painterResource(drawables.extension), contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Port to .hmod", maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        OutlinedButton(
                            onClick = onOpenFolderSheet,
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                        ) {
                            Icon(painterResource(drawables.outline_folder), contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Open Dir", maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }

            when {
                loading ->
                    Box(modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(modifier = Modifier.size(32.dp), strokeWidth = 3.dp)
                    }
                projects.isEmpty() ->
                    OutlinedCard(
                        shape = RoundedCornerShape(22.dp),
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
                                modifier = Modifier.size(44.dp),
                            )
                            Spacer(Modifier.height(12.dp))
                            Text(
                                text = "No projects yet",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = "Tap the + New Project FAB below to create an Android app, Horizon .hmod mod, or Fabric/Java mod.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                else ->
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        projects.forEach { project ->
                            val isActive = activeProjectPath == project.dir.absolutePath
                            ProjectCard(
                                project = project,
                                isActive = isActive,
                                onClick = { onOpenProject(project.dir) },
                            )
                        }
                    }
            }
        }

        // Material 3 FAB Stack in bottom-right of the Projects tab
        Column(
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            SmallFloatingActionButton(
                onClick = { onCreateProject(ProjectTemplate.HORIZON_CONTENT_MOD) },
                containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        painter = painterResource(drawables.extension),
                        contentDescription = "New Horizon Mod",
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text("New .hmod", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                }
            }

            ExtendedFloatingActionButton(
                onClick = { onCreateProject(ProjectTemplate.ANDROID_COMPOSE) },
                icon = { Icon(Icons.Outlined.Add, contentDescription = null) },
                text = { Text("New Project", fontWeight = FontWeight.SemiBold) },
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
    }
}

/**
 * Bottom sheet listing every project/repository under the sandbox home.
 */
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
                        text = "Projects & Repositories",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    val pluralSuffix = if (projects.size == 1) "" else "s"
                    Text(
                        text =
                            if (loading) "Scanning your workspace…"
                            else "${projects.size} project$pluralSuffix in your workspace",
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
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                painter = painterResource(drawables.folder),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(44.dp),
                            )
                            Spacer(Modifier.height(12.dp))
                            Text(
                                "No projects yet — create one or clone a repository to get started.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
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
    OutlinedCard(
        onClick = onClick,
        shape = RoundedCornerShape(18.dp),
        colors =
            CardDefaults.outlinedCardColors(
                containerColor =
                    if (isActive) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                    else MaterialTheme.colorScheme.surfaceContainerLow,
            ),
        modifier = Modifier.fillMaxWidth(),
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
            MetaLine(icon = drawables.edit, label = "Last edited", value = formatRelative(project.lastEdited))
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
            modifier = Modifier.width(84.dp),
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
