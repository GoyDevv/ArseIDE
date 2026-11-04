package com.rk.components

import android.content.res.Resources
import androidx.annotation.DrawableRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.rk.components.compose.utils.addIf
import com.rk.icons.Icon
import com.rk.icons.rememberSvgImageLoader
import java.io.File

@Composable
fun AddDialogItem(
    @DrawableRes resId: Int,
    title: String,
    description: String? = null,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    AddDialogItem(
        title = title,
        description = description,
        onClick = onClick,
        enabled = enabled,
        icon = {
            Icon(
                painter = painterResource(resId),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(22.dp),
            )
        },
    )
}

@Composable
fun AddDialogItem(
    @DrawableRes resId: Int,
    resources: Resources,
    title: String,
    description: String? = null,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    AddDialogItem(
        title = title,
        description = description,
        onClick = onClick,
        enabled = enabled,
        icon = {
            val theme = LocalContext.current.theme

            Icon(
                imageVector = ImageVector.vectorResource(theme = theme, resId = resId, res = resources),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(22.dp),
            )
        },
    )
}

@Composable
fun AddDialogItem(
    icon: ImageVector,
    title: String,
    description: String? = null,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    AddDialogItem(
        title = title,
        description = description,
        onClick = onClick,
        enabled = enabled,
        icon = {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(22.dp),
            )
        },
    )
}

@Composable
fun AddDialogItem(
    svgFile: File,
    title: String,
    description: String? = null,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    AddDialogItem(
        title = title,
        description = description,
        onClick = onClick,
        enabled = enabled,
        icon = {
            AsyncImage(
                model = svgFile,
                imageLoader = rememberSvgImageLoader(),
                contentDescription = null,
                modifier = Modifier.size(22.dp),
            )
        },
    )
}

@Composable
fun AddDialogItem(
    icon: Icon,
    title: String,
    description: String? = null,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    when (icon) {
        is Icon.ResourceIcon -> {
            AddDialogItem(
                resId = icon.drawableRes,
                title = title,
                description = description,
                onClick = onClick,
                enabled = enabled,
            )
        }

        is Icon.ExternalResourceIcon -> {
            AddDialogItem(
                resId = icon.drawableRes,
                resources = icon.resources,
                title = title,
                description = description,
                onClick = onClick,
                enabled = enabled,
            )
        }

        is Icon.VectorIcon -> {
            AddDialogItem(
                icon = icon.vector,
                title = title,
                description = description,
                onClick = onClick,
                enabled = enabled,
            )
        }

        is Icon.SvgIcon -> {
            AddDialogItem(
                svgFile = icon.file,
                title = title,
                description = description,
                onClick = onClick,
                enabled = enabled,
            )
        }

        else -> throw IllegalArgumentException("Text icons are not supported in dialog items.")
    }
}

@Composable
fun AddDialogItem(
    icon: @Composable () -> Unit,
    title: String,
    description: String? = null,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Row(
        modifier =
            Modifier.fillMaxWidth()
                .padding(vertical = 2.dp)
                .clip(RoundedCornerShape(14.dp))
                .clickable(enabled = enabled, onClick = onClick)
                .padding(horizontal = 12.dp, vertical = 10.dp)
                .addIf(!enabled) { alpha(0.5f) },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
            modifier = Modifier.size(40.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                icon()
            }
        }

        Spacer(Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            if (description != null) {
                Text(
                    description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
