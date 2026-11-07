package com.goydevv.arseide.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DrawerDefaults
import androidx.compose.material3.DrawerState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.PermanentDrawerSheet
import androidx.compose.material3.PermanentNavigationDrawer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.goydevv.arseide.settings.Settings

/**
 * When true the modal drawer (file tree / git / etc.) is maximized to the full screen width.
 * Toggled by the expand button in the drawer's top glass header. Read by [getDrawerWidth] so every
 * width-dependent piece of the drawer stays in sync.
 */
var isDrawerExpanded by mutableStateOf(false)

@Composable
fun getDrawerWidth(): Dp {
    val density = LocalDensity.current
    val widthPx = LocalWindowInfo.current.containerSize.width
    val targetFraction = if (isDrawerExpanded) 1f else 0.86f
    val fraction by
        animateFloatAsState(
            targetValue = targetFraction,
            animationSpec = spring(dampingRatio = 0.88f, stiffness = Spring.StiffnessMediumLow),
            label = "drawerWidthFraction",
        )
    return with(density) { (widthPx * fraction).toDp() }
}

var isPermanentDrawer by mutableStateOf(false)
    private set

@Composable
fun ResponsiveDrawer(
    drawerState: DrawerState,
    fullscreen: Boolean,
    mainContent: @Composable () -> Unit,
    sheetContent: @Composable ColumnScope.() -> Unit,
) {
    val density = LocalDensity.current
    val widthPx = LocalWindowInfo.current.containerSize.width
    val screenWidthDp = with(density) { widthPx.toDp() }
    isPermanentDrawer =
        remember(screenWidthDp, Settings.desktop_mode) {
            Settings.desktop_mode && screenWidthDp >= 840.dp
        }

    if (isPermanentDrawer) {
        PermanentNavigationDrawer(
            content = mainContent,
            modifier = Modifier.imePadding().systemBarsPadding(),
            drawerContent = {
                PermanentDrawerSheet(
                    windowInsets = if (fullscreen) WindowInsets() else DrawerDefaults.windowInsets,
                    drawerShape = RectangleShape,
                    drawerContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
                    content = sheetContent,
                )
            },
        )
    } else {
        val sheetShape =
            if (isDrawerExpanded) RectangleShape
            else RoundedCornerShape(topEnd = 24.dp, bottomEnd = 24.dp)

        ModalNavigationDrawer(
            modifier = Modifier.imePadding().systemBarsPadding(),
            drawerState = drawerState,
            gesturesEnabled = drawerState.isOpen,
            scrimColor = MaterialTheme.colorScheme.scrim.copy(alpha = 0.52f),
            content = mainContent,
            drawerContent = {
                ModalDrawerSheet(
                    windowInsets = if (fullscreen) WindowInsets() else DrawerDefaults.windowInsets,
                    modifier = Modifier.width(getDrawerWidth()),
                    drawerShape = sheetShape,
                    drawerContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
                    content = sheetContent,
                )
            },
        )
    }
}
