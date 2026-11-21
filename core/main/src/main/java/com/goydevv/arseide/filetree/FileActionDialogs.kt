package com.goydevv.arseide.filetree

import android.content.Context
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.goydevv.arseide.activities.main.MainActivity
import com.goydevv.arseide.activities.main.drawerStateRef
import com.goydevv.arseide.components.PropertiesDialog
import com.goydevv.arseide.components.SingleInputDialog
import com.goydevv.arseide.drawer.DrawerViewModel
import com.goydevv.arseide.file.FileObject
import com.goydevv.arseide.file.FileOperations
import com.goydevv.arseide.file.FileWrapper
import com.goydevv.arseide.resources.drawables
import com.goydevv.arseide.resources.fillPlaceholders
import com.goydevv.arseide.resources.getFilledString
import com.goydevv.arseide.resources.getString
import com.goydevv.arseide.resources.strings
import com.goydevv.arseide.settings.Settings
import com.goydevv.arseide.tabs.editor.EditorTab
import com.goydevv.arseide.utils.errorDialog
import com.goydevv.arseide.utils.toast
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun FileActionDialogs(
    drawerViewModel: DrawerViewModel,
    viewModel: FileTreeViewModel,
    scope: CoroutineScope,
    context: Context,
) {
    if (viewModel.showRenameDialog) {
        val file = viewModel.renameFile ?: return
        SingleInputDialog(
            title = if (file.isFile()) stringResource(strings.rename_file) else stringResource(strings.rename_folder),
            inputLabel = stringResource(id = strings.new_name),
            inputValue = viewModel.renameValue,
            errorMessage = viewModel.renameError,
            confirmEnabled = viewModel.renameValue.isNotBlank() && viewModel.renameValue != file.getName(),
            confirmText = stringResource(strings.rename),
            onInputValueChange = {
                viewModel.renameValue = it
                viewModel.renameError = null

                if (it.contains(Regex("""[\p{Cntrl}/\\<>:"|?*]"""))) {
                    viewModel.renameError = context.getString(strings.invalid_characters)
                }
            },
            onConfirm = {
                val newName = viewModel.renameValue
                scope.launch {
                    val oldPath = file.getAbsolutePath()
                    val tabsToRename =
                        MainActivity.instance?.viewModel?.tabs?.filterIsInstance<EditorTab>()?.filter {
                            it.file.getAbsolutePath() == oldPath
                        } ?: emptyList()

                    val success = file.renameTo(newName)
                    if (!success) {
                        toast(strings.rename_failed)
                        return@launch
                    }

                    val parentFile = file.getParentFile() ?: return@launch
                    viewModel.updateCache(parentFile)

                    tabsToRename.forEach {
                        it.tabTitle.value = newName
                        it.file = parentFile.getChildForName(newName)
                    }
                }
            },
            onFinish = { viewModel.closeRenameDialog() },
        )
    }

    if (viewModel.showDeleteConfirmation) {
        val files = viewModel.deleteFiles ?: return
        val root = viewModel.deleteRoot
        DeleteConfirmationDialog(
            files = files,
            projectRoot = root,
            onConfirm = {
                scope.launch {
                    for (file in files) {
                        viewModel.withFileOperation {
                            FileOperations.deleteFile(file)
                                .onFailure { toast(it.message ?: strings.delete_failed.getString()) }
                                .onSuccess {
                                    val parentFile = file.getParentFile()
                                    if (parentFile != null) {
                                        viewModel.updateCache(file.getParentFile()!!)
                                    }

                                    if (file == root) {
                                        drawerViewModel.removeFileTreeTab(file, true)
                                    }

                                    MainActivity.instance?.viewModel?.also { vm ->
                                        vm.tabs.forEachIndexed { index, tab ->
                                            if (tab.file == file) {
                                                vm.tabManager.removeTab(index)
                                            }
                                        }
                                    }
                                }
                        }
                    }
                }
                viewModel.closeDeleteConfirmation()
            },
            onDismiss = { viewModel.closeDeleteConfirmation() },
        )
    }

    if (viewModel.showPropertiesDialog) {
        val file = viewModel.propertyFile ?: return
        PropertiesDialog(file = file, onDismiss = { viewModel.closePropertiesDialog() })
    }

    if (viewModel.showCreateDialog) {
        val file = viewModel.createParentFile ?: return
        val root = viewModel.createRoot
        SingleInputDialog(
            title =
                if (viewModel.isCreateFile) stringResource(strings.new_file) else stringResource(strings.new_folder),
            inputLabel =
                if (viewModel.isCreateFile) stringResource(id = strings.file_name)
                else stringResource(id = strings.folder_name),
            inputValue = viewModel.createValue,
            errorMessage = viewModel.createError,
            confirmEnabled = viewModel.createValue.isNotBlank(),
            confirmText = stringResource(strings.create),
            onInputValueChange = {
                viewModel.createValue = it
                viewModel.createError = null

                if (
                    viewModel.isCreateFile && it.contains(Regex("""[\p{Cntrl}/\\<>:"|?*]""")) ||
                        !viewModel.isCreateFile && it.contains(Regex("""[\p{Cntrl}\\<>:"|?*]|^/"""))
                ) {
                    viewModel.createError = context.getString(strings.invalid_characters)
                }
            },
            onConfirm = {
                scope.launch {
                    runCatching {
                            if (!file.canWrite()) {
                                toast(strings.permission_denied)
                                return@launch
                            }
                            if (!file.hasChild(viewModel.createValue)) {
                                val newChild = file.createChild(viewModel.isCreateFile, viewModel.createValue)

                                if (newChild == null) {
                                    if (viewModel.isCreateFile) {
                                        toast(strings.file_creation_failed)
                                    } else {
                                        toast(strings.folder_creation_failed)
                                    }
                                }

                                if (viewModel.isCreateFile && newChild != null && Settings.auto_open_new_files) {
                                    MainActivity.instance
                                        ?.viewModel
                                        ?.editorManager
                                        ?.openFile(
                                            newChild,
                                            projectRoot = root,
                                            checkDuplicate = true,
                                            switchToTab = true,
                                        )
                                    drawerStateRef.get()?.close()
                                }
                            } else {
                                val msg =
                                    if (viewModel.isCreateFile) strings.file_already_exists
                                    else strings.folder_already_exists
                                toast(msg.getFilledString(viewModel.createValue))
                            }

                            viewModel.updateCache(file)
                            viewModel.createValue = ""
                        }
                        .onFailure { errorDialog(throwable = it) }
                }
            },
            onFinish = { viewModel.closeCreateDialog() },
        )
    }

    if (viewModel.showCloseProjectConfirmation) {
        val root = viewModel.projectConfirmationRoot ?: return
        ProjectCloseConfirmationDialog(
            projectName = root.getAppropriateName(),
            onConfirm = {
                drawerViewModel.removeFileTreeTab(root)
                viewModel.closeCloseProjectConfirmation()
            },
            onDismiss = { viewModel.closeCloseProjectConfirmation() },
        )
    }
}

@Composable
fun ProjectCloseConfirmationDialog(projectName: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(28.dp),
        title = { Text(stringResource(strings.close), fontWeight = FontWeight.Bold) },
        text = { Column { Text(text = stringResource(strings.close_current_project).fillPlaceholders(projectName)) } },
        confirmButton = { TextButton(onClick = onConfirm) { Text(stringResource(strings.close)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(strings.cancel)) } },
    )
}

private enum class DeletionRiskLevel {
    STANDARD,
    HIGH,
    CRITICAL,
}

private data class DeletionImpact(
    val fileCount: Int,
    val folderCount: Int,
    val totalBytes: Long,
    val capped: Boolean,
    val riskLevel: DeletionRiskLevel,
    val riskReasons: List<String>,
)

private val CRITICAL_NAMES =
    setOf(
        ".git",
        "src",
        "app",
        "core",
        "build.gradle",
        "build.gradle.kts",
        "settings.gradle",
        "settings.gradle.kts",
        "AndroidManifest.xml",
        "Cargo.toml",
        "package.json",
        "gradle",
        "res",
    )

private suspend fun analyzeDeletionImpact(files: List<FileObject>, projectRoot: FileObject?): DeletionImpact =
    withContext(Dispatchers.IO) {
        var fileCount = 0
        var folderCount = 0
        var totalBytes = 0L
        var capped = false
        val reasons = LinkedHashSet<String>()
        val maxEntries = 2000

        val rootPath = projectRoot?.getAbsolutePath()

        for (target in files) {
            val name = target.getName()
            val absPath = target.getAbsolutePath()
            if (rootPath != null && absPath == rootPath) {
                reasons.add("Deletes the entire project root directory")
            }
            if (CRITICAL_NAMES.contains(name)) {
                reasons.add("Contains critical project path \"$name\"")
            }

            if (target is FileWrapper) {
                val javaFile = File(absPath)
                if (javaFile.isDirectory) {
                    folderCount++
                    var scanned = 0
                    for (entry in javaFile.walkTopDown()) {
                        if (entry == javaFile) continue
                        scanned++
                        if (scanned > maxEntries) {
                            capped = true
                            break
                        }
                        if (entry.isDirectory) {
                            folderCount++
                            if (entry.name == ".git") {
                                reasons.add("Includes Git repository history (.git)")
                            }
                        } else {
                            fileCount++
                            totalBytes += entry.length()
                        }
                    }
                } else {
                    fileCount++
                    totalBytes += javaFile.length()
                }
            } else {
                if (target.isDirectory()) {
                    folderCount++
                    runCatching {
                        val children = target.listFiles()
                        fileCount += children.count { it.isFile() }
                        folderCount += children.count { it.isDirectory() }
                        children.forEach { totalBytes += it.length() }
                    }
                } else {
                    fileCount++
                    totalBytes += target.length()
                }
            }
        }

        if (fileCount >= 15 || capped) {
            reasons.add("Permanently removes ${if (capped) "$maxEntries+" else fileCount} files")
        }
        if (totalBytes >= 5L * 1024L * 1024L) {
            reasons.add("Large deletion (${formatDeletionSize(totalBytes)})")
        }

        val riskLevel =
            when {
                reasons.any { it.contains("project root") || it.contains("Git repository") } ||
                    fileCount >= 50 ||
                    capped ||
                    totalBytes >= 25L * 1024L * 1024L -> DeletionRiskLevel.CRITICAL
                reasons.isNotEmpty() || folderCount > 0 || files.size > 3 -> DeletionRiskLevel.HIGH
                else -> DeletionRiskLevel.STANDARD
            }

        DeletionImpact(
            fileCount = fileCount,
            folderCount = folderCount,
            totalBytes = totalBytes,
            capped = capped,
            riskLevel = riskLevel,
            riskReasons = reasons.toList(),
        )
    }

private fun formatDeletionSize(bytes: Long): String {
    if (bytes < 1024L) return "$bytes B"
    val kb = bytes / 1024.0
    if (kb < 1024.0) return String.format("%.1f KB", kb)
    val mb = kb / 1024.0
    if (mb < 1024.0) return String.format("%.1f MB", mb)
    return String.format("%.2f GB", mb / 1024.0)
}

@Composable
fun DeleteConfirmationDialog(
    files: List<FileObject>,
    projectRoot: FileObject? = null,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    var impact by remember(files) { mutableStateOf<DeletionImpact?>(null) }
    var acknowledgedRisk by remember(files) { mutableStateOf(false) }

    LaunchedEffect(files, projectRoot) {
        impact = analyzeDeletionImpact(files, projectRoot)
    }

    val currentImpact = impact
    val requiresAck =
        currentImpact != null &&
            (currentImpact.riskLevel == DeletionRiskLevel.CRITICAL ||
                (currentImpact.riskLevel == DeletionRiskLevel.HIGH && currentImpact.riskReasons.isNotEmpty()))

    val canDelete = currentImpact != null && (!requiresAck || acknowledgedRisk)

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(28.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        icon = {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color =
                    when (currentImpact?.riskLevel) {
                        DeletionRiskLevel.CRITICAL -> MaterialTheme.colorScheme.error
                        DeletionRiskLevel.HIGH -> MaterialTheme.colorScheme.errorContainer
                        else -> MaterialTheme.colorScheme.secondaryContainer
                    },
                contentColor =
                    when (currentImpact?.riskLevel) {
                        DeletionRiskLevel.CRITICAL -> MaterialTheme.colorScheme.onError
                        DeletionRiskLevel.HIGH -> MaterialTheme.colorScheme.onErrorContainer
                        else -> MaterialTheme.colorScheme.onSecondaryContainer
                    },
                modifier = Modifier.size(48.dp),
            ) {
                Icon(
                    painter = painterResource(drawables.error),
                    contentDescription = null,
                    modifier = Modifier.padding(12.dp),
                )
            }
        },
        title = {
            Text(
                text =
                    when (currentImpact?.riskLevel) {
                        DeletionRiskLevel.CRITICAL -> "Massive Permanent Deletion"
                        DeletionRiskLevel.HIGH -> "Confirm Deletion Risk"
                        else -> stringResource(strings.delete)
                    },
                fontWeight = FontWeight.Bold,
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().animateContentSize(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                val basePrompt =
                    if (files.size == 1) {
                        stringResource(strings.ask_deletion_one).fillPlaceholders(files.first().getName())
                    } else {
                        stringResource(strings.ask_deletion_many).fillPlaceholders(files.size)
                    }
                Text(text = basePrompt, style = MaterialTheme.typography.bodyMedium)

                if (currentImpact == null) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.padding(vertical = 4.dp),
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        Text(
                            text = "Scanning files and calculating deletion impact…",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHighest,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Column {
                                Text(
                                    text = if (currentImpact.capped) "2000+" else currentImpact.fileCount.toString(),
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                )
                                Text(
                                    text = if (currentImpact.fileCount == 1) "File" else "Files",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Column {
                                Text(
                                    text = currentImpact.folderCount.toString(),
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                )
                                Text(
                                    text = if (currentImpact.folderCount == 1) "Folder" else "Folders",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text =
                                        (if (currentImpact.capped) ">" else "") +
                                            formatDeletionSize(currentImpact.totalBytes),
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color =
                                        if (currentImpact.riskLevel != DeletionRiskLevel.STANDARD) {
                                            MaterialTheme.colorScheme.error
                                        } else {
                                            MaterialTheme.colorScheme.onSurface
                                        },
                                )
                                Text(
                                    text = "Total Size",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }

                    if (currentImpact.riskReasons.isNotEmpty()) {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column(
                                modifier = Modifier.fillMaxWidth().padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                Text(
                                    text = "Warning — Cannot be undone",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold,
                                )
                                currentImpact.riskReasons.forEach { reason ->
                                    Text(
                                        text = "• $reason",
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                }
                            }
                        }
                    }

                    if (files.size > 1) {
                        val previewNames = files.take(3).joinToString(", ") { it.getName() }
                        val extra = if (files.size > 3) " +${files.size - 3} more" else ""
                        Text(
                            text = "Targets: $previewNames$extra",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }

                    if (requiresAck) {
                        Row(
                            modifier =
                                Modifier.fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable { acknowledgedRisk = !acknowledgedRisk }
                                    .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(
                                checked = acknowledgedRisk,
                                onCheckedChange = { acknowledgedRisk = it },
                                colors =
                                    CheckboxDefaults.colors(
                                        checkedColor = MaterialTheme.colorScheme.error,
                                        checkmarkColor = MaterialTheme.colorScheme.onError,
                                    ),
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = "I understand these files will be permanently deleted",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Medium,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                enabled = canDelete,
                shape = RoundedCornerShape(14.dp),
                colors =
                    ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError,
                    ),
            ) {
                Text(
                    text =
                        if (currentImpact?.riskLevel == DeletionRiskLevel.CRITICAL) "Delete Permanently"
                        else stringResource(strings.delete),
                    fontWeight = FontWeight.SemiBold,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(strings.cancel))
            }
        },
    )
}
