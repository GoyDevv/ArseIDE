package com.goydevv.arseide.projects

import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.goydevv.arseide.resources.drawables
import com.goydevv.arseide.runner.RunOutputState
import com.goydevv.arseide.utils.toast
import java.io.File
import kotlinx.coroutines.launch

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AndroidBuildTab(
    projectRoot: File,
    onBuildApk: () -> Unit,
    onSyncGradle: () -> Unit,
    onOpenIdeConfig: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var buildType by remember(projectRoot.absolutePath) { mutableStateOf(GradleConfig.buildType(projectRoot)) }
    var logLevel by remember(projectRoot.absolutePath) { mutableStateOf(GradleConfig.logLevel(projectRoot)) }
    var selectedFlags by remember(projectRoot.absolutePath) { mutableStateOf(GradleConfig.selectedFlags(projectRoot)) }

    val initialSigning = remember(projectRoot.absolutePath) { AndroidSigningConfig.load(projectRoot) }
    var signingMode by remember(projectRoot.absolutePath) { mutableStateOf(initialSigning.mode) }
    var keystorePath by remember(projectRoot.absolutePath) { mutableStateOf(initialSigning.keystorePath) }
    var keyAlias by remember(projectRoot.absolutePath) { mutableStateOf(initialSigning.keyAlias) }
    var storePassword by remember(projectRoot.absolutePath) { mutableStateOf(initialSigning.storePassword) }
    var keyPassword by remember(projectRoot.absolutePath) { mutableStateOf(initialSigning.keyPassword) }

    var defaultKeyExists by remember(projectRoot.absolutePath) { mutableStateOf(AndroidSigningConfig.hasDefaultKeystore()) }
    var generatingDefault by remember { mutableStateOf(false) }
    var showCreateKeyDialog by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf<String?>(null) }

    fun persistSigning(
        mode: SigningMode = signingMode,
        path: String = keystorePath,
        alias: String = keyAlias,
        sPass: String = storePassword,
        kPass: String = keyPassword,
    ) {
        AndroidSigningConfig.save(
            projectRoot,
            SigningProfile(
                mode = mode,
                keystorePath = path,
                keyAlias = alias,
                storePassword = sPass,
                keyPassword = kPass,
            ),
        )
    }

    val keystorePicker =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) {
                scope.launch {
                    val displayName =
                        runCatching {
                            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                                val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                                if (cursor.moveToFirst() && idx >= 0) cursor.getString(idx) else null
                            }
                        }.getOrNull()
                    val imported = AndroidSigningConfig.importKeystoreFromUri(context, uri, displayName)
                    if (imported != null) {
                        val sandboxPath = AndroidSigningConfig.toSandboxKeystorePath(imported.absolutePath)
                        keystorePath = sandboxPath
                        signingMode = SigningMode.CUSTOM_KEYSTORE
                        persistSigning(mode = SigningMode.CUSTOM_KEYSTORE, path = sandboxPath)
                        statusMessage = "Imported keystore: ${imported.name}"
                        toast("Keystore imported: ${imported.name}")
                    } else {
                        toast("Could not read selected keystore file")
                    }
                }
            }
        }

    val scheme = MaterialTheme.colorScheme

    Column(
        modifier =
            modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 14.dp)
                .padding(bottom = 24.dp)
                .animateContentSize(
                    animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow)
                ),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        ElevatedCard(
            shape = RoundedCornerShape(28.dp),
            colors =
                CardDefaults.elevatedCardColors(
                    containerColor = scheme.primaryContainer,
                    contentColor = scheme.onPrimaryContainer,
                ),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = scheme.primary,
                        contentColor = scheme.onPrimary,
                        modifier = Modifier.size(48.dp),
                    ) {
                        Icon(
                            painter = painterResource(drawables.apk_document),
                            contentDescription = null,
                            modifier = Modifier.padding(12.dp),
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = projectRoot.name,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.ExtraBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text =
                                "${buildType.label} APK · ${signingMode.label}",
                            style = MaterialTheme.typography.bodySmall,
                            color = scheme.onPrimaryContainer.copy(alpha = 0.85f),
                        )
                    }
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = scheme.surface,
                        contentColor = scheme.primary,
                    ) {
                        Text(
                            text = buildType.id.uppercase(),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Button(
                        onClick = {
                            if (RunOutputState.isRunning) {
                                RunOutputState.stop()
                            } else {
                                persistSigning()
                                onBuildApk()
                            }
                        },
                        shape = RoundedCornerShape(16.dp),
                        colors =
                            if (RunOutputState.isRunning) {
                                ButtonDefaults.buttonColors(
                                    containerColor = scheme.error,
                                    contentColor = scheme.onError,
                                )
                            } else {
                                ButtonDefaults.buttonColors(
                                    containerColor = scheme.primary,
                                    contentColor = scheme.onPrimary,
                                )
                            },
                        contentPadding = PaddingValues(vertical = 13.dp, horizontal = 16.dp),
                        modifier = Modifier.weight(1.3f),
                    ) {
                        Icon(
                            painter = painterResource(if (RunOutputState.isRunning) drawables.stop else drawables.run),
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text =
                                if (RunOutputState.isRunning) "Stop Build"
                                else "Build ${buildType.label} APK",
                            fontWeight = FontWeight.Bold,
                        )
                    }

                    FilledTonalButton(
                        onClick = onSyncGradle,
                        enabled = !RunOutputState.isRunning,
                        shape = RoundedCornerShape(16.dp),
                        colors =
                            ButtonDefaults.filledTonalButtonColors(
                                containerColor = scheme.surface,
                                contentColor = scheme.onSurface,
                            ),
                        contentPadding = PaddingValues(vertical = 13.dp, horizontal = 14.dp),
                        modifier = Modifier.weight(0.9f),
                    ) {
                        Icon(
                            painter = painterResource(drawables.refresh),
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text("Sync", fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }

        SectionContainer(
            title = "APK Build Type",
            subtitle = "Choose the Gradle variant assembled by Build & Run",
            iconRes = drawables.build,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                GradleConfig.BuildType.entries.forEach { variant ->
                    val selected = buildType == variant
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = if (selected) scheme.secondaryContainer else scheme.surfaceContainerHigh,
                        contentColor = if (selected) scheme.onSecondaryContainer else scheme.onSurface,
                        border =
                            if (selected) BorderStroke(1.5.dp, scheme.primary)
                            else BorderStroke(1.dp, scheme.outlineVariant.copy(alpha = 0.5f)),
                        modifier =
                            Modifier.weight(1f)
                                .clip(RoundedCornerShape(20.dp))
                                .clickable {
                                    buildType = variant
                                    GradleConfig.setBuildType(projectRoot, variant)
                                },
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(
                                    text = variant.label,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                )
                                RadioButton(
                                    selected = selected,
                                    onClick = {
                                        buildType = variant
                                        GradleConfig.setBuildType(projectRoot, variant)
                                    },
                                )
                            }
                            Text(
                                text =
                                    if (variant == GradleConfig.BuildType.DEBUG) "assembleDebug"
                                    else "assembleRelease",
                                style = MaterialTheme.typography.labelSmall,
                                fontFamily = FontFamily.Monospace,
                                color = scheme.primary,
                            )
                            Text(
                                text =
                                    if (variant == GradleConfig.BuildType.DEBUG) "Fast incremental debuggable APK"
                                    else "Optimized release APK ready to sign",
                                style = MaterialTheme.typography.bodySmall,
                                color = scheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }

        SectionContainer(
            title = "APK Signing",
            subtitle = "Real zipalign + apksigner signing wired into every Android build",
            iconRes = drawables.lock,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SigningMode.entries.forEach { mode ->
                    val selected = signingMode == mode
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = if (selected) scheme.secondaryContainer else scheme.surfaceContainerHigh,
                        contentColor = if (selected) scheme.onSecondaryContainer else scheme.onSurface,
                        border =
                            if (selected) BorderStroke(1.5.dp, scheme.primary)
                            else BorderStroke(1.dp, scheme.outlineVariant.copy(alpha = 0.45f)),
                        modifier =
                            Modifier.fillMaxWidth()
                                .clip(RoundedCornerShape(20.dp))
                                .clickable {
                                    signingMode = mode
                                    persistSigning(mode = mode)
                                },
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(
                                selected = selected,
                                onClick = {
                                    signingMode = mode
                                    persistSigning(mode = mode)
                                },
                            )
                            Spacer(Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = mode.label,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                )
                                Text(
                                    text = mode.description,
                                    style = MaterialTheme.typography.bodySmall,
                                    color =
                                        if (selected) scheme.onSecondaryContainer.copy(alpha = 0.85f)
                                        else scheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }

                AnimatedVisibility(visible = signingMode == SigningMode.DEFAULT_ARSEIDE) {
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = scheme.surfaceContainerHighest,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = "Default Keystore Status",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                Surface(
                                    shape = RoundedCornerShape(50),
                                    color =
                                        if (defaultKeyExists) scheme.primaryContainer
                                        else scheme.tertiaryContainer,
                                    contentColor =
                                        if (defaultKeyExists) scheme.onPrimaryContainer
                                        else scheme.onTertiaryContainer,
                                ) {
                                    Text(
                                        text = if (defaultKeyExists) "Ready" else "Auto-generates on build",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                    )
                                }
                            }
                            Text(
                                text = "Path: ${AndroidSigningConfig.DEFAULT_KEYSTORE_SANDBOX_PATH}\nAlias: ${AndroidSigningConfig.DEFAULT_ALIAS}",
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace,
                                color = scheme.onSurfaceVariant,
                            )
                            if (!defaultKeyExists) {
                                FilledTonalButton(
                                    enabled = !generatingDefault,
                                    onClick = {
                                        generatingDefault = true
                                        scope.launch {
                                            val (ok, msg) = AndroidSigningConfig.ensureDefaultKeystore()
                                            defaultKeyExists = AndroidSigningConfig.hasDefaultKeystore()
                                            generatingDefault = false
                                            statusMessage = msg
                                            toast(if (ok) "ArseIDE Signing Key created" else msg)
                                        }
                                    },
                                    shape = RoundedCornerShape(14.dp),
                                ) {
                                    if (generatingDefault) {
                                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                        Spacer(Modifier.width(8.dp))
                                        Text("Generating RSA-2048 Key…")
                                    } else {
                                        Text("Generate ArseIDE Key Now")
                                    }
                                }
                            }
                        }
                    }
                }

                AnimatedVisibility(visible = signingMode == SigningMode.CUSTOM_KEYSTORE) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            FilledTonalButton(
                                onClick = { keystorePicker.launch(arrayOf("*/*")) },
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.weight(1f),
                            ) {
                                Icon(
                                    painter = painterResource(drawables.file_symlink),
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                )
                                Spacer(Modifier.width(6.dp))
                                Text("Pick Keystore")
                            }
                            Button(
                                onClick = { showCreateKeyDialog = true },
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.weight(1f),
                            ) {
                                Icon(
                                    painter = painterResource(drawables.add),
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                )
                                Spacer(Modifier.width(6.dp))
                                Text("Create New Key")
                            }
                        }

                        OutlinedTextField(
                            value = keystorePath,
                            onValueChange = {
                                keystorePath = it
                                persistSigning(path = it)
                            },
                            label = { Text("Keystore path (.keystore / .jks)") },
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth(),
                        )

                        OutlinedTextField(
                            value = keyAlias,
                            onValueChange = {
                                keyAlias = it
                                persistSigning(alias = it)
                            },
                            label = { Text("Key alias") },
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth(),
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            OutlinedTextField(
                                value = storePassword,
                                onValueChange = {
                                    storePassword = it
                                    persistSigning(sPass = it)
                                },
                                label = { Text("Store password") },
                                singleLine = true,
                                visualTransformation = PasswordVisualTransformation(),
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.weight(1f),
                            )
                            OutlinedTextField(
                                value = keyPassword,
                                onValueChange = {
                                    keyPassword = it
                                    persistSigning(kPass = it)
                                },
                                label = { Text("Key password") },
                                singleLine = true,
                                visualTransformation = PasswordVisualTransformation(),
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }

                statusMessage?.let { msg ->
                    Text(
                        text = msg,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        color = scheme.primary,
                    )
                }
            }
        }

        SectionContainer(
            title = "Gradle Performance & Flags",
            subtitle = "Tuned for low-RAM smooth builds with G1GC and 2 workers",
            iconRes = drawables.bolt,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Build Flags",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    val quickFlags =
                        listOf(
                            "--build-cache" to "Build Cache",
                            "--parallel" to "Parallel",
                            "--offline" to "Offline",
                            "--no-daemon" to "No Daemon (Low RAM)",
                            "--stacktrace" to "Stacktrace",
                        )
                    quickFlags.forEach { (arg, label) ->
                        val active = selectedFlags.contains(arg)
                        FilterChip(
                            selected = active,
                            onClick = {
                                GradleConfig.setFlag(projectRoot, arg, !active)
                                selectedFlags = GradleConfig.selectedFlags(projectRoot)
                            },
                            label = { Text(label) },
                        )
                    }
                }

                Text(
                    text = "Log Verbosity",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    GradleConfig.LogLevel.entries.forEach { lvl ->
                        FilterChip(
                            selected = logLevel == lvl,
                            onClick = {
                                logLevel = lvl
                                GradleConfig.setLogLevel(projectRoot, lvl)
                            },
                            label = { Text(lvl.label.substringBefore(" (")) },
                        )
                    }
                }

                OutlinedButton(
                    onClick = onOpenIdeConfig,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(
                        painter = painterResource(drawables.settings),
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Advanced JDK, NDK & Build-Tools Config")
                }
            }
        }
    }

    if (showCreateKeyDialog) {
        CreateKeystoreDialog(
            onDismiss = { showCreateKeyDialog = false },
            onCreated = { createdPath, alias, sPass, kPass ->
                showCreateKeyDialog = false
                signingMode = SigningMode.CUSTOM_KEYSTORE
                keystorePath = createdPath
                keyAlias = alias
                storePassword = sPass
                keyPassword = kPass
                persistSigning(
                    mode = SigningMode.CUSTOM_KEYSTORE,
                    path = createdPath,
                    alias = alias,
                    sPass = sPass,
                    kPass = kPass,
                )
                statusMessage = "Created and activated keystore: $createdPath"
            },
        )
    }
}

@Composable
private fun SectionContainer(
    title: String,
    subtitle: String,
    iconRes: Int,
    content: @Composable () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = scheme.surfaceContainerLow,
        tonalElevation = 1.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = scheme.secondaryContainer,
                    contentColor = scheme.onSecondaryContainer,
                    modifier = Modifier.size(36.dp),
                ) {
                    Icon(
                        painter = painterResource(iconRes),
                        contentDescription = null,
                        modifier = Modifier.padding(8.dp),
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.onSurfaceVariant,
                    )
                }
            }
            content()
        }
    }
}

@Composable
private fun CreateKeystoreDialog(
    onDismiss: () -> Unit,
    onCreated: (sandboxPath: String, alias: String, storePass: String, keyPass: String) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var fileName by remember { mutableStateOf("my-release.keystore") }
    var alias by remember { mutableStateOf("release") }
    var storePass by remember { mutableStateOf("") }
    var keyPass by remember { mutableStateOf("") }
    var commonName by remember { mutableStateOf("") }
    var organization by remember { mutableStateOf("") }
    var validityYears by remember { mutableStateOf("25") }
    var creating by remember { mutableStateOf(false) }
    var errorText by remember { mutableStateOf<String?>(null) }

    val validInputs =
        fileName.isNotBlank() &&
            alias.isNotBlank() &&
            storePass.length >= 6 &&
            (keyPass.isBlank() || keyPass.length >= 6)

    AlertDialog(
        onDismissRequest = { if (!creating) onDismiss() },
        shape = RoundedCornerShape(28.dp),
        title = { Text("Create Signing Keystore", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = "Generates a real PKCS12 RSA-2048 keystore inside your sandbox using keytool.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = fileName,
                    onValueChange = { fileName = it },
                    label = { Text("Keystore filename") },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = alias,
                    onValueChange = { alias = it },
                    label = { Text("Key alias") },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = storePass,
                    onValueChange = { storePass = it },
                    label = { Text("Store password (min 6 chars)") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = keyPass,
                    onValueChange = { keyPass = it },
                    label = { Text("Key password (leave blank to match store)") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = commonName,
                    onValueChange = { commonName = it },
                    label = { Text("Your Name / CN") },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedTextField(
                        value = organization,
                        onValueChange = { organization = it },
                        label = { Text("Organization") },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.weight(1.4f),
                    )
                    OutlinedTextField(
                        value = validityYears,
                        onValueChange = { validityYears = it.filter { ch -> ch.isDigit() } },
                        label = { Text("Years") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.weight(0.8f),
                    )
                }
                errorText?.let { err ->
                    Text(
                        text = err,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            Button(
                enabled = validInputs && !creating,
                shape = RoundedCornerShape(14.dp),
                onClick = {
                    creating = true
                    errorText = null
                    val finalKeyPass = keyPass.ifBlank { storePass }
                    scope.launch {
                        val (ok, result) =
                            AndroidSigningConfig.generateKeystore(
                                fileName = fileName,
                                alias = alias,
                                storePassword = storePass,
                                keyPassword = finalKeyPass,
                                commonName = commonName,
                                organization = organization,
                                validityYears = validityYears.toIntOrNull() ?: 25,
                            )
                        creating = false
                        if (ok) {
                            toast("Keystore created successfully")
                            onCreated(result, alias.trim(), storePass, finalKeyPass)
                        } else {
                            errorText = result
                        }
                    }
                },
            ) {
                if (creating) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                    Text("Creating…")
                } else {
                    Text("Create Keystore")
                }
            }
        },
        dismissButton = {
            TextButton(enabled = !creating, onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}
