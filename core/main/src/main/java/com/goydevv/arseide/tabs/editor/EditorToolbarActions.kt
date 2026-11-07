package com.goydevv.arseide.tabs.editor

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.goydevv.arseide.activities.main.MainActivity
import com.goydevv.arseide.activities.main.MainViewModel
import com.goydevv.arseide.commands.ActionContext
import com.goydevv.arseide.commands.KeybindingsManager
import com.goydevv.arseide.commands.ToggleableCommand
import com.goydevv.arseide.commands.ToolbarConfiguration
import com.goydevv.arseide.filetree.FileTreeTab
import com.goydevv.arseide.icons.Icon
import com.goydevv.arseide.icons.XedIcon
import com.goydevv.arseide.resources.drawables
import com.goydevv.arseide.resources.strings
import com.goydevv.arseide.theme.Typography
import java.io.File
import kotlin.math.min

/**
 * Clean Material 3 EditorToolbarActions:
 * Displays at most 3 inline action buttons (Undo, Redo, Save) + overflow menu so icons never
 * crowd, clip, or glitch inside the TopAppBar.
 */
@Composable
fun EditorToolbarActions(modifier: Modifier = Modifier, viewModel: MainViewModel) {
    var expanded by remember { mutableStateOf(false) }
    val activity = LocalActivity.current
    val projectRootPath =
        (MainActivity.instance?.drawerViewModel?.currentDrawerTab as? FileTreeTab)?.root?.getAbsolutePath()

    val allActions by remember {
        derivedStateOf { ToolbarConfiguration.editorCommands }
    }

    val visibleActions = allActions.filter { it.isSupported() }
    val actualVisibleCount = min(visibleActions.size, 3)
    val toolbarActions = visibleActions.take(actualVisibleCount)
    val dropdownActions = visibleActions.drop(actualVisibleCount)

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        toolbarActions.forEach { command ->
            IconButton(
                onClick = { activity?.let { command.performCommand(ActionContext(it)) } },
                modifier = Modifier.size(40.dp),
                enabled = command.isEnabled(),
                colors =
                    IconButtonDefaults.iconButtonColors().let {
                        if (command is ToggleableCommand && command.isOn())
                            it.copy(
                                contentColor = MaterialTheme.colorScheme.primary,
                                disabledContentColor = MaterialTheme.colorScheme.primary,
                            )
                        else it
                    },
            ) {
                XedIcon(
                    icon = if (command.preferText) Icon.TextIcon(command.getLabel()) else command.getIcon(),
                    contentDescription = command.getLabel(),
                )
            }
        }

        Box(modifier = Modifier.wrapContentSize(Alignment.TopEnd)) {
            IconButton(onClick = { expanded = true }, modifier = Modifier.size(40.dp)) {
                Icon(Icons.Outlined.MoreVert, stringResource(strings.more))
            }

            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                dropdownActions.forEach { command ->
                    val keyCombination = KeybindingsManager.getKeyCombinationForCommand(command.id)
                    val displayKeyCombination = keyCombination?.getDisplayName()

                    DropdownMenuItem(
                        enabled = command.isEnabled(),
                        text = {
                            Text(
                                text = command.getLabel(),
                                color =
                                    if (command is ToggleableCommand && command.isOn()) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        Color.Unspecified
                                    },
                            )
                        },
                        onClick = {
                            activity?.let { command.performCommand(ActionContext(it)) }
                            expanded = false
                        },
                        leadingIcon = {
                            XedIcon(
                                command.getIcon(),
                                contentDescription = command.getLabel(),
                                tint =
                                    if (command is ToggleableCommand && command.isOn()) {
                                        MaterialTheme.colorScheme.primary
                                    } else LocalContentColor.current,
                            )
                        },
                        trailingIcon =
                            displayKeyCombination?.let {
                                {
                                    Text(
                                        modifier = Modifier.padding(start = 4.dp),
                                        text = it,
                                        maxLines = 1,
                                        fontFamily = FontFamily.Monospace,
                                        style = Typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            },
                    )
                }

                if (projectRootPath != null) {
                    HorizontalDivider()
                    DropdownMenuItem(
                        text = { Text("IDE Build & Cache Config") },
                        leadingIcon = {
                            Icon(painterResource(drawables.build), contentDescription = null)
                        },
                        onClick = {
                            expanded = false
                            com.goydevv.arseide.projects.IdeConfigState.open(File(projectRootPath))
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("Sandbox Dependencies") },
                        leadingIcon = {
                            Icon(painterResource(drawables.download), contentDescription = null)
                        },
                        onClick = {
                            expanded = false
                            com.goydevv.arseide.projects.DependencyManagerState.open(File(projectRootPath))
                        },
                    )
                }
            }
        }
    }
}
