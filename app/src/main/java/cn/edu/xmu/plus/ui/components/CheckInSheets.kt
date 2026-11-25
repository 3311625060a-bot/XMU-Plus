package cn.edu.xmu.plus.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Backspace
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import cn.edu.xmu.plus.data.ActiveRollcall
import cn.edu.xmu.plus.network.CheckInApiResult
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun PasscodeCheckInSheet(
    activeRollcalls: List<ActiveRollcall>,
    initialRollcall: ActiveRollcall?,
    isDemoMode: Boolean,
    onRefreshRollcalls: () -> Unit,
    onSubmitPasscode: suspend (Long, String) -> CheckInApiResult,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val haptic = LocalHapticFeedback.current
    val hapticsEnabled = LocalHapticsEnabled.current
    val scope = rememberCoroutineScope()

    val numberRollcalls = remember(activeRollcalls) {
        activeRollcalls.filter { it.type == "number" || it.isNumber }
    }
    var selectedRollcallId by remember(initialRollcall, numberRollcalls) {
        mutableStateOf(
            initialRollcall?.id ?: numberRollcalls.firstOrNull()?.id ?: activeRollcalls.firstOrNull()?.id,
        )
    }
    val activeTarget = remember(selectedRollcallId, activeRollcalls, initialRollcall) {
        activeRollcalls.find { it.id == selectedRollcallId } ?: initialRollcall ?: numberRollcalls.firstOrNull()
    }

    LaunchedEffect(Unit) {
        onRefreshRollcalls()
    }

    var digits by remember { mutableStateOf("") }
    var state by remember { mutableStateOf(CheckInUiState.INPUT) }
    var feedbackMessage by remember { mutableStateOf("输入老师公布的 4 位数字签到码") }
    val shakeOffset = remember { Animatable(0f) }

    fun submitCode(code: String) {
        val targetId = activeTarget?.id
        if (targetId == null && !isDemoMode) {
            state = CheckInUiState.ERROR
            feedbackMessage = "当前暂无进行中的数字点名，请刷新后再试"
            digits = ""
            return
        }
        scope.launch {
            state = CheckInUiState.VERIFYING
            feedbackMessage = "正在校验签到码…"
            when (val res = onSubmitPasscode(targetId ?: 0L, code)) {
                is CheckInApiResult.Success -> {
                    state = CheckInUiState.SUCCESS
                    feedbackMessage = res.message
                    if (hapticsEnabled) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                }

                is CheckInApiResult.Error -> {
                    state = CheckInUiState.ERROR
                    feedbackMessage = res.message
                    if (hapticsEnabled) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    val amplitudes = listOf(-18f, 18f, -14f, 14f, -8f, 8f, 0f)
                    for (target in amplitudes) {
                        shakeOffset.animateTo(target, spring(stiffness = Spring.StiffnessHigh))
                    }
                    delay(450)
                    digits = ""
                    state = CheckInUiState.INPUT
                }
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        dragHandle = { SheetGrabber() },
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.96f),
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "密码签到",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = activeTarget?.let { "《${it.courseName}》 · ${it.startedAt}" }
                            ?: if (isDemoMode) "演示模式 · 输入 0000 可体验错误抖动" else "当前暂无进行中的数字点名（可点击刷新）",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Row {
                    IconButton(onClick = onRefreshRollcalls) {
                        Icon(Icons.Rounded.Refresh, contentDescription = "刷新点名")
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Rounded.Close, contentDescription = "关闭")
                    }
                }
            }

            if (activeRollcalls.size > 1) {
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    activeRollcalls.take(3).forEach { rc ->
                        FilterChip(
                            selected = rc.id == activeTarget?.id,
                            onClick = { selectedRollcallId = rc.id },
                            label = { Text(rc.courseName, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            AnimatedContent(
                targetState = state,
                transitionSpec = {
                    (fadeIn(tween(150)) + scaleIn(initialScale = 0.96f)) togetherWith
                        (fadeOut(tween(110)) + scaleOut(targetScale = 0.97f))
                },
                label = "passcodeState",
            ) { uiState ->
                when (uiState) {
                    CheckInUiState.VERIFYING -> {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 36.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            LoadingIndicator(modifier = Modifier.size(56.dp))
                            Spacer(modifier = Modifier.height(14.dp))
                            Text(
                                text = feedbackMessage,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }

                    CheckInUiState.SUCCESS -> {
                        Surface(
                            shape = RoundedCornerShape(24.dp),
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column(
                                modifier = Modifier.padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.CheckCircle,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(52.dp),
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = "签到成功",
                                    style = MaterialTheme.typography.titleLarge,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = feedbackMessage,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f),
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Button(onClick = onDismiss) {
                                    Text("完成")
                                }
                            }
                        }
                    }

                    CheckInUiState.INPUT, CheckInUiState.ERROR -> {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Row(
                                modifier = Modifier
                                    .offset { IntOffset(shakeOffset.value.roundToInt(), 0) },
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                repeat(4) { index ->
                                    val ch = digits.getOrNull(index)?.toString() ?: ""
                                    val filled = ch.isNotEmpty()
                                    val isCurrent = digits.length == index
                                    val slotScale by animateFloatAsState(
                                        targetValue = if (filled) 1.03f else 1f,
                                        animationSpec = spring(
                                            dampingRatio = Spring.DampingRatioNoBouncy,
                                            stiffness = 860f,
                                        ),
                                        label = "digitPop",
                                    )
                                    Surface(
                                        modifier = Modifier
                                            .size(width = 62.dp, height = 72.dp)
                                            .graphicsLayer {
                                                scaleX = slotScale
                                                scaleY = slotScale
                                            },
                                        shape = RoundedCornerShape(20.dp),
                                        color = when {
                                            uiState == CheckInUiState.ERROR -> MaterialTheme.colorScheme.errorContainer
                                            filled -> MaterialTheme.colorScheme.primaryContainer
                                            else -> MaterialTheme.colorScheme.surfaceContainerLowest
                                        },
                                        border = if (isCurrent || uiState == CheckInUiState.ERROR) {
                                            BorderStroke(
                                                width = 1.5.dp,
                                                color = if (uiState == CheckInUiState.ERROR) {
                                                    MaterialTheme.colorScheme.error
                                                } else {
                                                    MaterialTheme.colorScheme.primary
                                                },
                                            )
                                        } else {
                                            null
                                        },
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Text(
                                                text = ch,
                                                style = MaterialTheme.typography.displaySmall.copy(
                                                    fontFeatureSettings = "tnum",
                                                ),
                                                color = if (uiState == CheckInUiState.ERROR) {
                                                    MaterialTheme.colorScheme.onErrorContainer
                                                } else {
                                                    MaterialTheme.colorScheme.onPrimaryContainer
                                                },
                                            )
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = feedbackMessage,
                                style = MaterialTheme.typography.bodySmall,
                                color = if (uiState == CheckInUiState.ERROR) {
                                    MaterialTheme.colorScheme.error
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                            )
                            Spacer(modifier = Modifier.height(18.dp))

                            val rows = listOf(
                                listOf("1", "2", "3"),
                                listOf("4", "5", "6"),
                                listOf("7", "8", "9"),
                                listOf("C", "0", "DEL"),
                            )
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                rows.forEach { row ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    ) {
                                        row.forEach { key ->
                                            KeypadButton(
                                                key = key,
                                                modifier = Modifier.weight(1f),
                                                onClick = {
                                                    when (key) {
                                                        "C" -> {
                                                            digits = ""
                                                            feedbackMessage = "输入老师公布的 4 位数字签到码"
                                                        }

                                                        "DEL" -> {
                                                            if (digits.isNotEmpty()) {
                                                                digits = digits.dropLast(1)
                                                            }
                                                        }

                                                        else -> {
                                                            if (digits.length < 4) {
                                                                val next = digits + key
                                                                digits = next
                                                                if (next.length == 4) {
                                                                    submitCode(next)
                                                                }
                                                            }
                                                        }
                                                    }
                                                },
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private enum class CheckInUiState {
    INPUT, VERIFYING, SUCCESS, ERROR
}

@Composable
private fun KeypadButton(
    key: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val isAction = key == "C" || key == "DEL"
    Surface(
        modifier = modifier
            .height(56.dp)
            .bounceClickable(pressedScale = 0.94f, onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        color = if (isAction) {
            MaterialTheme.colorScheme.surfaceContainerHigh
        } else {
            MaterialTheme.colorScheme.surfaceContainerLowest
        },
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (key == "DEL") {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.Backspace,
                    contentDescription = "删除",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
            } else {
                Text(
                    text = key,
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = if (isAction) FontWeight.Medium else FontWeight.SemiBold,
                        fontFeatureSettings = "tnum",
                    ),
                    color = if (isAction) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                )
            }
        }
    }
}
