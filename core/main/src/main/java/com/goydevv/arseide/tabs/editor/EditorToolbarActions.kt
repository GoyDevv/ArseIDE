package com.goydevv.arseide.tabs.editor

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.material3.Surface
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
import androidx.compose.ui.platform.LocalConfiguration
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

@Composable
fun EditorToolbarActions(modifier: Modifier = Modifier, viewModel: MainViewModel) {
    var expanded by remember { mutableStateOf(false) }
    val activity = LocalActivity.current
    val screenWidthDp = LocalConfiguration.current.screenWidthDp
    val projectRootPath =
        (MainActivity.instance?.drawerViewModel?.currentDrawerTab as? FileTreeTab)?.root?.getAbsolutePath()

    val allActions by remember {
        derivedStateOf { ToolbarConfiguration.editorCommands }
    }

    val visibleActions =
        allActions.filter {
            it.isSupported() && it.id != "editor.run" && it.id != "editor.sync"
        }

    val maxInline =
        when {
            screenWidthDp >= 720 -> 8
            screenWidthDp >= 520 -> 6
            screenWidthDp >= 390 -> 5
            else -> 4
        }

    val actualVisibleCount = min(visibleActions.size, maxInline)
    val toolbarActions = visibleActions.take(actualVisibleCount)
    val dropdownActions = visibleActions.drop(actualVisibleCount)

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(2.dp, Alignment.End),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 2.dp, vertical = 1.dp),
            ) {
                toolbarActions.forEach { command ->
                    val isToggleActive = command is ToggleableCommand && command.isOn()
                    IconButton(
                        onClick = { activity?.let { command.performCommand(ActionContext(it)) } },
                        modifier = Modifier.size(36.dp),
                        enabled = command.isEnabled(),
                        colors =
                            IconButtonDefaults.iconButtonColors(
                                contentColor =
                                    if (isToggleActive) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurfaceVariant,
                                disabledContentColor =
                                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.34f),
                            ),
                    ) {
                        XedIcon(
                            icon = if (command.preferText) Icon.TextIcon(command.getLabel()) else command.getIcon(),
                            contentDescription = command.getLabel(),
                        )
                    }
                }
            }
        }

        Box(modifier = Modifier.wrapContentSize(Alignment.TopEnd)) {
            IconButton(onClick = { expanded = true }, modifier = Modifier.size(38.dp)) {
                Icon(Icons.Outlined.MoreVert, stringResource(strings.more))
            }

            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                shape = RoundedCornerShape(20.dp),
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            ) {
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
                        text = { Text("IDE Build & Toolchain Config") },
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
