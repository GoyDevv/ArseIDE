package com.goydevv.arseide.settings.extension

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.goydevv.arseide.App
import com.goydevv.arseide.components.compose.preferences.base.PreferenceLayout
import com.goydevv.arseide.extension.Extension
import com.goydevv.arseide.resources.strings

@Composable
fun ExtensionSettings(extension: Extension?) {
    val api = App.extensionManager.loadedExtensions[extension]?.api

    PreferenceLayout(label = extension?.name ?: stringResource(strings.ext_not_found)) {
        if (extension == null || api == null) {
            Text(stringResource(strings.ext_not_found_desc), modifier = Modifier.padding(horizontal = 16.dp))
        } else {
            api.SettingsContent()
        }
    }
}
