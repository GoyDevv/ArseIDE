package com.goydevv.arseide.tabs.editor

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import com.goydevv.arseide.activities.main.MainActivity
import com.goydevv.arseide.commands.ActionContext
import com.goydevv.arseide.commands.CommandProvider
import com.goydevv.arseide.commands.ToggleableCommand
import com.goydevv.arseide.components.GlassSurface
import com.goydevv.arseide.icons.Icon
import com.goydevv.arseide.icons.XedIcon
import com.goydevv.arseide.settings.Settings

private data class ExtraKey(
    val label: String,
    val icon: Icon,
    val isOn: Boolean = false,
    val enabled: Boolean,
    val onClick: () -> Unit,
)

@Composable
fun ExtraKeys(editorTab: EditorTab) {
    val commandIds by remember { derivedStateOf { Settings.extra_keys_commands.split("|").toTypedArray() } }
    val commands by remember { derivedStateOf { commandIds.mapNotNull { id -> CommandProvider.getForId(id) } } }

    val commandExtraKeys =
        commands.map { command ->
            ExtraKey(
                label = command.getLabel(),
                icon = if (command.preferText) Icon.TextIcon(command.getLabel()) else command.getIcon(),
                isOn = command is ToggleableCommand && command.isOn(),
                enabled = command.isEnabled() && command.isSupported(),
                onClick = { MainActivity.instance?.let { command.performCommand(ActionContext(it)) } },
            )
        }

    val isEditable by remember { derivedStateOf { editorTab.editorState.editable } }
    val symbols = Settings.extra_keys_symbols
    val symbolExtraKeys =
        symbols.map {
            ExtraKey(
                label = it.toString(),
                icon = Icon.TextIcon(it.toString()),
                enabled = isEditable,
                onClick = {
                    val editor = editorTab.editorState.editor.get() ?: return@ExtraKey
                    val insertChar = it.toString()

                    if (insertChar == "\t") {
                        if (editor.snippetController.isInSnippet()) {
                            editor.snippetController.shiftToNextTabStop()
                        } else {
                            editor.indentOrCommitTab()
                        }
                    } else {
                        editor.insertText(insertChar, 1)
                    }
                },
            )
        }

    val extraKeys = commandExtraKeys + symbolExtraKeys

    GlassSurface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 4.dp),
        shape = RoundedCornerShape(16.dp),
        shadowElevation = 4.dp,
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
    ) {
        Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (Settings.split_extra_keys) {
                KeyRow(commandExtraKeys)
                KeyRow(symbolExtraKeys)
            } else {
                KeyRow(extraKeys)
            }
        }
    }
}

@Composable
private fun KeyRow(extraKeys: List<ExtraKey>) {
    LazyRow(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        items(extraKeys, key = { it.label }) { KeyButton(it) }
    }
}

@Composable
private fun KeyButton(key: ExtraKey) {
    val hapticFeedback = LocalHapticFeedback.current
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by
        animateFloatAsState(
            targetValue = if (isPressed && key.enabled) 0.90f else 1f,
            animationSpec = spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessMedium),
            label = "keyButtonScale",
        )

    val keyContentColor =
        when {
            key.isOn && key.enabled -> MaterialTheme.colorScheme.primary
            key.isOn -> MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
            key.enabled -> MaterialTheme.colorScheme.onSurface
            else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.42f)
        }

    val keyBgColor =
        if (Settings.extra_keys_bg) {
            if (key.isOn) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f)
            else MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = if (key.enabled) 0.85f else 0.4f)
        } else {
            Color.Transparent
        }

    Surface(
        color = keyBgColor,
        shape = RoundedCornerShape(10.dp),
        border =
            if (Settings.extra_keys_bg) {
                BorderStroke(
                    width = 0.8.dp,
                    color =
                        if (key.isOn) MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
                        else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.28f),
                )
            } else {
                null
            },
        modifier =
            Modifier.size(36.dp, 34.dp)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }
                .clip(RoundedCornerShape(10.dp))
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    enabled = key.enabled,
                    onClick = {
                        hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        key.onClick()
                    },
                ),
    ) {
        Box(contentAlignment = Alignment.Center) {
            XedIcon(
                icon = key.icon,
                modifier = Modifier.size(16.dp),
                contentDescription = key.label,
                tint = keyContentColor,
            )
        }
    }
}
