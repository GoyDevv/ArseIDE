package com.goydevv.arseide.components

import android.content.Intent
import android.content.pm.PackageManager
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.goydevv.arseide.DefaultScope
import com.goydevv.arseide.activities.main.MainActivity
import com.goydevv.arseide.activities.main.MainViewModel
import com.goydevv.arseide.activities.main.drawerStateRef
import com.goydevv.arseide.activities.main.fileTreeViewModel
import com.goydevv.arseide.activities.main.searchViewModel
import com.goydevv.arseide.drawer.DrawerViewModel
import com.goydevv.arseide.file.FileObject
import com.goydevv.arseide.file.FileWrapper
import com.goydevv.arseide.file.child
import com.goydevv.arseide.file.createFileIfNot
import com.goydevv.arseide.file.toFileObject
import com.goydevv.arseide.filetree.FileTreeTab
import com.goydevv.arseide.icons.CreateNewFile
import com.goydevv.arseide.icons.Icon
import com.goydevv.arseide.icons.XedIcon
import com.goydevv.arseide.icons.XedIcons
import com.goydevv.arseide.projects.DetectedProjectType
import com.goydevv.arseide.resources.drawables
import com.goydevv.arseide.resources.strings
import com.goydevv.arseide.runner.ProjectRunner
import com.goydevv.arseide.runner.RunOutputState
import com.goydevv.arseide.search.CodeSearchDialog
import com.goydevv.arseide.search.FileSearchDialog
import com.goydevv.arseide.utils.application
import com.goydevv.arseide.utils.errorDialog
import com.goydevv.arseide.utils.getTempDir
import com.goydevv.arseide.utils.toast
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

var addDialog by mutableStateOf(false)
var fileSearchDialog by mutableStateOf(false)
var codeSearchDialog by mutableStateOf(false)

/**
 * Clean, non-glitching Material 3 Global Toolbar Actions:
 * Always keeps the primary Run / Stop button (and Android Gradle Sync when applicable) accessible
 * without overflowing or colliding with editor tab actions.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GlobalToolbarActions(viewModel: MainViewModel, drawerViewModel: DrawerViewModel) {
    val activity = LocalActivity.current
    val scope = rememberCoroutineScope()
    var tempFileNameDialog by remember { mutableStateOf(false) }
    var rootFileDialog by remember { mutableStateOf(false) }
    var rootFilePath by remember { mutableStateOf("") }

    val projectRoot: FileObject? = (drawerViewModel.currentDrawerTab as? FileTreeTab)?.root
    val activeFile: FileObject? = viewModel.currentTab?.file ?: projectRoot

    Row(verticalAlignment = Alignment.CenterVertically) {
        if (projectRoot is FileWrapper) {
            val rootPathStr = projectRoot.getAbsolutePath()
            val detectedType = ProjectRunner.detect(rootPathStr)

            // Sync button for Android projects
            if (!RunOutputState.isRunning && detectedType == DetectedProjectType.ANDROID) {
                IconButton(
                    onClick = {
                        activity?.let { act ->
                            DefaultScope.launch { ProjectRunner.sync(act, projectRoot, activeFile ?: projectRoot) }
                        }
                    },
                    modifier = Modifier.size(40.dp),
                ) {
                    XedIcon(Icon.ResourceIcon(drawables.refresh), contentDescription = "Gradle Sync")
                }
            }

            // Primary Run / Build / Package .hmod button
            if (RunOutputState.isRunning || ProjectRunner.canRun(rootPathStr)) {
                FilledTonalIconButton(
                    onClick = {
                        if (RunOutputState.isRunning) {
                            RunOutputState.stop()
                        } else {
                            activity?.let { act ->
                                DefaultScope.launch { ProjectRunner.run(act, projectRoot, activeFile ?: projectRoot) }
                            }
                        }
                    },
                    colors =
                        IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor =
                                if (RunOutputState.isRunning) MaterialTheme.colorScheme.errorContainer
                                else MaterialTheme.colorScheme.primaryContainer,
                            contentColor =
                                if (RunOutputState.isRunning) MaterialTheme.colorScheme.onErrorContainer
                                else MaterialTheme.colorScheme.onPrimaryContainer,
                        ),
                    modifier = Modifier.padding(horizontal = 4.dp).size(38.dp),
                ) {
                    XedIcon(
                        if (RunOutputState.isRunning) Icon.ResourceIcon(drawables.stop)
                        else Icon.ResourceIcon(drawables.run),
                        contentDescription = if (RunOutputState.isRunning) "Stop" else "Run / Build",
                    )
                }
            }

            // When no editor tabs are open, also show quick Toolchain & Terminal icons
            if (viewModel.tabs.isEmpty()) {
                IconButton(
                    onClick = { com.goydevv.arseide.projects.IdeConfigState.open(File(rootPathStr)) },
                    modifier = Modifier.size(40.dp),
                ) {
                    XedIcon(Icon.ResourceIcon(drawables.build), contentDescription = "IDE Configuration")
                }
                IconButton(
                    onClick = { com.goydevv.arseide.projects.DependencyManagerState.open(File(rootPathStr)) },
                    modifier = Modifier.size(40.dp),
                ) {
                    XedIcon(Icon.ResourceIcon(drawables.download), contentDescription = "Dependencies")
                }
            }
        }
    }

    if (fileSearchDialog && drawerViewModel.currentDrawerTab is FileTreeTab) {
        FileSearchDialog(
            mainViewModel = viewModel,
            searchViewModel = searchViewModel.get()!!,
            projectFile = (drawerViewModel.currentDrawerTab as FileTreeTab).root,
            onFinish = { fileSearchDialog = false },
            onSelect = { projectFile, fileObject ->
                scope.launch {
                    if (fileObject.isFile()) {
                        viewModel.editorManager.openFile(
                            fileObject = fileObject,
                            projectRoot = projectFile,
                            checkDuplicate = true,
                            switchToTab = true,
                        )
                        drawerStateRef.get()?.close()
                    } else {
                        fileTreeViewModel.get()?.goToFolder(projectFile, fileObject)
                        drawerStateRef.get()!!.open()
                    }
                }
            },
        )
    }

    if (codeSearchDialog && drawerViewModel.currentDrawerTab is FileTreeTab) {
        CodeSearchDialog(
            mainViewModel = viewModel,
            searchViewModel = searchViewModel.get()!!,
            projectFile = (drawerViewModel.currentDrawerTab as FileTreeTab).root,
            onFinish = { codeSearchDialog = false },
        )
    }

    if (addDialog) {
        ModalBottomSheet(onDismissRequest = { addDialog = false }) {
            Column(modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp, top = 0.dp)) {
                AddDialogItem(resId = drawables.file, title = stringResource(strings.temp_file)) {
                    val intent = Intent(Intent.ACTION_CREATE_DOCUMENT)
                    intent.addCategory(Intent.CATEGORY_OPENABLE)
                    intent.setType("application/octet-stream")
                    intent.putExtra(Intent.EXTRA_TITLE, "newfile.txt")

                    val activities =
                        application!!.packageManager.queryIntentActivities(intent, PackageManager.MATCH_ALL)

                    if (activities.isEmpty()) {
                        errorDialog(strings.unsupported_feature)
                    } else {
                        tempFileNameDialog = true
                    }

                    addDialog = false
                }

                if ((drawerViewModel.currentDrawerTab as? FileTreeTab)?.root != null) {
                    AddDialogItem(icon = XedIcons.CreateNewFile, title = "New file in project root") {
                        addDialog = false
                        rootFilePath = ""
                        rootFileDialog = true
                    }
                }

                AddDialogItem(icon = XedIcons.CreateNewFile, title = stringResource(strings.new_file)) {
                    addDialog = false
                    val intent = Intent(Intent.ACTION_CREATE_DOCUMENT)
                    intent.addCategory(Intent.CATEGORY_OPENABLE)
                    intent.setType("application/octet-stream")
                    intent.putExtra(Intent.EXTRA_TITLE, "newfile.txt")

                    val activities =
                        application!!.packageManager.queryIntentActivities(intent, PackageManager.MATCH_ALL)
                    if (activities.isEmpty()) {
                        errorDialog(strings.unsupported_feature)
                    } else {
                        MainActivity.instance?.apply {
                            fileManager.createNewFile(mimeType = "*/*", title = "newfile.txt") {
                                if (it != null) {
                                    lifecycleScope.launch {
                                        viewModel.editorManager.openFile(
                                            it,
                                            projectRoot = null,
                                            checkDuplicate = true,
                                            switchToTab = true,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                AddDialogItem(resId = drawables.file_symlink, title = stringResource(strings.open_file)) {
                    addDialog = false
                    MainActivity.instance?.apply {
                        fileManager.requestOpenFile(mimeType = "*/*") {
                            if (it != null) {
                                lifecycleScope.launch {
                                    viewModel.editorManager.openFile(
                                        it.toFileObject(expectedIsFile = true),
                                        checkDuplicate = true,
                                        projectRoot = null,
                                        switchToTab = true,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (tempFileNameDialog) {
        var fileName by remember { mutableStateOf("untitled.txt") }
        SingleInputDialog(
            title = stringResource(strings.temp_file),
            inputValue = fileName,
            onInputValueChange = { fileName = it },
            onConfirm = {
                val requested = fileName.ifBlank { "untitled.txt" }
                tempFileNameDialog = false
                DefaultScope.launch(Dispatchers.IO) {
                    val tempDir = getTempDir().child("temp_editor")
                    val tempFile = FileWrapper(tempDir.child(uniqueTempName(tempDir, requested)))
                    tempFile.createFileIfNot()
                    viewModel.editorManager.openFile(tempFile, projectRoot = null, switchToTab = true)
                }
            },
            onDismiss = { tempFileNameDialog = false },
            singleLineMode = true,
            confirmText = stringResource(strings.ok),
            inputLabel = stringResource(strings.file_name),
        )
    }

    if (rootFileDialog) {
        val projectRootObj = (drawerViewModel.currentDrawerTab as? FileTreeTab)?.root
        SingleInputDialog(
            title = "New file in project root",
            inputValue = rootFilePath,
            onInputValueChange = { rootFilePath = it },
            onConfirm = {
                val rel = rootFilePath.trim().trimStart('/')
                rootFileDialog = false
                if (projectRootObj != null && rel.isNotBlank()) {
                    DefaultScope.launch(Dispatchers.IO) {
                        runCatching {
                            val base = File(projectRootObj.getAbsolutePath())
                            val target = File(base, rel)
                            target.parentFile?.mkdirs()
                            if (!target.exists()) target.createNewFile()
                            viewModel.editorManager.openFile(
                                FileWrapper(target),
                                projectRoot = projectRootObj,
                                switchToTab = true,
                            )
                        }.onFailure { toast(it.message ?: "Could not create file") }
                    }
                }
                rootFilePath = ""
            },
            onDismiss = {
                rootFileDialog = false
                rootFilePath = ""
            },
            singleLineMode = true,
            confirmText = stringResource(strings.ok),
            inputLabel = "Path, e.g. src/utils/File.kt — any /folders/ in the path are created for you",
        )
    }
}

private fun uniqueTempName(dir: File, base: String): String {
    if (!dir.child(base).exists()) return base
    val ext = base.substringAfterLast('.', "")
    val name = base.substringBeforeLast('.', base)
    var i = 1
    var candidate: String
    do {
        candidate = if (ext.isNotEmpty()) "$name$i.$ext" else "$name$i"
        i++
    } while (dir.child(candidate).exists())
    return candidate
}
