package com.goydevv.arseide.horizon

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.goydevv.arseide.file.sandboxHomeDir
import com.goydevv.arseide.projects.DetectedProjectType
import com.goydevv.arseide.projects.ProjectTypeDetector
import com.goydevv.arseide.resources.drawables
import com.goydevv.arseide.utils.toast
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Interactive ModalBottomSheet for converting a Java `.jar` file (via the Android system file
 * picker) or an existing Fabric/Forge project directory into a Horizon `.hnmod` project + bundle.
 *
 * Used from the **Projects** tab (project action sheet & FAB menu) and from **Settings > Tools**.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HnmodConverterSheet(
    initialProjectDir: File? = null,
    onDismiss: () -> Unit,
    onOpenConvertedProject: (File) -> Unit,
    onBuildConvertedProject: (File) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val homeDir = remember { sandboxHomeDir() }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var selectedProjectDir by remember { mutableStateOf(initialProjectDir) }
    var isConverting by remember { mutableStateOf(false) }
    var portResult by remember { mutableStateOf<HorizonModPorter.PortResult?>(null) }
    val javaProjects = remember { mutableStateListOf<File>() }

    // Real Android system file picker for .jar files — no manual path typing required!
    val jarPickerLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.OpenDocument(),
            onResult = { uri: Uri? ->
                if (uri != null) {
                    isConverting = true
                    selectedProjectDir = null
                    scope.launch {
                        val res =
                            HorizonModPorter.portFromUri(
                                context = context,
                                jarUri = uri,
                                targetParentDir = homeDir,
                            )
                        portResult = res
                        isConverting = false
                        if (res.success) {
                            toast("Converted ${res.modName} to ${res.modId}.hnmod")
                        } else {
                            toast(res.error ?: "Conversion failed")
                        }
                    }
                }
            },
        )

    LaunchedEffect(initialProjectDir) {
        withContext(Dispatchers.IO) {
            val found =
                homeDir
                    .listFiles()
                    ?.filter { it.isDirectory && !it.name.startsWith(".") }
                    ?.filter { dir ->
                        val t = ProjectTypeDetector.detect(dir)
                        t == DetectedProjectType.FABRIC_MOD || t == DetectedProjectType.FORGE_MOD
                    }
                    .orEmpty()
            javaProjects.clear()
            javaProjects.addAll(found)
        }
    }

    ModalBottomSheet(
        onDismissRequest = { if (!isConverting) onDismiss() },
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
    ) {
        Column(
            modifier =
                Modifier.fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 28.dp)
                    .animateContentSize(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(46.dp),
                ) {
                    Icon(
                        painter = painterResource(drawables.extension),
                        contentDescription = null,
                        modifier = Modifier.padding(11.dp),
                    )
                }
                Spacer(Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Convert to Horizon .hnmod",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = "Pick a .jar file or select a Fabric / Forge project",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // 1. Real System .jar File Picker Button
            Button(
                enabled = !isConverting,
                onClick = {
                    jarPickerLauncher.launch(
                        arrayOf(
                            "application/java-archive",
                            "application/x-java-archive",
                            "application/zip",
                            "application/octet-stream",
                            "*/*",
                        )
                    )
                },
                modifier = Modifier.fillMaxWidth().height(50.dp),
            ) {
                Icon(
                    painter = painterResource(drawables.archive),
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(10.dp))
                Text("Select Mod .jar File from Storage", fontWeight = FontWeight.SemiBold)
            }

            // 2. Existing Fabric / Forge project selector
            if (javaProjects.isNotEmpty() || selectedProjectDir != null) {
                OutlinedCard(
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = "Or convert a workspace Fabric / Forge project:",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold,
                        )

                        val listToShow =
                            remember(javaProjects.size, selectedProjectDir) {
                                buildList {
                                    selectedProjectDir?.let { add(it) }
                                    javaProjects.forEach { if (it.absolutePath != selectedProjectDir?.absolutePath) add(it) }
                                }
                            }

                        for ((idx, projDir) in listToShow.withIndex()) {
                            if (idx > 0) HorizontalDivider(thickness = 0.5.dp)
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    painter = painterResource(drawables.java),
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp),
                                )
                                Spacer(Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = projDir.name,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Text(
                                        text = ProjectTypeDetector.detect(projDir).label,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                FilledTonalButton(
                                    enabled = !isConverting,
                                    onClick = {
                                        isConverting = true
                                        scope.launch {
                                            val res =
                                                HorizonModPorter.port(
                                                    sourceFileOrDir = projDir,
                                                    targetParentDir = homeDir,
                                                )
                                            portResult = res
                                            isConverting = false
                                            if (res.success) {
                                                toast("Converted ${res.modName} to ${res.modId}.hnmod")
                                            } else {
                                                toast(res.error ?: "Conversion failed")
                                            }
                                        }
                                    },
                                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                                ) {
                                    Text("Convert")
                                }
                            }
                        }
                    }
                }
            }

            AnimatedVisibility(visible = isConverting, enter = fadeIn(), exit = fadeOut()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.5.dp)
                    Spacer(Modifier.width(12.dp))
                    Text("Inspecting Java classes & generating .hnmod bundle…", style = MaterialTheme.typography.bodyMedium)
                }
            }

            portResult?.let { res ->
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color =
                        if (res.success) MaterialTheme.colorScheme.secondaryContainer
                        else MaterialTheme.colorScheme.errorContainer,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text =
                                if (res.success)
                                    "✓ ${res.modName} (${res.modId}.hnmod · hooks API ${res.hooksApiLevel})"
                                else "Conversion Failed",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                        )
                        if (res.success) {
                            Text(
                                text =
                                    "Hooks: ${res.detectedHooks.joinToString(", ")}\n" +
                                        "Scanned: ${res.classesScanned} classes, ${res.blocksDiscovered} blocks, ${res.itemsDiscovered} items",
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace,
                            )
                        } else {
                            Text(
                                text = res.error ?: "Unknown error",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                            )
                        }

                        if (res.success) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                OutlinedButton(
                                    onClick = { onBuildConvertedProject(res.outputDir) },
                                    modifier = Modifier.weight(1f),
                                ) {
                                    Icon(painterResource(drawables.run), contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Compile & Verify")
                                }
                                Button(
                                    onClick = { onOpenConvertedProject(res.outputDir) },
                                    modifier = Modifier.weight(1f),
                                ) {
                                    Text("Open Project")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
