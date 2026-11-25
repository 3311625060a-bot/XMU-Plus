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
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
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

private enum class DockAxis { HORIZONTAL, VERTICAL }

/**
 * Shared container surface for both Phone bottom dock and Tablet side rail:
 * unifies Haze liquid-glass refraction, classic solid tint, elevation, and hairline border.
 */
private fun Modifier.dockContainerSurface(
    dockStyle: DockStyle,
    hazeState: HazeState,
    shape: Shape,
    isDark: Boolean,
): Modifier = composed {
    if (dockStyle == DockStyle.LIQUID_GLASS) {
        this
            .shadow(elevation = 14.dp, shape = shape, clip = false)
            .clip(shape)
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
                shape = shape,
            )
    } else {
        this
            .shadow(elevation = 12.dp, shape = shape, clip = false)
            .clip(shape)
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
                shape = shape,
            )
    }
}

/**
 * Shared sliding indicator lens for both Phone bottom dock and Tablet side rail.
 */
private fun Modifier.dockActiveLensSurface(
    dockStyle: DockStyle,
    shape: Shape,
    isInteracting: Boolean,
    lensScale: Float,
    isDark: Boolean,
): Modifier = composed {
    val scaled = this.graphicsLayer {
        scaleX = lensScale
        scaleY = lensScale
    }
    if (dockStyle == DockStyle.LIQUID_GLASS) {
        scaled
            .clip(shape)
            .background(
                if (isDark) {
                    Color.White.copy(alpha = if (isInteracting) 0.24f else 0.19f)
                } else {
                    Color.White.copy(alpha = if (isInteracting) 0.92f else 0.84f)
                },
            )
            .background(MaterialTheme.colorScheme.primary.copy(alpha = if (isInteracting) 0.14f else 0.12f))
            .border(
                width = 0.9.dp,
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color.White.copy(alpha = if (isDark) 0.48f else 0.98f),
                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = if (isDark) 0.30f else 0.35f),
                    ),
                ),
                shape = shape,
            )
    } else {
        scaled
            .clip(shape)
            .background(MaterialTheme.colorScheme.primaryContainer)
    }
}

/**
 * Unified tap + finger-scrubbing gesture layer (Commit-on-Release) along X or Y axis.
 */
private fun Modifier.dockScrubGesture(
    axis: DockAxis,
    itemCount: Int,
    slotSizePx: Float,
    totalSizePx: Float,
    onInteractingChange: (Boolean) -> Unit,
    onDraggingChange: (Boolean) -> Unit,
    onHoveredIndexChange: (Int?) -> Unit,
    onDragOffsetChange: (Float) -> Unit,
    onCommitIndex: (Int) -> Unit,
): Modifier = composed {
    val haptic = LocalHapticFeedback.current
    val hapticsEnabled by rememberUpdatedState(LocalHapticsEnabled.current)
    val touchSlop = LocalViewConfiguration.current.touchSlop
    val latestOnCommit by rememberUpdatedState(onCommitIndex)

    this.pointerInput(axis, itemCount, slotSizePx, totalSizePx) {
        if (slotSizePx <= 0f || itemCount <= 0) return@pointerInput
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            val downCoord = if (axis == DockAxis.HORIZONTAL) down.position.x else down.position.y
            onInteractingChange(true)
            var dragged = false
            var lastHoveredIdx = (downCoord / slotSizePx).toInt().coerceIn(0, itemCount - 1)

            try {
                while (true) {
                    val event = awaitPointerEvent()
                    val change = event.changes.firstOrNull { it.id == down.id }
                        ?: event.changes.firstOrNull()
                        ?: break
                    val coord = if (axis == DockAxis.HORIZONTAL) change.position.x else change.position.y
                    if (!change.pressed) {
                        val finalIdx = (coord / slotSizePx).toInt().coerceIn(0, itemCount - 1)
                        if (hapticsEnabled) {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        }
                        latestOnCommit(finalIdx)
                        break
                    }

                    if (!dragged && abs(coord - downCoord) > touchSlop) {
                        dragged = true
                        onDraggingChange(true)
                        onHoveredIndexChange(lastHoveredIdx)
                        if (hapticsEnabled) {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        }
                    }

                    if (dragged) {
                        change.consume()
                        val maxDrag = (totalSizePx - slotSizePx).coerceAtLeast(0f)
                        val rawTarget = coord - slotSizePx / 2f
                        val clamped = when {
                            rawTarget < 0f -> (rawTarget * 0.22f).coerceAtLeast(-10f)
                            rawTarget > maxDrag -> maxDrag + ((rawTarget - maxDrag) * 0.22f).coerceAtMost(10f)
                            else -> rawTarget
                        }
                        onDragOffsetChange(clamped)
                        val idx = (coord / slotSizePx).toInt().coerceIn(0, itemCount - 1)
                        if (idx != lastHoveredIdx) {
                            lastHoveredIdx = idx
                            onHoveredIndexChange(idx)
                            if (hapticsEnabled) {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            }
                        }
                    }
                }
            } finally {
                onDraggingChange(false)
                onInteractingChange(false)
                onHoveredIndexChange(null)
            }
        }
    }
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

    Box(
        modifier = wrapperModifier,
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .dockContainerSurface(dockStyle, hazeState, dockShape, isDark),
        ) {
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

                val lensScale by animateFloatAsState(
                    targetValue = if (isInteracting) 1.015f else 1f,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioNoBouncy,
                        stiffness = 860f,
                    ),
                    label = "dockLensScale",
                )

                val pillShape = RoundedCornerShape(26.dp)
                Box(
                    modifier = Modifier
                        .offset { IntOffset(activeOffsetPx.roundToInt(), 0) }
                        .width(slotWidthDp)
                        .fillMaxHeight()
                        .dockActiveLensSurface(dockStyle, pillShape, isInteracting, lensScale, isDark),
                )

                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .dockScrubGesture(
                            axis = DockAxis.HORIZONTAL,
                            itemCount = destinations.size,
                            slotSizePx = slotWidthPx,
                            totalSizePx = totalWidthPx,
                            onInteractingChange = { isInteracting = it },
                            onDraggingChange = { isDragging = it },
                            onHoveredIndexChange = { hoveredIndex = it },
                            onDragOffsetChange = { dragOffsetPx = it },
                            onCommitIndex = { onNavigate(destinations[it]) },
                        ),
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
    val selectedIndex = destinations.indexOfFirst { it.route == currentRoute }.coerceAtLeast(0)
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val density = LocalDensity.current
    val railShape = RoundedCornerShape(28.dp)
    val slotHeightDp = 58.dp
    val trackHeightDp = slotHeightDp * destinations.size

    Box(
        modifier = modifier
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(start = 14.dp, top = 12.dp, bottom = 12.dp),
    ) {
        Column(
            modifier = Modifier
                .width(82.dp)
                .fillMaxHeight()
                .dockContainerSurface(dockStyle, hazeState, railShape, isDark)
                .verticalScroll(rememberScrollState())
                .padding(vertical = 12.dp, horizontal = 5.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
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

            Spacer(modifier = Modifier.height(12.dp))

            // Vertical sliding glass lens + scrubbing gesture track (matches bottom dock fluid motion)
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(trackHeightDp),
            ) {
                val resolvedSlotDp = maxHeight / destinations.size
                val slotHeightPx = with(density) { resolvedSlotDp.toPx() }
                val totalHeightPx = with(density) { maxHeight.toPx() }

                var isInteracting by remember { mutableStateOf(false) }
                var isDragging by remember { mutableStateOf(false) }
                var hoveredIndex by remember { mutableStateOf<Int?>(null) }
                var dragOffsetPx by remember { mutableFloatStateOf(0f) }

                val visualIndex = hoveredIndex ?: selectedIndex

                val targetSpringOffsetPx by animateFloatAsState(
                    targetValue = slotHeightPx * visualIndex,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioNoBouncy,
                        stiffness = 780f,
                    ),
                    label = "railSpringPx",
                )
                val activeOffsetPx = if (isDragging) dragOffsetPx else targetSpringOffsetPx

                val lensScale by animateFloatAsState(
                    targetValue = if (isInteracting) 1.02f else 1f,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioNoBouncy,
                        stiffness = 860f,
                    ),
                    label = "railLensScale",
                )

                val pillShape = RoundedCornerShape(22.dp)
                Box(
                    modifier = Modifier
                        .offset { IntOffset(0, activeOffsetPx.roundToInt()) }
                        .fillMaxWidth()
                        .height(resolvedSlotDp)
                        .dockActiveLensSurface(dockStyle, pillShape, isInteracting, lensScale, isDark),
                )

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .dockScrubGesture(
                            axis = DockAxis.VERTICAL,
                            itemCount = destinations.size,
                            slotSizePx = slotHeightPx,
                            totalSizePx = totalHeightPx,
                            onInteractingChange = { isInteracting = it },
                            onDraggingChange = { isDragging = it },
                            onHoveredIndexChange = { hoveredIndex = it },
                            onDragOffsetChange = { dragOffsetPx = it },
                            onCommitIndex = { onNavigate(destinations[it]) },
                        ),
                    horizontalAlignment = Alignment.CenterHorizontally,
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
                            label = "railContentColor",
                        )
                        val iconScale by animateFloatAsState(
                            targetValue = if (isActiveVisual) 1.05f else 1.0f,
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioNoBouncy,
                                stiffness = 820f,
                            ),
                            label = "railIconScale",
                        )

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                        ) {
                            Icon(
                                imageVector = if (isActiveVisual) dest.selectedIcon else dest.unselectedIcon,
                                contentDescription = dest.label,
                                tint = contentColor,
                                modifier = Modifier
                                    .size(20.dp)
                                    .graphicsLayer {
                                        scaleX = iconScale
                                        scaleY = iconScale
                                    },
                            )
                            Spacer(modifier = Modifier.height(2.dp))
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
