package com.goydevv.arseide.components

import android.os.Build
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Hardware-accelerated RenderEffect blur on Android 12+ when [radius] > 0.dp.
 */
fun Modifier.backdropBlur(radius: Dp): Modifier =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && radius > 0.dp) {
        this.blur(radius = radius)
    } else {
        this
    }

/**
 * Clean Material You (Material 3) tonal surface container.
 * Replaces the old overdone specular/gradient glass with authentic Material 3 tonal hierarchy.
 */
@Composable
fun GlassSurface(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(20.dp),
    tonalAlpha: Float = 1f,
    shadowElevation: Dp = 2.dp,
    accentGlow: Color? = null,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    content: @Composable BoxScope.() -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val containerColor =
        if (accentGlow != null) {
            scheme.primaryContainer.copy(alpha = tonalAlpha.coerceIn(0f, 1f))
        } else {
            scheme.surfaceContainer.copy(alpha = tonalAlpha.coerceIn(0f, 1f))
        }
    val borderColor =
        accentGlow?.copy(alpha = 0.55f) ?: scheme.outlineVariant.copy(alpha = 0.45f)

    Surface(
        modifier = modifier,
        shape = shape,
        color = containerColor,
        tonalElevation = 2.dp,
        shadowElevation = shadowElevation.coerceAtMost(4.dp),
        border = BorderStroke(1.dp, borderColor),
    ) {
        Box(modifier = Modifier.padding(contentPadding), content = content)
    }
}

/**
 * Interactive Material You card with subtle spring press feedback and tonal border.
 */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(20.dp),
    selected: Boolean = false,
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    content: @Composable BoxScope.() -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by
        animateFloatAsState(
            targetValue = if (isPressed && enabled) 0.98f else 1f,
            animationSpec = spring(dampingRatio = 0.75f, stiffness = Spring.StiffnessMedium),
            label = "m3CardScale",
        )
    val scheme = MaterialTheme.colorScheme

    Surface(
        modifier =
            modifier
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    alpha = if (enabled) 1f else 0.5f
                }
                .then(
                    if (onClick != null) {
                        Modifier.clip(shape).clickable(
                            interactionSource = interactionSource,
                            indication = null,
                            enabled = enabled,
                            onClick = onClick,
                        )
                    } else {
                        Modifier
                    }
                ),
        shape = shape,
        color = if (selected) scheme.secondaryContainer else scheme.surfaceContainerLow,
        tonalElevation = if (selected) 4.dp else 1.dp,
        border =
            BorderStroke(
                width = if (selected) 1.5.dp else 1.dp,
                color = if (selected) scheme.primary else scheme.outlineVariant.copy(alpha = 0.4f),
            ),
    ) {
        Box(modifier = Modifier.padding(contentPadding), content = content)
    }
}

/**
 * Data model for an item in the Material You bottom navigation bar.
 */
data class GlassNavDestination(
    val id: String,
    val label: String,
    val iconRes: Int? = null,
    val iconVector: ImageVector? = null,
    val customIcon: (@Composable () -> Unit)? = null,
    val selected: Boolean = false,
    val enabled: Boolean = true,
    val badgeCount: Int = 0,
    val isAccentAction: Boolean = false,
    val onClick: () -> Unit,
    val onLongClick: (() -> Unit)? = null,
)

/**
 * Authentic Material You (Material 3) NavigationBar with active indicator pills and badges.
 */
@Composable
fun GlassBottomNavigationBar(
    items: List<GlassNavDestination>,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    val scheme = MaterialTheme.colorScheme

    NavigationBar(
        modifier = modifier.fillMaxWidth().height(if (compact) 64.dp else 74.dp),
        containerColor = scheme.surfaceContainer,
        contentColor = scheme.onSurface,
        tonalElevation = 3.dp,
    ) {
        items.forEach { item ->
            NavigationBarItem(
                selected = item.selected,
                enabled = item.enabled,
                onClick = item.onClick,
                icon = {
                    BadgedBox(
                        badge = {
                            if (item.badgeCount > 0) {
                                Badge(
                                    containerColor = scheme.primary,
                                    contentColor = scheme.onPrimary,
                                ) {
                                    Text(if (item.badgeCount > 99) "99+" else item.badgeCount.toString())
                                }
                            }
                        }
                    ) {
                        when {
                            item.customIcon != null -> item.customIcon.invoke()
                            item.iconRes != null ->
                                Icon(
                                    painter = painterResource(item.iconRes),
                                    contentDescription = item.label,
                                    modifier = Modifier.size(22.dp),
                                )
                            item.iconVector != null ->
                                Icon(
                                    imageVector = item.iconVector,
                                    contentDescription = item.label,
                                    modifier = Modifier.size(22.dp),
                                )
                        }
                    }
                },
                label = {
                    Text(
                        text = item.label,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = if (item.selected) FontWeight.SemiBold else FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                colors =
                    NavigationBarItemDefaults.colors(
                        selectedIconColor = scheme.onSecondaryContainer,
                        selectedTextColor = scheme.onSurface,
                        indicatorColor = scheme.secondaryContainer,
                        unselectedIconColor = scheme.onSurfaceVariant,
                        unselectedTextColor = scheme.onSurfaceVariant,
                    ),
            )
        }
    }
}

/**
 * Material You segmented selector for tab sub-views (e.g. Git Code / Diff / Actions).
 */
@Composable
fun GlassSegmentedControl(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    badges: Map<Int, Int> = emptyMap(),
) {
    val scheme = MaterialTheme.colorScheme

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = scheme.surfaceContainerHigh,
        border = BorderStroke(1.dp, scheme.outlineVariant.copy(alpha = 0.4f)),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            options.forEachIndexed { index, title ->
                val isSelected = selectedIndex == index
                val bg by
                    animateColorAsState(
                        targetValue = if (isSelected) scheme.secondaryContainer else Color.Transparent,
                        animationSpec = tween(180),
                        label = "segBg",
                    )
                val fg by
                    animateColorAsState(
                        targetValue = if (isSelected) scheme.onSecondaryContainer else scheme.onSurfaceVariant,
                        animationSpec = tween(180),
                        label = "segFg",
                    )

                Surface(
                    modifier =
                        Modifier.weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { onSelect(index) },
                    color = bg,
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Row(
                        modifier = Modifier.padding(vertical = 8.dp, horizontal = 8.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                            color = fg,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        val count = badges[index] ?: 0
                        if (count > 0) {
                            Spacer(Modifier.width(6.dp))
                            Surface(
                                color = if (isSelected) scheme.primary else scheme.surfaceContainerHighest,
                                contentColor = if (isSelected) scheme.onPrimary else scheme.onSurfaceVariant,
                                shape = CircleShape,
                            ) {
                                Text(
                                    text = count.toString(),
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
