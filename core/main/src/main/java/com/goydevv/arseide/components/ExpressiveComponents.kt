package com.goydevv.arseide.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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

@Composable
fun ExpressiveSurface(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(24.dp),
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainer,
    tonalElevation: Dp = 1.dp,
    shadowElevation: Dp = 0.dp,
    border: BorderStroke? = null,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    content: @Composable BoxScope.() -> Unit,
) {
    Surface(
        modifier = modifier,
        shape = shape,
        color = containerColor,
        tonalElevation = tonalElevation,
        shadowElevation = shadowElevation,
        border = border,
    ) {
        Box(modifier = Modifier.padding(contentPadding), content = content)
    }
}

@Composable
fun ExpressiveCard(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(24.dp),
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
            animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
            label = "expressiveCardScale",
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
                            enabled = enabled,
                            onClick = onClick,
                        )
                    } else {
                        Modifier
                    }
                ),
        shape = shape,
        color = if (selected) scheme.secondaryContainer else scheme.surfaceContainerLow,
        tonalElevation = if (selected) 2.dp else 0.dp,
        border = if (selected) BorderStroke(1.5.dp, scheme.primary) else null,
    ) {
        Box(modifier = Modifier.padding(contentPadding), content = content)
    }
}

data class ExpressiveNavDestination(
    val id: String,
    val label: String,
    val iconRes: Int? = null,
    val iconVector: ImageVector? = null,
    val customIcon: (@Composable () -> Unit)? = null,
    val selected: Boolean = false,
    val enabled: Boolean = true,
    val badgeCount: Int = 0,
    val onClick: () -> Unit,
    val onLongClick: (() -> Unit)? = null,
)

@Composable
fun ExpressiveBottomNavigationBar(
    items: List<ExpressiveNavDestination>,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    val scheme = MaterialTheme.colorScheme

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = scheme.surfaceContainer,
        contentColor = scheme.onSurface,
        tonalElevation = 2.dp,
    ) {
        Column(modifier = Modifier.fillMaxWidth().navigationBarsPadding()) {
            Row(
                modifier =
                    Modifier.fillMaxWidth()
                        .height(if (compact) 64.dp else 74.dp)
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                items.forEach { item ->
                    val interactionSource = remember { MutableInteractionSource() }
                    val isPressed by interactionSource.collectIsPressedAsState()
                    val itemScale by
                        animateFloatAsState(
                            targetValue = if (isPressed) 0.92f else 1f,
                            animationSpec =
                                spring(
                                    dampingRatio = Spring.DampingRatioMediumBouncy,
                                    stiffness = Spring.StiffnessMedium,
                                ),
                            label = "navScale_${item.id}",
                        )
                    val pillWidth by
                        animateDpAsState(
                            targetValue = if (item.selected) 64.dp else 32.dp,
                            animationSpec =
                                spring(
                                    dampingRatio = Spring.DampingRatioMediumBouncy,
                                    stiffness = Spring.StiffnessMediumLow,
                                ),
                            label = "navPill_${item.id}",
                        )
                    val pillColor by
                        animateColorAsState(
                            targetValue = if (item.selected) scheme.secondaryContainer else Color.Transparent,
                            animationSpec = tween(180),
                            label = "navPillColor_${item.id}",
                        )
                    val iconTint by
                        animateColorAsState(
                            targetValue =
                                if (item.selected) scheme.onSecondaryContainer else scheme.onSurfaceVariant,
                            animationSpec = tween(180),
                            label = "navIcon_${item.id}",
                        )
                    val labelColor by
                        animateColorAsState(
                            targetValue = if (item.selected) scheme.onSurface else scheme.onSurfaceVariant,
                            animationSpec = tween(180),
                            label = "navLabel_${item.id}",
                        )

                    Column(
                        modifier =
                            Modifier.weight(1f)
                                .graphicsLayer {
                                    scaleX = itemScale
                                    scaleY = itemScale
                                }
                                .clip(RoundedCornerShape(18.dp))
                                .combinedClickable(
                                    interactionSource = interactionSource,
                                    indication = null,
                                    enabled = item.enabled,
                                    onClick = item.onClick,
                                    onLongClick = item.onLongClick,
                                )
                                .padding(vertical = 2.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Box(
                            modifier =
                                Modifier.width(pillWidth)
                                    .height(32.dp)
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(pillColor),
                            contentAlignment = Alignment.Center,
                        ) {
                            BadgedBox(
                                badge = {
                                    if (item.badgeCount > 0) {
                                        Badge(
                                            containerColor = scheme.error,
                                            contentColor = scheme.onError,
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
                                            tint = iconTint,
                                            modifier = Modifier.size(22.dp),
                                        )
                                    item.iconVector != null ->
                                        Icon(
                                            imageVector = item.iconVector,
                                            contentDescription = item.label,
                                            tint = iconTint,
                                            modifier = Modifier.size(22.dp),
                                        )
                                }
                            }
                        }

                        Spacer(Modifier.height(4.dp))

                        Text(
                            text = item.label,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = if (item.selected) FontWeight.Bold else FontWeight.Medium,
                            color = labelColor,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ExpressiveSegmentedControl(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    badges: Map<Int, Int> = emptyMap(),
) {
    val scheme = MaterialTheme.colorScheme

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(50),
        color = scheme.surfaceContainerHigh,
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
                            .clip(RoundedCornerShape(50))
                            .clickable { onSelect(index) },
                    color = bg,
                    shape = RoundedCornerShape(50),
                ) {
                    Row(
                        modifier = Modifier.padding(vertical = 9.dp, horizontal = 8.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
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
