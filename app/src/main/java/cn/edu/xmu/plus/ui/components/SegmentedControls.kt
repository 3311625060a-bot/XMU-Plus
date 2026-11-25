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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.SettingsBrightness
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import cn.edu.xmu.plus.data.DarkModePreference
import cn.edu.xmu.plus.data.DockStyle
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
fun <T> ExpressiveGlidingSegmentedBar(
    items: List<T>,
    selected: T,
    onSelect: (T) -> Unit,
    labelProvider: (T) -> String,
    modifier: Modifier = Modifier,
    iconProvider: ((T) -> ImageVector)? = null,
    height: Dp = 40.dp,
    pillColor: Color? = null,
    selectedTextColor: Color = MaterialTheme.colorScheme.onSurface,
) {
    val selectedIndex = items.indexOf(selected).coerceAtLeast(0)
    val density = LocalDensity.current
    val haptic = LocalHapticFeedback.current
    val hapticsEnabled by rememberUpdatedState(LocalHapticsEnabled.current)
    val currentOnSelect by rememberUpdatedState(onSelect)
    val touchSlop = LocalViewConfiguration.current.touchSlop

    val trackFill = MaterialTheme.colorScheme.surfaceContainer
    val resolvedPillColor = pillColor ?: MaterialTheme.colorScheme.surfaceContainerLowest

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(height / 2))
            .background(trackFill)
            .border(
                width = 0.6.dp,
                color = MaterialTheme.colorScheme.outlineVariant,
                shape = RoundedCornerShape(height / 2),
            ),
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(3.dp),
        ) {
            val slotWidthDp = maxWidth / items.size
            val slotWidthPx = with(density) { slotWidthDp.toPx() }
            val totalWidthPx = with(density) { maxWidth.toPx() }

            var isDragging by remember { mutableStateOf(false) }
            var hoveredIndex by remember { mutableStateOf<Int?>(null) }
            var dragOffsetPx by remember { mutableFloatStateOf(0f) }

            val visualIndex = hoveredIndex ?: selectedIndex

            val springOffsetPx by animateFloatAsState(
                targetValue = slotWidthPx * visualIndex,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = 820f,
                ),
                label = "segOffsetPx",
            )
            val activeOffsetPx = if (isDragging) dragOffsetPx else springOffsetPx

            Box(
                modifier = Modifier
                    .offset { IntOffset(activeOffsetPx.roundToInt(), 0) }
                    .width(slotWidthDp)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape((height - 6.dp) / 2))
                    .background(resolvedPillColor)
                    .border(
                        width = 0.6.dp,
                        color = MaterialTheme.colorScheme.outlineVariant,
                        shape = RoundedCornerShape((height - 6.dp) / 2),
                    ),
            )

            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(items, slotWidthPx, totalWidthPx) {
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            val downX = down.position.x
                            var dragged = false
                            var lastHoveredIdx = ((downX / slotWidthPx).toInt())
                                .coerceIn(0, items.lastIndex)

                            try {
                                while (true) {
                                    val event = awaitPointerEvent()
                                    val change = event.changes.firstOrNull { it.id == down.id }
                                        ?: event.changes.firstOrNull()
                                        ?: break
                                    if (!change.pressed) {
                                        val finalIdx = ((change.position.x / slotWidthPx).toInt())
                                            .coerceIn(0, items.lastIndex)
                                        if (hapticsEnabled) {
                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        }
                                        currentOnSelect(items[finalIdx])
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
                                        dragOffsetPx = (currentX - slotWidthPx / 2f).coerceIn(0f, maxDrag)
                                        val idx = ((currentX / slotWidthPx).toInt())
                                            .coerceIn(0, items.lastIndex)
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
                                hoveredIndex = null
                            }
                        }
                    },
            ) {
                items.forEachIndexed { index, item ->
                    val isActiveVisual = index == visualIndex
                    val fg by animateColorAsState(
                        targetValue = if (isActiveVisual) selectedTextColor else MaterialTheme.colorScheme.onSurfaceVariant,
                        label = "segText",
                    )
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (iconProvider != null) {
                            Icon(
                                imageVector = iconProvider(item),
                                contentDescription = null,
                                tint = if (isActiveVisual) MaterialTheme.colorScheme.primary else fg,
                                modifier = Modifier.size(16.dp),
                            )
                            Spacer(modifier = Modifier.width(5.dp))
                        }
                        Text(
                            text = labelProvider(item),
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = if (isActiveVisual) FontWeight.Bold else FontWeight.Medium,
                            ),
                            color = fg,
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun DarkModeSegmentedRow(
    selected: DarkModePreference,
    onSelect: (DarkModePreference) -> Unit,
) {
    ExpressiveGlidingSegmentedBar(
        items = DarkModePreference.entries,
        selected = selected,
        onSelect = onSelect,
        labelProvider = { it.label },
        iconProvider = { mode ->
            when (mode) {
                DarkModePreference.SYSTEM -> Icons.Rounded.SettingsBrightness
                DarkModePreference.LIGHT -> Icons.Rounded.LightMode
                DarkModePreference.DARK -> Icons.Rounded.DarkMode
            }
        },
        height = 38.dp,
    )
}

@Composable
fun DockStyleSegmentedRow(
    selected: DockStyle,
    onSelect: (DockStyle) -> Unit,
) {
    ExpressiveGlidingSegmentedBar(
        items = DockStyle.entries,
        selected = selected,
        onSelect = onSelect,
        labelProvider = { it.label },
        iconProvider = { style ->
            when (style) {
                DockStyle.LIQUID_GLASS -> Icons.Rounded.AutoAwesome
                DockStyle.CLASSIC -> Icons.Rounded.Layers
            }
        },
        height = 38.dp,
    )
}
