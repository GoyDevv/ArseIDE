package com.goydevv.arseide.filetree

import android.os.Environment
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewModelScope
import com.goydevv.arseide.activities.main.MainActivity
import com.goydevv.arseide.activities.main.searchViewModel
import com.goydevv.arseide.drawer.DrawerViewModel
import com.goydevv.arseide.file.FileObject
import com.goydevv.arseide.icons.XedIcon
import com.goydevv.arseide.resources.getString
import com.goydevv.arseide.resources.strings
import com.goydevv.arseide.settings.Settings
import kotlin.math.min
import kotlinx.coroutines.launch

enum class SortMode(val stringRes: Int) {
    SORT_BY_NAME(strings.sort_by_name),
    SORT_BY_SIZE(strings.sort_by_size),
    SORT_BY_DATE(strings.sort_by_date),
}

@Composable
fun FileTree(
    rootNode: FileTreeNode,
    modifier: Modifier = Modifier,
    onFileClick: FileTreeNode.(FileTreeNode) -> Unit,
    onSearchClick: () -> Unit = {},
    viewModel: FileTreeViewModel,
    drawerViewModel: DrawerViewModel,
) {
    // Auto-expand root node on first composition
    LaunchedEffect(rootNode.file) {
        if (!viewModel.isNodeExpanded(rootNode.file, rootNode.file)) {
            viewModel.toggleNodeExpansion(rootNode.file, rootNode.file)
            viewModel.loadChildrenForNode(rootNode)
        }
    }

    LaunchedEffect(MainActivity.instance) {
        MainActivity.instance?.foregroundListener["fileTreeRefresh"] = { resumed ->
            if (resumed) {
                viewModel.refreshEverything()
            }
        }
    }

    LaunchedEffect(viewModel.sortMode) { viewModel.refreshEverything() }

    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (viewModel.isAnyFileSelected(rootNode.file)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { viewModel.unselectAllFiles(rootNode.file) }) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(strings.go_prev))
                    }

                    Text(
                        text = "${viewModel.getSelectionCount(rootNode.file)} selected",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            } else {
                Text(
                    text = rootNode.name,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f).padding(start = 4.dp),
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.End,
                modifier = if (viewModel.isAnyFileSelected(rootNode.file)) Modifier.weight(1f) else Modifier,
            ) {
                if (viewModel.isAnyFileSelected(rootNode.file)) {
                    SelectionActions(viewModel, drawerViewModel, rootNode)
                } else {
                    FileTreeActions(viewModel, onSearchClick)
                }
            }
        }

        Box(modifier = Modifier.fillMaxWidth().height(3.dp)) {
            if (viewModel.isFileOperationInProgress() || searchViewModel.get()?.isIndexing(rootNode.file) == true) {
                LinearProgressIndicator(modifier = Modifier.fillMaxSize())
            } else {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
            }
        }

        Column(
            modifier =
                Modifier.fillMaxSize()
                    .horizontalScroll(rememberScrollState())
                    .verticalScroll(rememberScrollState())
                    .padding(top = 6.dp, bottom = 88.dp)
        ) {
            key(rootNode.file.hashCode(), rootNode.name) {
                FileTreeNodeItem(
                    modifier = Modifier.fillMaxWidth(),
                    root = rootNode.file,
                    node = rootNode,
                    depth = 0,
                    onFileClick = { rootNode.onFileClick(it) },
                    viewModel = viewModel,
                )
            }
        }
    }
}

@Composable
private fun SelectionActions(viewModel: FileTreeViewModel, drawerViewModel: DrawerViewModel, rootNode: FileTreeNode) {
    val selectedFiles = viewModel.getSelectedFiles(rootNode.file)
    val actions = remember(selectedFiles, rootNode.file) { FileActionProvider.getActions(selectedFiles, rootNode.file) }
    var expanded by remember { mutableStateOf(false) }

    val context = LocalContext.current

    BoxWithConstraints {
        val itemWidth = 64.dp
        val availableWidth = maxWidth - 48.dp
        val maxVisibleCount = (availableWidth / itemWidth).toInt().coerceAtLeast(0)

        var actualVisibleCount = min(actions.size, maxVisibleCount)

        if (actions.size - actualVisibleCount == 1) {
            actualVisibleCount += 1
        }

        val toolbarActions = actions.sortedByDescending { it.importance }.take(actualVisibleCount)
        val dropdownActions = actions - toolbarActions.toSet()

        Row(verticalAlignment = Alignment.CenterVertically) {
            toolbarActions.forEach { action ->
                when (action) {
                    is FileAction -> {
                        val file = selectedFiles.first()
                        IconButton(
                            enabled = action.isEnabled(file),
                            onClick = {
                                val actionContext =
                                    FileActionContext(file, rootNode.file, viewModel, drawerViewModel, context)
                                action.action(actionContext)

                                viewModel.unselectAllFiles(rootNode.file)
                            },
                        ) {
                            XedIcon(action.icon, contentDescription = action.title)
                        }
                    }
                    is MultiFileAction -> {
                        IconButton(
                            enabled = action.isEnabled(selectedFiles),
                            onClick = {
                                val actionContext =
                                    MultiFileActionContext(
                                        selectedFiles,
                                        rootNode.file,
                                        viewModel,
                                        drawerViewModel,
                                        context,
                                    )
                                action.action(actionContext)

                                viewModel.unselectAllFiles(rootNode.file)
                            },
                        ) {
                            XedIcon(action.icon, contentDescription = action.title)
                        }
                    }
                }
            }

            if (dropdownActions.isNotEmpty()) {
                Box(modifier = Modifier.wrapContentSize(Alignment.TopEnd)) {
                    IconButton(onClick = { expanded = true }, modifier = Modifier.size(48.dp)) {
                        Icon(Icons.Outlined.MoreVert, stringResource(strings.more))
                    }

                    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        dropdownActions.forEach { action ->
                            when (action) {
                                is FileAction -> {
                                    val file = selectedFiles.first()
                                    DropdownMenuItem(
                                        text = { Text(action.title) },
                                        leadingIcon = { XedIcon(action.icon, contentDescription = action.title) },
                                        enabled = action.isEnabled(file),
                                        onClick = {
                                            val actionContext =
                                                FileActionContext(
                                                    file,
                                                    rootNode.file,
                                                    viewModel,
                                                    drawerViewModel,
                                                    context,
                                                )
                                            action.action(actionContext)

                                            viewModel.unselectAllFiles(rootNode.file)
                                            expanded = false
                                        },
                                    )
                                }
                                is MultiFileAction -> {
                                    DropdownMenuItem(
                                        text = { Text(action.title) },
                                        leadingIcon = { XedIcon(action.icon, contentDescription = action.title) },
                                        enabled = action.isEnabled(selectedFiles),
                                        onClick = {
                                            val actionContext =
                                                MultiFileActionContext(
                                                    selectedFiles,
                                                    rootNode.file,
                                                    viewModel,
                                                    drawerViewModel,
                                                    context,
                                                )
                                            action.action(actionContext)

                                            viewModel.unselectAllFiles(rootNode.file)
                                            expanded = false
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
}

@Composable
private fun FileTreeActions(viewModel: FileTreeViewModel, onSearchClick: () -> Unit) {
    var showOptionsMenu by remember { mutableStateOf(false) }

    IconButton(onClick = { viewModel.viewModelScope.launch { viewModel.refreshEverything() } }) {
        Icon(Icons.Outlined.Refresh, stringResource(strings.refresh))
    }

    IconButton(onClick = onSearchClick) { Icon(Icons.Outlined.Search, stringResource(strings.search)) }

    Box {
        IconButton(onClick = { showOptionsMenu = true }) { Icon(Icons.Outlined.MoreVert, stringResource(strings.more)) }

        DropdownMenu(expanded = showOptionsMenu, onDismissRequest = { showOptionsMenu = false }) {
            DropdownMenuItem(
                text = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = Settings.show_hidden_files_drawer, onCheckedChange = null)
                        Spacer(Modifier.width(12.dp))
                        Text(stringResource(strings.show_hidden_files))
                        Spacer(Modifier.width(8.dp))
                    }
                },
                onClick = {
                    Settings.show_hidden_files_drawer = !Settings.show_hidden_files_drawer
                    showOptionsMenu = false
                },
            )

            DropdownMenuItem(
                text = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = Settings.compact_folders_drawer, onCheckedChange = null)
                        Spacer(Modifier.width(12.dp))
                        Text(stringResource(strings.compact_folders))
                        Spacer(Modifier.width(8.dp))
                    }
                },
                onClick = {
                    Settings.compact_folders_drawer = !Settings.compact_folders_drawer
                    showOptionsMenu = false
                },
            )

            DropdownMenuItem(
                text = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = viewModel.sortMode == SortMode.SORT_BY_NAME, onClick = null)
                        Spacer(Modifier.width(12.dp))
                        Text(stringResource(strings.sort_by_name))
                        Spacer(Modifier.width(8.dp))
                    }
                },
                onClick = {
                    viewModel.sortMode = SortMode.SORT_BY_NAME
                    Settings.sort_mode = viewModel.sortMode.ordinal
                    showOptionsMenu = false
                },
            )

            DropdownMenuItem(
                text = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = viewModel.sortMode == SortMode.SORT_BY_SIZE, onClick = null)
                        Spacer(Modifier.width(12.dp))
                        Text(stringResource(strings.sort_by_size))
                        Spacer(Modifier.width(8.dp))
                    }
                },
                onClick = {
                    viewModel.sortMode = SortMode.SORT_BY_SIZE
                    Settings.sort_mode = viewModel.sortMode.ordinal
                    showOptionsMenu = false
                },
            )

            DropdownMenuItem(
                text = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = viewModel.sortMode == SortMode.SORT_BY_DATE, onClick = null)
                        Spacer(Modifier.width(12.dp))
                        Text(stringResource(strings.sort_by_date))
                        Spacer(Modifier.width(8.dp))
                    }
                },
                onClick = {
                    viewModel.sortMode = SortMode.SORT_BY_DATE
                    Settings.sort_mode = viewModel.sortMode.ordinal
                    showOptionsMenu = false
                },
            )
        }
    }
}

fun FileObject.getAppropriateName(): String {
    return if (getAbsolutePath() == Environment.getExternalStorageDirectory().absolutePath) {
        strings.storage.getString()
    } else {
        getName()
    }
}
