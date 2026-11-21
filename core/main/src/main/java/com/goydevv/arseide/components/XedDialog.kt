package com.goydevv.arseide.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

@Composable
fun XedDialog(
    modifier: Modifier = Modifier,
    onDismissRequest: () -> Unit,
    dialogProperties: DialogProperties =
        DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    content: @Composable () -> Unit,
) {
    val config = LocalConfiguration.current

    Dialog(onDismissRequest = onDismissRequest, properties = dialogProperties) {
        Surface(
            modifier =
                modifier
                    .padding(horizontal = 16.dp, vertical = 32.dp)
                    .width((config.screenWidthDp / 1.16).dp),
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 6.dp,
            shadowElevation = 6.dp,
        ) {
            Box(contentAlignment = Alignment.Center) {
                content()
            }
        }
    }
}
