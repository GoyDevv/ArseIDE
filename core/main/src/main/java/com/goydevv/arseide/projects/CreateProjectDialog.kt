package com.goydevv.arseide.projects

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.goydevv.arseide.exec.isTerminalInstalled
import com.goydevv.arseide.resources.drawables
import com.goydevv.arseide.resources.strings
import java.io.File

/**
 * Create Project bottom sheet.
 *
 * Primary templates (Minecraft Java Mod, Android Compose, Python, Node.js, Web, Empty) appear first,
 * and Horizon `.hnmod` templates appear at the very back of the template list.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateProjectDialog(
    projectsDir: File,
    initialTemplate: ProjectTemplate = ProjectTemplate.MINECRAFT_MOD,
    onDismiss: () -> Unit,
    onCreate: (ProjectConfig) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var template by remember { mutableStateOf(initialTemplate) }
    var modLoader by remember { mutableStateOf(ModLoader.FABRIC) }
    var packageName by remember { mutableStateOf("") }
    var packageEdited by remember { mutableStateOf(false) }
    var author by remember { mutableStateOf("GoyDevv") }
    var modId by remember { mutableStateOf("") }
    var modDescription by remember { mutableStateOf("") }
    var modVersion by remember { mutableStateOf("0.1.0") }
    var minecraftVersion by remember { mutableStateOf("") }
    var jdkVersion by remember { mutableStateOf("21") }
    var jdkEdited by remember { mutableStateOf(false) }
    var sdkVersion by remember { mutableStateOf("35") }
    var initGit by remember { mutableStateOf(false) }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val parentDir = projectsDir
    val trimmedName = name.trim()

    var mcVersions by remember { mutableStateOf(MinecraftVersions.FALLBACK) }
    var loadingVersions by remember { mutableStateOf(false) }
    LaunchedEffect(template) {
        if (template == ProjectTemplate.MINECRAFT_MOD) {
            if (minecraftVersion.isBlank()) minecraftVersion = mcVersions.first()
            loadingVersions = true
            mcVersions = MinecraftVersions.fetchReleases()
            if (minecraftVersion !in mcVersions) minecraftVersion = mcVersions.first()
            loadingVersions = false
        }
    }

    var sdkVersions by remember { mutableStateOf(AndroidSdkVersions.FALLBACK) }
    var loadingSdk by remember { mutableStateOf(false) }
    LaunchedEffect(template) {
        if (template == ProjectTemplate.ANDROID_COMPOSE) {
            if (jdkVersion !in listOf("17", "21")) jdkVersion = "21"
            if (sdkVersion.isBlank()) sdkVersion = "35"
            loadingSdk = true
            sdkVersions = AndroidSdkVersions.fetchApiLevels()
            if (sdkVersion !in sdkVersions) {
                sdkVersion = if ("35" in sdkVersions) "35" else sdkVersions.first()
            }
            loadingSdk = false
        }
    }

    LaunchedEffect(trimmedName, author, template) {
        if (template.showsPackageName && !packageEdited) {
            packageName =
                ProjectConfig(
                        name = trimmedName.ifBlank { "app" },
                        template = template,
                        parentDir = parentDir,
                        packageName = "",
                        author = author.trim(),
                    )
                    .resolvedPackageName()
        }
    }

    LaunchedEffect(minecraftVersion, template) {
        if (template == ProjectTemplate.MINECRAFT_MOD && !jdkEdited && minecraftVersion.isNotBlank()) {
            jdkVersion = recommendedJdkForMc(minecraftVersion)
        }
    }

    var toolState by remember { mutableStateOf<ToolState>(ToolState.None) }
    LaunchedEffect(template, jdkVersion) {
        val cfg = ProjectConfig(name = "check", template = template, parentDir = parentDir, jdkVersion = jdkVersion)
        val tools = ProjectDependencies.requiredTools(cfg)
        when {
            tools.isEmpty() -> toolState = ToolState.None
            !isTerminalInstalled() -> toolState = ToolState.NoTerminal
            else -> {
                toolState = ToolState.Checking
                val missing = ProjectDependencies.missingTools(tools)
                toolState = if (missing.isEmpty()) ToolState.Ready else ToolState.Missing(missing.map { it.name })
            }
        }
    }

    val nameError: String? =
        when {
            trimmedName.isEmpty() -> null
            trimmedName.contains('/') || trimmedName.contains('\\') || trimmedName == "." || trimmedName == ".." ->
                stringResource(strings.invalid_project_name_err)
            File(parentDir, trimmedName).exists() -> stringResource(strings.project_exists_err)
            else -> null
        }
    val canCreate = trimmedName.isNotEmpty() && nameError == null

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier =
                Modifier.fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 28.dp)
                    .animateContentSize(),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            // Header
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.size(48.dp),
                    ) {
                        Icon(
                            painter = painterResource(template.iconRes),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(11.dp),
                        )
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(strings.create_project),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = template.displayName,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }
            }

            // Template chooser (Horizon .hnmod templates are at the very end)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FormSectionHeader(icon = drawables.widgets, title = stringResource(strings.project_template))
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    ProjectTemplate.entries.forEach { t ->
                        TemplateCard(template = t, selected = template == t) { template = t }
                    }
                }
                Text(
                    text = template.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // Project details
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                FormSectionHeader(icon = drawables.edit, title = "Project")
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    singleLine = true,
                    label = { Text(stringResource(strings.project_name)) },
                    leadingIcon = { Icon(painterResource(drawables.folder_code), contentDescription = null) },
                    isError = nameError != null,
                    supportingText = nameError?.let { { Text(it, color = MaterialTheme.colorScheme.error) } },
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            // Horizon .hnmod options
            if (template.isHorizonTemplate) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    FormSectionHeader(icon = drawables.extension, title = "Horizon .hnmod")
                    OutlinedTextField(
                        value = modId,
                        onValueChange = { modId = it },
                        singleLine = true,
                        label = { Text("Mod ID") },
                        placeholder = {
                            Text(
                                ProjectConfig(name = trimmedName, template = template, parentDir = parentDir)
                                    .resolvedModId()
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = modDescription,
                        onValueChange = { modDescription = it },
                        label = { Text(stringResource(strings.mod_description)) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = modVersion,
                        onValueChange = { modVersion = it },
                        singleLine = true,
                        label = { Text(stringResource(strings.mod_version)) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            // Minecraft Java Mod options
            if (template.showsMinecraftOptions) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    FormSectionHeader(icon = drawables.coffee, title = "Mod settings")
                    LabeledDropdown(
                        label = stringResource(strings.mod_loader),
                        selected = modLoader.displayName,
                        options = ModLoader.entries.map { it.displayName },
                        onSelect = { display -> modLoader = ModLoader.entries.first { it.displayName == display } },
                    )
                    LabeledDropdown(
                        label =
                            stringResource(strings.minecraft_version) +
                                if (loadingVersions) " (${stringResource(strings.loading_versions)})" else "",
                        selected = minecraftVersion.ifBlank { mcVersions.first() },
                        options = mcVersions,
                        onSelect = { minecraftVersion = it },
                    )
                    OutlinedTextField(
                        value = modId,
                        onValueChange = { modId = it },
                        singleLine = true,
                        label = { Text(stringResource(strings.mod_id)) },
                        placeholder = {
                            Text(
                                ProjectConfig(name = trimmedName, template = template, parentDir = parentDir)
                                    .resolvedModId()
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = modDescription,
                        onValueChange = { modDescription = it },
                        label = { Text(stringResource(strings.mod_description)) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = modVersion,
                        onValueChange = { modVersion = it },
                        singleLine = true,
                        label = { Text(stringResource(strings.mod_version)) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            // Package + author + JDK/SDK
            if (template.showsPackageName) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    FormSectionHeader(icon = drawables.folder_code, title = stringResource(strings.package_name))
                    OutlinedTextField(
                        value = packageName,
                        onValueChange = {
                            packageName = it
                            packageEdited = true
                        },
                        singleLine = true,
                        label = { Text(stringResource(strings.package_name)) },
                        leadingIcon = { Icon(painterResource(drawables.folder_code), contentDescription = null) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = author,
                        onValueChange = { author = it },
                        singleLine = true,
                        label = { Text(stringResource(strings.author_name)) },
                        leadingIcon = { Icon(painterResource(drawables.person), contentDescription = null) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    val jdkOptions =
                        if (template == ProjectTemplate.ANDROID_COMPOSE) listOf("17", "21")
                        else listOf("8", "11", "17", "21")
                    LabeledDropdown(
                        label = stringResource(strings.jdk_version),
                        selected = if (jdkVersion in jdkOptions) jdkVersion else jdkOptions.last(),
                        options = jdkOptions,
                        onSelect = {
                            jdkVersion = it
                            jdkEdited = true
                        },
                    )
                    if (template == ProjectTemplate.ANDROID_COMPOSE) {
                        LabeledDropdown(
                            label =
                                "Android SDK" +
                                    if (loadingSdk) " (${stringResource(strings.loading_versions)})" else "",
                            selected = AndroidSdkVersions.label(sdkVersion.ifBlank { sdkVersions.first() }),
                            options = sdkVersions.map { AndroidSdkVersions.label(it) },
                            onSelect = { display -> sdkVersion = AndroidSdkVersions.apiFromLabel(display) },
                        )
                    }
                }
            }

            // Git Option
            OutlinedCard(
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Icon(
                            painter = painterResource(drawables.git),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(22.dp),
                        )
                        Spacer(Modifier.width(14.dp))
                        Text(stringResource(strings.git_init))
                    }
                    Switch(checked = initGit, onCheckedChange = { initGit = it })
                }
            }

            when (val state = toolState) {
                ToolState.None -> {}
                ToolState.Checking ->
                    ToolStatusRow(
                        drawables.info,
                        stringResource(strings.tools_checking),
                        MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                ToolState.Ready ->
                    ToolStatusRow(
                        drawables.info,
                        stringResource(strings.tools_installed),
                        MaterialTheme.colorScheme.primary,
                    )
                ToolState.NoTerminal ->
                    ToolStatusRow(
                        drawables.cloud_off,
                        stringResource(strings.tools_no_terminal),
                        MaterialTheme.colorScheme.error,
                    )
                is ToolState.Missing ->
                    ToolStatusRow(
                        drawables.download,
                        stringResource(strings.tools_missing) + " " + state.names.joinToString(", "),
                        MaterialTheme.colorScheme.tertiary,
                    )
            }
            ToolStatusRow(
                icon = drawables.folder,
                text = File(parentDir, trimmedName.ifBlank { "<name>" }).absolutePath,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f).height(50.dp)) {
                    Text(stringResource(strings.cancel))
                }
                Button(
                    enabled = canCreate,
                    onClick = {
                        onCreate(
                            ProjectConfig(
                                name = trimmedName,
                                template = template,
                                parentDir = parentDir,
                                packageName = packageName.trim(),
                                author = author.trim(),
                                modLoader = if (template.showsMinecraftOptions) modLoader else null,
                                modId = modId.trim(),
                                modDescription = modDescription.trim(),
                                modVersion = modVersion.trim().ifBlank { "0.1.0" },
                                minecraftVersion = minecraftVersion.trim(),
                                jdkVersion = jdkVersion.trim().ifBlank { "21" },
                                sdkVersion =
                                    if (template == ProjectTemplate.ANDROID_COMPOSE) sdkVersion.trim() else "",
                                initGit = initGit,
                            )
                        )
                    },
                    modifier = Modifier.weight(1f).height(50.dp),
                ) {
                    Icon(painterResource(drawables.add), contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(strings.create))
                }
            }
        }
    }
}

@Composable
private fun TemplateCard(template: ProjectTemplate, selected: Boolean, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color =
            if (selected) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surfaceContainerHigh,
        border =
            if (selected) BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
            else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
        modifier = Modifier.width(114.dp).clickable(onClick = onClick),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(
                painter = painterResource(template.iconRes),
                contentDescription = null,
                tint =
                    if (selected) MaterialTheme.colorScheme.onPrimaryContainer
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(28.dp),
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = template.displayName,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                textAlign = TextAlign.Center,
                maxLines = 2,
                minLines = 2,
                overflow = TextOverflow.Ellipsis,
                color =
                    if (selected) MaterialTheme.colorScheme.onPrimaryContainer
                    else MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
private fun FormSectionHeader(icon: Int, title: String) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Icon(
            painter = painterResource(icon),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

@Composable
private fun ToolStatusRow(icon: Int, text: String, color: Color) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Icon(painter = painterResource(icon), contentDescription = null, tint = color, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(10.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = color,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LabeledDropdown(label: String, selected: String, options: List<String>, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded },
        modifier = Modifier.fillMaxWidth(),
    ) {
        OutlinedTextField(
            value = selected,
            onValueChange = {},
            readOnly = true,
            singleLine = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option) },
                    onClick = {
                        onSelect(option)
                        expanded = false
                    },
                )
            }
        }
    }
}

private sealed interface ToolState {
    data object None : ToolState

    data object Checking : ToolState

    data object Ready : ToolState

    data object NoTerminal : ToolState

    data class Missing(val names: List<String>) : ToolState
}

private fun recommendedJdkForMc(mc: String): String {
    val parts = mc.split(".")
    val first = parts.getOrNull(0)?.toIntOrNull() ?: 1
    if (first >= 26) return "21"
    val minor = parts.getOrNull(1)?.toIntOrNull() ?: 0
    val patch = parts.getOrNull(2)?.toIntOrNull() ?: 0
    return when {
        minor >= 21 -> "21"
        minor == 20 && patch >= 5 -> "21"
        minor >= 17 -> "17"
        else -> "8"
    }
}
