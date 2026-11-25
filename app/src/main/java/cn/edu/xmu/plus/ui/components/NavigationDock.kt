package cn.edu.xmu.plus.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Assignment
import androidx.compose.material.icons.automirrored.rounded.Assignment
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.rounded.AutoStories
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import cn.edu.xmu.plus.data.DockStyle
import dev.chrisbanes.haze.HazeInputScale
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect
import kotlin.math.abs
import kotlin.math.roundToInt

enum class MainDestination(
    val route: String,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
) {
    HOME("home", "首页", Icons.Rounded.Home, Icons.Outlined.Home),
    HOMEWORK(
        "homework",
        "作业",
        Icons.AutoMirrored.Rounded.Assignment,
        Icons.AutoMirrored.Outlined.Assignment,
    ),
    COURSES("courses", "课程", Icons.Rounded.AutoStories, Icons.Outlined.AutoStories),
    PROFILE("profile", "我的", Icons.Rounded.Person, Icons.Outlined.Person),
}

@Composable
fun ExpressiveFloatingNavBar(
    currentRoute: String?,
    dockStyle: DockStyle,
    hazeState: HazeState,
    onNavigate: (MainDestination) -> Unit,
    modifier: Modifier = Modifier,
) {
    val destinations = MainDestination.entries
    val selectedIndex = destinations.indexOfFirst { it.route == currentRoute }.coerceAtLeast(0)
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val bg = MaterialTheme.colorScheme.background
    val haptic = LocalHapticFeedback.current
    val hapticsEnabled by rememberUpdatedState(LocalHapticsEnabled.current)
    val currentOnNavigate by rememberUpdatedState(onNavigate)
    val touchSlop = LocalViewConfiguration.current.touchSlop
    val density = LocalDensity.current

    val wrapperModifier = if (dockStyle == DockStyle.CLASSIC) {
        modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color.Transparent,
                        bg.copy(alpha = 0.78f),
                        bg.copy(alpha = 0.96f),
                    ),
                ),
            )
            .navigationBarsPadding()
            .padding(horizontal = 26.dp, vertical = 10.dp)
    } else {
        modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 26.dp, vertical = 10.dp)
    }

    val dockShape = RoundedCornerShape(28.dp)
    val dockModifier = if (dockStyle == DockStyle.LIQUID_GLASS) {
        Modifier
            .fillMaxWidth()
            .height(56.dp)
            .shadow(elevation = 14.dp, shape = dockShape, clip = false)
            .clip(dockShape)
            .hazeEffect(
                state = hazeState,
                style = HazeStyle(
                    backgroundColor = MaterialTheme.colorScheme.surface,
                    tint = HazeTint(
                        if (isDark) {
                            MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.40f)
                        } else {
                            Color.White.copy(alpha = 0.48f)
                        },
                    ),
                    blurRadius = 24.dp,
                    noiseFactor = 0.03f,
                ),
            ) {
                inputScale = HazeInputScale.Auto
            }
            .border(
                width = 0.9.dp,
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color.White.copy(alpha = if (isDark) 0.28f else 0.94f),
                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.28f),
                    ),
                ),
                shape = dockShape,
            )
    } else {
        Modifier
            .fillMaxWidth()
            .height(56.dp)
            .shadow(elevation = 12.dp, shape = dockShape, clip = false)
            .clip(dockShape)
            .background(
                if (isDark) {
                    MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.92f)
                } else {
                    MaterialTheme.colorScheme.surfaceContainerLowest.copy(alpha = 0.95f)
                },
            )
            .border(
                width = 0.8.dp,
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color.White.copy(alpha = if (isDark) 0.16f else 0.92f),
                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.24f),
                    ),
                ),
                shape = dockShape,
            )
    }

    Box(
        modifier = wrapperModifier,
        contentAlignment = Alignment.Center,
    ) {
        Box(modifier = dockModifier) {
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(4.dp),
            ) {
                val slotWidthDp = maxWidth / destinations.size
                val slotWidthPx = with(density) { slotWidthDp.toPx() }
                val totalWidthPx = with(density) { maxWidth.toPx() }

                var isInteracting by remember { mutableStateOf(false) }
                var isDragging by remember { mutableStateOf(false) }
                var hoveredIndex by remember { mutableStateOf<Int?>(null) }
                var dragOffsetPx by remember { mutableFloatStateOf(0f) }

                val visualIndex = hoveredIndex ?: selectedIndex

                val targetSpringOffsetPx by animateFloatAsState(
                    targetValue = slotWidthPx * visualIndex,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioNoBouncy,
                        stiffness = 780f,
                    ),
                    label = "dockSpringPx",
                )

                val activeOffsetPx = if (isDragging) dragOffsetPx else targetSpringOffsetPx

                val lensScaleX by animateFloatAsState(
                    targetValue = if (isInteracting) 1.015f else 1f,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioNoBouncy,
                        stiffness = 860f,
                    ),
                    label = "lensScaleX",
                )
                val lensScaleY by animateFloatAsState(
                    targetValue = if (isInteracting) 1.015f else 1f,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioNoBouncy,
                        stiffness = 860f,
                    ),
                    label = "lensScaleY",
                )

                val pillShape = RoundedCornerShape(26.dp)
                val pillModifier = if (dockStyle == DockStyle.LIQUID_GLASS) {
                    Modifier
                        .offset { IntOffset(activeOffsetPx.roundToInt(), 0) }
                        .width(slotWidthDp)
                        .fillMaxHeight()
                        .graphicsLayer {
                            scaleX = lensScaleX
                            scaleY = lensScaleY
                        }
                        .shadow(elevation = if (isInteracting) 8.dp else 3.dp, shape = pillShape, clip = false)
                        .clip(pillShape)
                        .background(
                            if (isDark) {
                                Color.White.copy(alpha = 0.19f)
                            } else {
                                Color.White.copy(alpha = 0.84f)
                            },
                        )
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
                        .border(
                            width = 0.9.dp,
                            brush = Brush.verticalGradient(
                                colors = listOf(
                                    Color.White.copy(alpha = if (isDark) 0.48f else 0.98f),
                                    Color.White.copy(alpha = 0.10f),
                                ),
                            ),
                            shape = pillShape,
                        )
                } else {
                    Modifier
                        .offset { IntOffset(activeOffsetPx.roundToInt(), 0) }
                        .width(slotWidthDp)
                        .fillMaxHeight()
                        .graphicsLayer {
                            scaleX = lensScaleX
                            scaleY = lensScaleY
                        }
                        .clip(pillShape)
                        .background(MaterialTheme.colorScheme.primaryContainer)
                }

                Box(modifier = pillModifier)

                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(destinations.size, slotWidthPx, totalWidthPx) {
                            awaitEachGesture {
                                val down = awaitFirstDown(requireUnconsumed = false)
                                val downX = down.position.x
                                isInteracting = true
                                var dragged = false
                                var lastHoveredIdx = ((downX / slotWidthPx).toInt())
                                    .coerceIn(0, destinations.lastIndex)

                                try {
                                    while (true) {
                                        val event = awaitPointerEvent()
                                        val change = event.changes.firstOrNull { it.id == down.id }
                                            ?: event.changes.firstOrNull()
                                            ?: break
                                        if (!change.pressed) {
                                            val finalIdx = ((change.position.x / slotWidthPx).toInt())
                                                .coerceIn(0, destinations.lastIndex)
                                            if (hapticsEnabled) {
                                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            }
                                            currentOnNavigate(destinations[finalIdx])
                                            break
                                        }

                                        val currentX = change.position.x
                                        if (!dragged && abs(currentX - downX) > touchSlop) {
                                            dragged = true
                                            isDragging = true
                                            hoveredIndex = lastHoveredIdx
                                            if (hapticsEnabled) {
                                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            }
                                        }

                                        if (dragged) {
                                            change.consume()
                                            val maxDrag = (totalWidthPx - slotWidthPx).coerceAtLeast(0f)
                                            val rawTarget = currentX - slotWidthPx / 2f
                                            dragOffsetPx = when {
                                                rawTarget < 0f -> (rawTarget * 0.22f).coerceAtLeast(-10f)
                                                rawTarget > maxDrag -> maxDrag + ((rawTarget - maxDrag) * 0.22f).coerceAtMost(10f)
                                                else -> rawTarget
                                            }
                                            val idx = ((currentX / slotWidthPx).toInt())
                                                .coerceIn(0, destinations.lastIndex)
                                            if (idx != lastHoveredIdx) {
                                                lastHoveredIdx = idx
                                                hoveredIndex = idx
                                                if (hapticsEnabled) {
                                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                }
                                            }
                                        }
                                    }
                                } finally {
                                    isDragging = false
                                    isInteracting = false
                                    hoveredIndex = null
                                }
                            }
                        },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    destinations.forEachIndexed { index, dest ->
                        val isActiveVisual = index == visualIndex
                        val contentColor by animateColorAsState(
                            targetValue = if (isActiveVisual) {
                                if (dockStyle == DockStyle.LIQUID_GLASS) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onPrimaryContainer
                                }
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            animationSpec = spring(stiffness = Spring.StiffnessMedium),
                            label = "dockContentColor",
                        )
                        val iconScale by animateFloatAsState(
                            targetValue = if (isActiveVisual) 1.04f else 1.0f,
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioNoBouncy,
                                stiffness = 820f,
                            ),
                            label = "dockIconScale",
                        )

                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                        ) {
                            Icon(
                                imageVector = if (isActiveVisual) dest.selectedIcon else dest.unselectedIcon,
                                contentDescription = dest.label,
                                tint = contentColor,
                                modifier = Modifier
                                    .size(19.dp)
                                    .graphicsLayer {
                                        scaleX = iconScale
                                        scaleY = iconScale
                                    },
                            )
                            Spacer(modifier = Modifier.height(1.dp))
                            Text(
                                text = dest.label,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = if (isActiveVisual) FontWeight.SemiBold else FontWeight.Medium,
                                ),
                                color = contentColor,
                                maxLines = 1,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ExpressiveTabletNavigationRail(
    currentRoute: String?,
    dockStyle: DockStyle,
    hazeState: HazeState,
    onNavigate: (MainDestination) -> Unit,
    modifier: Modifier = Modifier,
) {
    val destinations = MainDestination.entries
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val railShape = RoundedCornerShape(28.dp)

    val containerModifier = if (dockStyle == DockStyle.LIQUID_GLASS) {
        Modifier
            .width(86.dp)
            .fillMaxHeight()
            .clip(railShape)
            .hazeEffect(
                state = hazeState,
                style = HazeStyle(
                    backgroundColor = MaterialTheme.colorScheme.surface,
                    tint = HazeTint(
                        if (isDark) {
                            MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.42f)
                        } else {
                            Color.White.copy(alpha = 0.52f)
                        },
                    ),
                    blurRadius = 20.dp,
                    noiseFactor = 0.04f,
                ),
            ) {
                inputScale = HazeInputScale.Auto
            }
            .border(
                width = 1.dp,
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color.White.copy(alpha = if (isDark) 0.28f else 0.90f),
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.22f),
                    ),
                ),
                shape = railShape,
            )
    } else {
        Modifier
            .width(86.dp)
            .fillMaxHeight()
            .clip(railShape)
            .background(
                if (isDark) {
                    MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.94f)
                } else {
                    MaterialTheme.colorScheme.surfaceContainerLowest.copy(alpha = 0.96f)
                },
            )
            .border(
                width = 0.75.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.22f),
                shape = railShape,
            )
    }

    Box(
        modifier = modifier
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(start = 14.dp, top = 12.dp, bottom = 12.dp),
    ) {
        Column(
            modifier = containerModifier
                .verticalScroll(rememberScrollState())
                .padding(vertical = 12.dp, horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "X+",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
            Spacer(modifier = Modifier.height(2.dp))
            destinations.forEach { dest ->
                val isSelected = currentRoute == dest.route
                val itemBg by animateColorAsState(
                    targetValue = if (isSelected) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        Color.Transparent
                    },
                    label = "railBg",
                )
                val itemFg by animateColorAsState(
                    targetValue = if (isSelected) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    label = "railFg",
                )
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(itemBg)
                        .bounceClickable(pressedScale = 0.94f) { onNavigate(dest) }
                        .padding(vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(
                        imageVector = if (isSelected) dest.selectedIcon else dest.unselectedIcon,
                        contentDescription = dest.label,
                        tint = itemFg,
                        modifier = Modifier.size(22.dp),
                    )
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = dest.label,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        ),
                        color = itemFg,
                    )
                }
            }
        }
    }
}
