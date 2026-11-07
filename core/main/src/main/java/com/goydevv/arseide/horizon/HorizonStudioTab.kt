package com.goydevv.arseide.horizon

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.goydevv.arseide.file.sandboxHomeDir
import com.goydevv.arseide.projects.DetectedProjectType
import com.goydevv.arseide.projects.ProjectTemplate
import com.goydevv.arseide.projects.ProjectTypeDetector
import com.goydevv.arseide.resources.drawables
import com.goydevv.arseide.utils.toast
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Material You (Material 3) Horizon Mod Studio fragment shown in the bottom navigation bar.
 *
 * Provides:
 * 1. Quick-create cards for all 4 Horizon Mod templates (`.hmod` Content Mod API 0.2, Client/Hybrid
 *    WASM Mod API 0.1/0.2, Server Gameplay WASM Mod API 0.3, and `.hnmod` Native Mixin Mod).
 * 2. Automated + AI-assisted "Port Java / Fabric / NeoForge Mod to Horizon Mod (`.hmod`)" pipeline
 *    that accepts either an open-source Fabric/NeoForge project folder or a `.jar` file.
 * 3. One-click Manifest Validation, 26.3 Item Model Dispatch repair, and `.hmod` / `.hnmod`
 *    Deflate Packaging for the active or selected Horizon project.
 */
@Composable
fun HorizonStudioFragment(
    activeProjectPath: String?,
    onCreateHorizonTemplate: (ProjectTemplate) -> Unit,
    onOpenProjectDir: (File) -> Unit,
    onRunBuildProject: (File) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val homeDir = remember { sandboxHomeDir() }

    var sourceInputPath by remember { mutableStateOf("") }
    var customPortName by remember { mutableStateOf("") }
    var autoPackageOnPort by remember { mutableStateOf(true) }
    var isPorting by remember { mutableStateOf(false) }
    var portResult by remember { mutableStateOf<HorizonModPorter.PortResult?>(null) }

    var validationReport by remember { mutableStateOf<HorizonModValidator.ValidationReport?>(null) }
    var packageStatusMessage by remember { mutableStateOf<String?>(null) }
    var isPackaging by remember { mutableStateOf(false) }

    val candidateJavaProjects = remember { mutableStateListOf<File>() }

    LaunchedEffect(activeProjectPath) {
        withContext(Dispatchers.IO) {
            val found = mutableListOf<File>()
            homeDir.listFiles()?.filter { it.isDirectory && !it.name.startsWith(".") }?.forEach { dir ->
                val t = ProjectTypeDetector.detect(dir)
                if (t == DetectedProjectType.FABRIC_MOD || t == DetectedProjectType.FORGE_MOD) {
                    found += dir
                }
            }
            homeDir.listFiles()?.filter { it.isFile && it.extension.equals("jar", ignoreCase = true) }?.forEach { jar ->
                found += jar
            }
            candidateJavaProjects.clear()
            candidateJavaProjects.addAll(found)
        }

        if (activeProjectPath != null) {
            val dir = File(activeProjectPath)
            val detected = ProjectTypeDetector.detect(dir)
            if (detected == DetectedProjectType.FABRIC_MOD || detected == DetectedProjectType.FORGE_MOD) {
                if (sourceInputPath.isBlank()) sourceInputPath = dir.absolutePath
            }
            if (
                detected == DetectedProjectType.HORIZON_MOD ||
                    detected == DetectedProjectType.HORIZON_MIXIN_MOD ||
                    File(dir, "horizon-mod.toml").isFile ||
                    File(dir, "horizon-mixin.toml").isFile
            ) {
                validationReport = withContext(Dispatchers.IO) { HorizonModValidator.validate(dir) }
            } else {
                validationReport = null
            }
        }
    }

    Column(
        modifier =
            modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // ---- Hero Banner ----------------------------------------------------------------------
        ElevatedCard(
            shape = RoundedCornerShape(24.dp),
            colors =
                CardDefaults.elevatedCardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(20.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(52.dp),
                ) {
                    Icon(
                        painter = painterResource(drawables.extension),
                        contentDescription = null,
                        modifier = Modifier.padding(12.dp),
                    )
                }
                Spacer(Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Horizon Mod Studio",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = "Create .hmod / .hnmod mods, port Java & Fabric mods to Minecraft 26.3 + Rust WASM, and package archives.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f),
                    )
                }
            }
        }

        // ---- Active Horizon Project One-Click Validate & Package ------------------------------
        val activeDir = activeProjectPath?.let { File(it) }
        val isActiveHorizon =
            activeDir != null &&
                (File(activeDir, "horizon-mod.toml").isFile || File(activeDir, "horizon-mixin.toml").isFile)

        if (isActiveHorizon && activeDir != null) {
            OutlinedCard(
                shape = RoundedCornerShape(20.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            painter = painterResource(drawables.archive),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp),
                        )
                        Spacer(Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Active Horizon Mod: ${activeDir.name}",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                            validationReport?.let { rep ->
                                Text(
                                    text =
                                        "id: ${rep.modId} · v${rep.version} · API ${rep.api} · " +
                                            "${rep.declaredBlocks} blocks, ${rep.declaredItems} items",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }

                    validationReport?.let { rep ->
                        if (rep.isValid) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.secondaryContainer,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(
                                    text = "✓ Manifest & 26.3 client item model dispatches verified.",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier.padding(12.dp),
                                )
                            }
                        } else {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.errorContainer,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text(
                                        text = "Validation Issues (${rep.errors.size}):",
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onErrorContainer,
                                    )
                                    Spacer(Modifier.height(4.dp))
                                    rep.errors.forEach { err ->
                                        Text(
                                            text = "• $err",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onErrorContainer,
                                        )
                                    }
                                }
                            }
                        }
                    }

                    packageStatusMessage?.let { msg ->
                        Text(
                            text = msg,
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        OutlinedButton(
                            onClick = {
                                scope.launch {
                                    withContext(Dispatchers.IO) { HorizonModPackager.ensureItemDispatches(activeDir) }
                                    validationReport = withContext(Dispatchers.IO) { HorizonModValidator.validate(activeDir) }
                                    toast("Validated ${activeDir.name}")
                                }
                            },
                            modifier = Modifier.weight(1f),
                        ) {
                            Text("Validate & Fix")
                        }
                        FilledTonalButton(
                            enabled = !isPackaging,
                            onClick = {
                                isPackaging = true
                                scope.launch {
                                    val res = HorizonModPackager.packageProject(activeDir)
                                    validationReport = res.report
                                    packageStatusMessage = res.message
                                    toast(res.message)
                                    isPackaging = false
                                }
                            },
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(if (isPackaging) "Packaging…" else "Package .hmod")
                        }
                        Button(
                            onClick = { onRunBuildProject(activeDir) },
                            modifier = Modifier.weight(1f),
                        ) {
                            Icon(painterResource(drawables.run), contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Build")
                        }
                    }
                }
            }
        }

        // ---- 1. Create New Horizon Mod (4 Official Templates) ---------------------------------
        OutlinedCard(
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        painter = painterResource(drawables.widgets),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = "1. Create Horizon Mod Project",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                }

                val horizonTemplates =
                    listOf(
                        ProjectTemplate.HORIZON_CONTENT_MOD,
                        ProjectTemplate.HORIZON_WASM_CLIENT_MOD,
                        ProjectTemplate.HORIZON_WASM_SERVER_MOD,
                        ProjectTemplate.HORIZON_NATIVE_MIXIN_MOD,
                    )

                horizonTemplates.forEachIndexed { idx, tmpl ->
                    if (idx > 0) HorizontalDivider(thickness = 0.5.dp)
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            modifier = Modifier.size(42.dp),
                        ) {
                            Icon(
                                painter = painterResource(tmpl.iconRes),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.padding(10.dp),
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = tmpl.displayName,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                text = tmpl.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        FilledTonalButton(
                            onClick = { onCreateHorizonTemplate(tmpl) },
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                        ) {
                            Text("Create")
                        }
                    }
                }
            }
        }

        // ---- 2. Port Java / Fabric / NeoForge Mod to Horizon (.hmod) --------------------------
        OutlinedCard(
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        painter = painterResource(drawables.auto_fix),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "2. Port Java / Fabric Mod to Horizon (.hmod)",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = "Upgrades 1.20/1.21 assets & data to 26.3, converts Java registries to Horizon JSON, and translates HUD/events to Rust WASM.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                if (candidateJavaProjects.isNotEmpty()) {
                    Text(
                        text = "Detected Java/Fabric mods in workspace (tap to select):",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        candidateJavaProjects.forEach { candidate ->
                            Surface(
                                onClick = {
                                    sourceInputPath = candidate.absolutePath
                                    if (customPortName.isBlank()) {
                                        customPortName = "${HorizonModPorter.sanitizeModId(candidate.nameWithoutExtension)}-hmod"
                                    }
                                },
                                shape = RoundedCornerShape(12.dp),
                                color =
                                    if (sourceInputPath == candidate.absolutePath)
                                        MaterialTheme.colorScheme.primaryContainer
                                    else MaterialTheme.colorScheme.surfaceContainerHigh,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Icon(
                                        painter = painterResource(drawables.java),
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp),
                                    )
                                    Spacer(Modifier.width(10.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = candidate.name,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.SemiBold,
                                        )
                                        Text(
                                            text = candidate.absolutePath,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                OutlinedTextField(
                    value = sourceInputPath,
                    onValueChange = { sourceInputPath = it },
                    singleLine = true,
                    label = { Text("Source .jar file or Fabric/NeoForge project folder path") },
                    placeholder = { Text("${homeDir.absolutePath}/my-fabric-mod") },
                    leadingIcon = { Icon(painterResource(drawables.folder_code), contentDescription = null) },
                    modifier = Modifier.fillMaxWidth(),
                )

                OutlinedTextField(
                    value = customPortName,
                    onValueChange = { customPortName = it },
                    singleLine = true,
                    label = { Text("Output folder name (optional)") },
                    placeholder = { Text("ported_mod-hmod") },
                    leadingIcon = { Icon(painterResource(drawables.folder), contentDescription = null) },
                    modifier = Modifier.fillMaxWidth(),
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Automatically package <id>.hmod archive after porting",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                    )
                    Switch(checked = autoPackageOnPort, onCheckedChange = { autoPackageOnPort = it })
                }

                Button(
                    enabled = sourceInputPath.isNotBlank() && !isPorting,
                    onClick = {
                        isPorting = true
                        val srcFile = File(sourceInputPath.trim())
                        scope.launch {
                            val res =
                                HorizonModPorter.port(
                                    sourceFileOrDir = srcFile,
                                    targetParentDir = homeDir,
                                    customOutputName = customPortName.trim().ifBlank { null },
                                    alsoPackageHmod = autoPackageOnPort,
                                )
                            portResult = res
                            isPorting = false
                            if (res.success) {
                                toast("Ported ${res.modName} to ${res.outputDir.name}")
                            } else {
                                toast(res.error ?: "Porting failed")
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                ) {
                    if (isPorting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                        Spacer(Modifier.width(10.dp))
                        Text("Porting to Horizon .hmod…")
                    } else {
                        Icon(painterResource(drawables.auto_fix), contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Port to Horizon Mod (.hmod)")
                    }
                }

                portResult?.let { res ->
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color =
                            if (res.success) MaterialTheme.colorScheme.secondaryContainer
                            else MaterialTheme.colorScheme.errorContainer,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Text(
                                text =
                                    if (res.success)
                                        "✓ Ported ${res.modName} (id=${res.modId}, API ${res.apiVersion})"
                                    else "✗ Porting Failed",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                            )
                            res.logs.forEach { line ->
                                Text(
                                    text = "• $line",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontFamily = FontFamily.Monospace,
                                )
                            }
                            if (res.success) {
                                Spacer(Modifier.height(4.dp))
                                Button(
                                    onClick = { onOpenProjectDir(res.outputDir) },
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Text("Open Ported Project in Editor")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
