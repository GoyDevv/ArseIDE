package com.goydevv.arseide.activities.main

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.goydevv.arseide.resources.drawables
import com.goydevv.arseide.resources.strings
import com.goydevv.arseide.terminal.AutoSetup
import com.goydevv.arseide.terminal.SetupEstimate

/**
 * Global trigger so the user can open the Auto-Setup screen at any time from Settings,
 * Projects, or Terminal if they skipped it on first launch.
 */
var showAutoSetupDialogGlobal by mutableStateOf(false)

@Composable
fun AutoSetupDialog(onLaunch: () -> Unit, onDismissForever: () -> Unit) {
    val activity = LocalActivity.current
    var showDeclineWarning by remember { mutableStateOf(false) }
    var estimate by remember { mutableStateOf<SetupEstimate?>(null) }

    LaunchedEffect(Unit) { estimate = AutoSetup.estimate() }

    if (showDeclineWarning) {
        AlertDialog(
            onDismissRequest = { showDeclineWarning = false },
            title = { Text("Skip sandbox setup for now?") },
            text = {
                Text(
                    "You can still edit files, browse projects, and convert .jar mods. Whenever you open the Terminal or Settings later, you'll have a one-tap option to run Auto-Setup.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeclineWarning = false
                        onDismissForever()
                    }
                ) {
                    Text("Skip for Now")
                }
            },
            dismissButton = {
                Button(onClick = { showDeclineWarning = false }) {
                    Text(stringResource(strings.auto_setup_go_back))
                }
            },
        )
        return
    }

    Dialog(
        onDismissRequest = { showDeclineWarning = true },
        properties =
            DialogProperties(
                usePlatformDefaultWidth = false,
                dismissOnBackPress = true,
                dismissOnClickOutside = false,
            ),
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
        ) {
            Column(modifier = Modifier.fillMaxSize().safeContentPadding().padding(24.dp)) {
                Column(
                    modifier = Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(56.dp),
                    ) {
                        Icon(
                            painter = painterResource(drawables.terminal),
                            contentDescription = null,
                            modifier = Modifier.padding(14.dp),
                        )
                    }

                    Text(
                        text = stringResource(strings.auto_setup_title),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = stringResource(strings.auto_setup_message),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    ElevatedCard(
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(18.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            val est = estimate
                            if (est == null) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text(
                                        stringResource(strings.auto_setup_checking_speed),
                                        style = MaterialTheme.typography.bodyMedium,
                                    )
                                }
                            } else {
                                InfoRow(stringResource(strings.auto_setup_storage), est.storageText)
                                InfoRow(stringResource(strings.auto_setup_data), est.dataText)
                                InfoRow(
                                    stringResource(strings.auto_setup_eta),
                                    est.etaText ?: stringResource(strings.auto_setup_eta_unknown),
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(
                        modifier = Modifier.weight(1f).height(48.dp),
                        onClick = { showDeclineWarning = true },
                    ) {
                        Text(stringResource(strings.auto_setup_skip), maxLines = 1)
                    }
                    Button(
                        modifier = Modifier.weight(1f).height(48.dp),
                        onClick = {
                            activity?.let { AutoSetup.launch(it) }
                            onLaunch()
                        },
                    ) {
                        Text(stringResource(strings.auto_setup_start), maxLines = 1)
                    }
                }
            }
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
    }
}
