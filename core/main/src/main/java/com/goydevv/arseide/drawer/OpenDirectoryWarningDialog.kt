package com.goydevv.arseide.drawer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.goydevv.arseide.resources.strings
import com.goydevv.arseide.settings.Settings

@Composable
fun OpenDirectoryWarningDialog(onConfirm: () -> Unit, onCancel: () -> Unit) {
    var neverAgain by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(stringResource(strings.open_dir_warning_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = stringResource(strings.open_dir_warning_msg),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = neverAgain, onCheckedChange = { neverAgain = it })
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(strings.never_show_again), style = MaterialTheme.typography.bodyMedium)
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (neverAgain) Settings.open_dir_warning_dismissed = true
                    onConfirm()
                },
            ) {
                Text(stringResource(strings.ok))
            }
        },
        dismissButton = { TextButton(onClick = onCancel) { Text(stringResource(strings.cancel)) } },
    )
}
