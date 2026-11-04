package com.rk.components

import android.os.Build
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
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
import com.rk.theme.LocalGlassColors

/**
 * Applies a hardware-accelerated RenderEffect blur on Android 12+ (API 31+) when [radius] > 0.dp.
 * On older API levels it safely no-ops and relies on the high-translucency frosted surface layers.
 */
fun Modifier.backdropBlur(radius: Dp): Modifier =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && radius > 0.dp) {
        this.blur(radius = radius)
    } else {
        this
    }

/**
 * A multi-layered frosted glass surface following 2026 Liquid Glass design principles:
 * 1. Translucent gradient fill derived from the active theme's [LocalGlassColors].
 * 2. Specular top-left -> bottom-right hairline rim border that catches light.
 * 3. Subtle top luminance highlight + layered ambient drop shadow.
 */
@Composable
fun GlassSurface(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(22.dp),
    tonalAlpha: Float = 1f,
    shadowElevation: Dp = 10.dp,
    accentGlow: Color? = null,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    content: @Composable BoxScope.() -> Unit,
) {
    val glass = LocalGlassColors.current
    val scheme = MaterialTheme.colorScheme

    val topFill = glass.surfaceTop.copy(alpha = (glass.surfaceTop.alpha * tonalAlpha).coerceIn(0f, 0.98f))
    val bottomFill = glass.surfaceBottom.copy(alpha = (glass.surfaceBottom.alpha * tonalAlpha).coerceIn(0f, 0.98f))

    val rimStart = accentGlow?.copy(alpha = 0.52f) ?: glass.borderTop
    val rimMid = glass.borderMid
    val rimEnd = glass.borderBottom

    val spotColor = accentGlow ?: scheme.primary.copy(alpha = 0.28f)

    Box(
        modifier =
            modifier
                .shadow(
                    elevation = shadowElevation,
                    shape = shape,
                    ambientColor = Color.Black.copy(alpha = 0.35f),
                    spotColor = spotColor,
                )
                .clip(shape)
                .background(
                    brush =
                        Brush.verticalGradient(
                            colors = listOf(topFill, bottomFill),
                        ),
                    shape = shape,
                )
                .border(
                    width = 1.dp,
                    brush =
                        Brush.linearGradient(
                            colors = listOf(rimStart, rimMid, rimEnd),
                            start = Offset(0f, 0f),
                            end = Offset(600f, 600f),
                        ),
                    shape = shape,
                ),
    ) {
        // Specular top sheen overlay
        Box(
            modifier =
                Modifier.fillMaxWidth()
                    .height(24.dp)
                    .align(Alignment.TopCenter)
                    .background(
                        brush =
                            Brush.verticalGradient(
                                colors =
                                    listOf(
                                        (accentGlow ?: glass.specularHighlight).copy(alpha = 0.12f),
                                        Color.Transparent,
                                    )
                            )
                    )
        )
        Box(modifier = Modifier.padding(contentPadding), content = content)
    }
}

/**
 * Interactive glassmorphic card with spring press feedback (`scale: 0.97f`) and subtle rim illumination.
 */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(18.dp),
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
            targetValue = if (isPressed && enabled) 0.97f else 1f,
            animationSpec = spring(dampingRatio = 0.65f, stiffness = Spring.StiffnessMedium),
            label = "glassCardScale",
        )
    val scheme = MaterialTheme.colorScheme
    val accent = if (selected) scheme.primary else null

    GlassSurface(
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
        shadowElevation = if (selected) 8.dp else 3.dp,
        accentGlow = accent,
        contentPadding = contentPadding,
        content = content,
    )
}

/**
 * Data model for an item in the [GlassBottomNavigationBar].
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
 * Floating Glassmorphic Bottom Navigation Bar with specular rim, translucent frost,
 * spring-physics press & selection transitions, and optional badge counters.
 */
@Composable
fun GlassBottomNavigationBar(
    items: List<GlassNavDestination>,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    val glass = LocalGlassColors.current
    val scheme = MaterialTheme.colorScheme

    GlassSurface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(26.dp),
        shadowElevation = 14.dp,
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = if (compact) 6.dp else 8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            items.forEach { item ->
                GlassBottomNavButton(
                    item = item,
                    compact = compact,
                    activePillColor = glass.activePillContainer,
                    activeContentColor = glass.activePillContent,
                    inactiveContentColor = scheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun GlassBottomNavButton(
    item: GlassNavDestination,
    compact: Boolean,
    activePillColor: Color,
    activeContentColor: Color,
    inactiveContentColor: Color,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scheme = MaterialTheme.colorScheme

    val scale by
        animateFloatAsState(
            targetValue =
                when {
                    !item.enabled -> 1f
                    isPressed -> 0.91f
                    item.selected -> 1.03f
                    else -> 1f
                },
            animationSpec = spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMedium),
            label = "navItemScale",
        )

    val pillBg by
        animateColorAsState(
            targetValue =
                when {
                    item.isAccentAction -> scheme.primaryContainer.copy(alpha = 0.85f)
                    item.selected -> activePillColor
                    else -> Color.Transparent
                },
            animationSpec = tween(durationMillis = 200),
            label = "navPillBg",
        )

    val contentColor by
        animateColorAsState(
            targetValue =
                when {
                    !item.enabled -> inactiveContentColor.copy(alpha = 0.38f)
                    item.isAccentAction -> scheme.onPrimaryContainer
                    item.selected -> activeContentColor
                    else -> inactiveContentColor
                },
            animationSpec = tween(durationMillis = 180),
            label = "navContentColor",
        )

    val pillHorizontalPadding by
        animateDpAsState(
            targetValue = if (item.selected || item.isAccentAction) 12.dp else 6.dp,
            animationSpec = spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessMedium),
            label = "navPillPadding",
        )

    val pillBorder =
        if (item.selected || item.isAccentAction) {
            BorderStroke(
                width = 1.dp,
                color = scheme.primary.copy(alpha = if (item.selected) 0.38f else 0.25f),
            )
        } else {
            null
        }

    Column(
        modifier =
            modifier
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }
                .clip(RoundedCornerShape(18.dp))
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    enabled = item.enabled,
                    onClick = item.onClick,
                )
                .padding(vertical = 2.dp, horizontal = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Surface(
            color = pillBg,
            shape = RoundedCornerShape(16.dp),
            border = pillBorder,
        ) {
            Box(
                modifier =
                    Modifier.padding(
                        horizontal = pillHorizontalPadding,
                        vertical = if (compact) 4.dp else 5.dp,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                CompositionLocalProvider(LocalContentColor provides contentColor) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        when {
                            item.customIcon != null -> item.customIcon.invoke()
                            item.iconRes != null ->
                                Icon(
                                    painter = painterResource(item.iconRes),
                                    contentDescription = item.label,
                                    tint = contentColor,
                                    modifier = Modifier.size(if (compact) 18.dp else 20.dp),
                                )
                            item.iconVector != null ->
                                Icon(
                                    imageVector = item.iconVector,
                                    contentDescription = item.label,
                                    tint = contentColor,
                                    modifier = Modifier.size(if (compact) 18.dp else 20.dp),
                                )
                        }

                        if (item.badgeCount > 0) {
                            Spacer(Modifier.width(4.dp))
                            Surface(
                                color = scheme.primary,
                                contentColor = scheme.onPrimary,
                                shape = CircleShape,
                            ) {
                                Text(
                                    text = if (item.badgeCount > 99) "99+" else item.badgeCount.toString(),
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

        Spacer(Modifier.height(2.dp))

        Text(
            text = item.label,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = if (compact) 10.sp else 11.sp),
            fontWeight = if (item.selected || item.isAccentAction) FontWeight.SemiBold else FontWeight.Medium,
            color = contentColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * Glassmorphic segmented pill selector (used in GitTab for Code / Diff / Actions and similar tab switches).
 */
@Composable
fun GlassSegmentedControl(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    badges: Map<Int, Int> = emptyMap(),
) {
    val glass = LocalGlassColors.current
    val scheme = MaterialTheme.colorScheme

    GlassSurface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        shadowElevation = 2.dp,
        contentPadding = PaddingValues(4.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            options.forEachIndexed { index, title ->
                val isSelected = selectedIndex == index
                val interactionSource = remember { MutableInteractionSource() }
                val isPressed by interactionSource.collectIsPressedAsState()
                val scale by
                    animateFloatAsState(
                        targetValue = if (isPressed) 0.95f else 1f,
                        animationSpec = spring(dampingRatio = 0.65f, stiffness = Spring.StiffnessMedium),
                        label = "segScale",
                    )
                val bg by
                    animateColorAsState(
                        targetValue = if (isSelected) glass.activePillContainer else Color.Transparent,
                        animationSpec = tween(180),
                        label = "segBg",
                    )
                val fg by
                    animateColorAsState(
                        targetValue = if (isSelected) glass.activePillContent else scheme.onSurfaceVariant,
                        animationSpec = tween(180),
                        label = "segFg",
                    )

                Surface(
                    modifier =
                        Modifier.weight(1f)
                            .graphicsLayer {
                                scaleX = scale
                                scaleY = scale
                            }
                            .clip(RoundedCornerShape(12.dp))
                            .clickable(
                                interactionSource = interactionSource,
                                indication = null,
                                onClick = { onSelect(index) },
                            ),
                    color = bg,
                    shape = RoundedCornerShape(12.dp),
                    border =
                        if (isSelected) BorderStroke(1.dp, scheme.primary.copy(alpha = 0.35f)) else null,
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
