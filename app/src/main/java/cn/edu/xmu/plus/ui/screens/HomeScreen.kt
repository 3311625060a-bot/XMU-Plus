package cn.edu.xmu.plus.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.automirrored.rounded.FactCheck
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.Dialpad
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.edu.xmu.plus.auth.AuthState
import cn.edu.xmu.plus.data.ActiveRollcall
import cn.edu.xmu.plus.data.HomeworkItem
import cn.edu.xmu.plus.data.HomeworkStatus
import cn.edu.xmu.plus.data.UrgencyLevel
import cn.edu.xmu.plus.data.XmuDashboardState
import cn.edu.xmu.plus.ui.components.AmbientGlowBackground
import cn.edu.xmu.plus.ui.components.FrostedGlassCard
import cn.edu.xmu.plus.ui.components.FrostedHairlineDivider
import cn.edu.xmu.plus.ui.components.FrostedIconButton
import cn.edu.xmu.plus.ui.components.bounceClickable
import cn.edu.xmu.plus.ui.components.groupedCardShape
import cn.edu.xmu.plus.ui.components.optionalSharedBounds

@OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalMaterial3ExpressiveApi::class,
    ExperimentalSharedTransitionApi::class,
)
@Composable
fun HomeScreen(
    authState: AuthState,
    dashboardState: XmuDashboardState,
    isTabletLayout: Boolean = false,
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
    onOpenLogin: () -> Unit,
    onRefreshAll: () -> Unit,
    onOpenSearch: () -> Unit,
    onToggleDarkMode: (currentlyDark: Boolean) -> Unit,
    onOpenQrCheckIn: () -> Unit,
    onOpenPasscodeCheckIn: (ActiveRollcall?) -> Unit,
    onOpenRollcallHistory: () -> Unit,
    onOpenHomeworkDetail: (Long, String) -> Unit,
    onOpenPendingHomework: () -> Unit,
    onOpenAllHomework: () -> Unit,
    onOpenCourses: () -> Unit,
) {
    val pullState = rememberPullToRefreshState()
    val listState = rememberLazyListState()
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    var userPullRefresh by remember { mutableStateOf(false) }

    LaunchedEffect(dashboardState.isRefreshing) {
        if (!dashboardState.isRefreshing) {
            userPullRefresh = false
        }
    }

    val scrollOffsetPx by remember {
        derivedStateOf {
            if (listState.firstVisibleItemIndex == 0) {
                listState.firstVisibleItemScrollOffset.toFloat()
            } else {
                650f
            }
        }
    }

    val activeRollcall = dashboardState.activeRollcalls.firstOrNull()
    val pendingHomeworks = remember(dashboardState.homeworks) {
        dashboardState.homeworks.filter { it.status == HomeworkStatus.PENDING }
    }
    val unreadCount = remember(dashboardState.courses) {
        dashboardState.courses.sumOf { it.unreadCount }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AmbientGlowBackground(scrollOffsetPx = { scrollOffsetPx })

        PullToRefreshBox(
            isRefreshing = userPullRefresh && dashboardState.isRefreshing,
            onRefresh = {
                userPullRefresh = true
                onRefreshAll()
            },
            state = pullState,
            indicator = {
                PullToRefreshDefaults.LoadingIndicator(
                    state = pullState,
                    isRefreshing = userPullRefresh && dashboardState.isRefreshing,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .statusBarsPadding(),
                )
            },
            modifier = Modifier.fillMaxSize(),
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(top = 10.dp, bottom = if (isTabletLayout) 28.dp else 112.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                // Swiss Editorial Header
                item {
                    HomeEditorialHeader(
                        authState = authState,
                        isRefreshing = dashboardState.isRefreshing,
                        isDemoMode = dashboardState.isDemoMode,
                        lastSyncText = dashboardState.lastSyncText,
                        isDark = isDark,
                        onRefresh = onRefreshAll,
                        onOpenLogin = onOpenLogin,
                        onOpenSearch = onOpenSearch,
                        onToggleDarkMode = { onToggleDarkMode(isDark) },
                    )
                }

                // Live Active Rollcall Banner
                item {
                    AnimatedVisibility(
                        visible = activeRollcall != null,
                        enter = fadeIn(tween(130)) + expandVertically(tween(150)),
                        exit = shrinkVertically(tween(120)),
                    ) {
                        activeRollcall?.let { rollcall ->
                            LiveRollcallHeroCard(
                                rollcall = rollcall,
                                onClick = {
                                    if (rollcall.type == "qr") {
                                        onOpenQrCheckIn()
                                    } else {
                                        onOpenPasscodeCheckIn(rollcall)
                                    }
                                },
                                modifier = Modifier.padding(horizontal = 20.dp),
                            )
                        }
                    }
                }

                // Split Large Check-In Action Cards
                item {
                    Column(
                        modifier = Modifier.padding(horizontal = 20.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        CheckInActionCardsRow(
                            onOpenQrCheckIn = onOpenQrCheckIn,
                            onOpenPasscodeCheckIn = { onOpenPasscodeCheckIn(activeRollcall) },
                        )
                        FrostedGlassCard(
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .bounceClickable(pressedScale = 0.99f, onClick = onOpenRollcallHistory),
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 11.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(30.dp)
                                        .clip(RoundedCornerShape(9.dp))
                                        .background(MaterialTheme.colorScheme.secondaryContainer),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Rounded.FactCheck,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.secondary,
                                        modifier = Modifier.size(16.dp),
                                    )
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = "签到记录查询",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "各科出勤率与点名明细",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f),
                                )
                                Icon(
                                    imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        }
                    }
                }

                // Monospace Metric Cards
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        val urgentDdl = pendingHomeworks.firstOrNull()
                        OverviewStatCard(
                            label = "待交作业",
                            value = pendingHomeworks.size.toString(),
                            statusText = urgentDdl?.remainingText ?: "全部清空",
                            accentStatus = urgentDdl?.urgency == UrgencyLevel.HIGH,
                            onClick = onOpenPendingHomework,
                            modifier = Modifier.weight(1f),
                        )
                        OverviewStatCard(
                            label = "本学期课程",
                            value = dashboardState.courses.size.toString(),
                            statusText = if (unreadCount > 0) "$unreadCount 条未读" else "已全部阅读",
                            accentStatus = unreadCount > 0,
                            onClick = onOpenCourses,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }

                // Upcoming Section Header
                item {
                    SectionHeader(
                        title = "近期截止",
                        actionLabel = "全部 ${dashboardState.homeworks.size}",
                        onAction = onOpenAllHomework,
                    )
                }

                // Wireframe Task List or Empty State
                item {
                    if (pendingHomeworks.isEmpty()) {
                        EmptyTasksZenCard(
                            isRefreshing = dashboardState.isRefreshing,
                            onOpenAllHomework = onOpenAllHomework,
                            modifier = Modifier.padding(horizontal = 20.dp),
                        )
                    } else {
                        val previewItems = pendingHomeworks.take(5)
                        FrostedGlassCard(
                            shape = RoundedCornerShape(20.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp),
                        ) {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                previewItems.forEachIndexed { index, hw ->
                                    val shape = groupedCardShape(index, previewItems.size)
                                    val cardModifier = Modifier.optionalSharedBounds(
                                        sharedTransitionScope = sharedTransitionScope,
                                        animatedVisibilityScope = animatedVisibilityScope,
                                        key = "hw_card_home_${hw.id}",
                                        clipShape = shape,
                                    )
                                    HomeHomeworkRow(
                                        homework = hw,
                                        modifier = cardModifier,
                                        onClick = { onOpenHomeworkDetail(hw.id, "home") },
                                    )
                                    if (index < previewItems.lastIndex) {
                                        FrostedHairlineDivider(startIndent = 16.dp)
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

@Composable
private fun HomeEditorialHeader(
    authState: AuthState,
    isRefreshing: Boolean,
    isDemoMode: Boolean,
    lastSyncText: String?,
    isDark: Boolean,
    onRefresh: () -> Unit,
    onOpenLogin: () -> Unit,
    onOpenSearch: () -> Unit,
    onToggleDarkMode: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 20.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .clickable(enabled = !isRefreshing) { onRefresh() }
                    .padding(vertical = 2.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(
                            when {
                                isRefreshing -> MaterialTheme.colorScheme.primary
                                isDemoMode -> MaterialTheme.colorScheme.outline
                                else -> MaterialTheme.colorScheme.tertiary
                            },
                        ),
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = when {
                        isRefreshing -> "SYNCING..."
                        isDemoMode -> "DEMO · 点击同步"
                        lastSyncText != null -> lastSyncText
                        else -> "XMU PLUS"
                    },
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontFamily = FontFamily.Monospace,
                        fontFeatureSettings = "tnum",
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Spacer(modifier = Modifier.height(2.dp))

            Text(
                text = when (authState) {
                    is AuthState.LoggedIn -> "你好，${authState.profile.name}"
                    else -> "XMU Plus"
                },
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            val subtitleText = when (authState) {
                is AuthState.LoggedIn -> "厦门大学数字化教学平台"
                AuthState.Checking -> "正在恢复本地加密会话…"
                AuthState.LoggedOut -> "点击登录统一身份认证 →"
            }
            Text(
                text = subtitleText,
                style = MaterialTheme.typography.bodySmall,
                color = if (authState is AuthState.LoggedOut) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = if (authState is AuthState.LoggedOut) {
                    Modifier
                        .padding(top = 2.dp)
                        .clickable { onOpenLogin() }
                } else {
                    Modifier.padding(top = 1.dp)
                },
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FrostedIconButton(
                icon = Icons.Rounded.Search,
                contentDescription = "全局搜索",
                onClick = onOpenSearch,
                tint = MaterialTheme.colorScheme.onSurface,
            )
            FrostedIconButton(
                icon = if (isDark) Icons.Rounded.LightMode else Icons.Rounded.DarkMode,
                contentDescription = if (isDark) "切换为浅色主题" else "切换为深色主题",
                onClick = onToggleDarkMode,
                tint = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun CheckInActionCardsRow(
    onOpenQrCheckIn: () -> Unit,
    onOpenPasscodeCheckIn: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        FrostedGlassCard(
            modifier = Modifier
                .weight(1f)
                .bounceClickable(pressedScale = 0.985f, onClick = onOpenQrCheckIn),
            shape = RoundedCornerShape(20.dp),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(RoundedCornerShape(13.dp))
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.QrCodeScanner,
                            contentDescription = "扫码签到",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(21.dp),
                        )
                    }
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
                        modifier = Modifier.size(18.dp),
                    )
                }
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = "扫码签到",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = "教室二维码即扫即签",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        FrostedGlassCard(
            modifier = Modifier
                .weight(1f)
                .bounceClickable(pressedScale = 0.985f, onClick = onOpenPasscodeCheckIn),
            shape = RoundedCornerShape(20.dp),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(RoundedCornerShape(13.dp))
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Dialpad,
                            contentDescription = "密码签到",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(21.dp),
                        )
                    }
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
                        modifier = Modifier.size(18.dp),
                    )
                }
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = "密码签到",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = "4 位数字口令签到",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun LiveRollcallHeroCard(
    rollcall: ActiveRollcall,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val pulseTransition = rememberInfiniteTransition(label = "livePulse")
    val pulseAlpha by pulseTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(900),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pulseAlpha",
    )

    FrostedGlassCard(
        modifier = modifier
            .fillMaxWidth()
            .bounceClickable(onClick = onClick)
            .border(
                width = 0.8.dp,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.45f),
                shape = RoundedCornerShape(20.dp),
            ),
        shape = RoundedCornerShape(20.dp),
        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min)
                .padding(horizontal = 14.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .fillMaxHeight()
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .graphicsLayer { alpha = pulseAlpha }
                            .background(MaterialTheme.colorScheme.primary),
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "正在点名 · ${rollcall.startedAt}",
                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = rollcall.courseName,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "${rollcall.teacher} · ${if (rollcall.type == "qr") "二维码签到" else "数字口令签到"}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(modifier = Modifier.width(10.dp))

            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.primary,
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "立即签到",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(14.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun OverviewStatCard(
    label: String,
    value: String,
    statusText: String,
    accentStatus: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    FrostedGlassCard(
        modifier = modifier.bounceClickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp),
    ) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 13.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = statusText,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontFamily = FontFamily.Monospace,
                        fontFeatureSettings = "tnum",
                    ),
                    color = if (accentStatus) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom,
            ) {
                Text(
                    text = value,
                    style = MaterialTheme.typography.displaySmall.copy(
                        fontSize = 30.sp,
                        fontFamily = FontFamily.Monospace,
                        fontFeatureSettings = "tnum",
                        fontWeight = FontWeight.Bold,
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
                    modifier = Modifier.size(17.dp),
                )
            }
        }
    }
}

@Composable
private fun SectionHeader(
    title: String,
    actionLabel: String,
    onAction: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .bounceClickable(onClick = onAction)
                .padding(horizontal = 4.dp, vertical = 2.dp),
        ) {
            Text(
                text = actionLabel,
                style = MaterialTheme.typography.labelLarge.copy(
                    fontFamily = FontFamily.Monospace,
                    fontFeatureSettings = "tnum",
                ),
                color = MaterialTheme.colorScheme.primary,
            )
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

@Composable
private fun EmptyTasksZenCard(
    isRefreshing: Boolean,
    onOpenAllHomework: () -> Unit,
    modifier: Modifier = Modifier,
) {
    FrostedGlassCard(
        shape = RoundedCornerShape(20.dp),
        modifier = modifier
            .fillMaxWidth()
            .bounceClickable(onClick = onOpenAllHomework),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Rounded.CheckCircle,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.tertiary,
                modifier = Modifier.size(19.dp),
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (isRefreshing) "正在同步待交作业…" else "当前无待交作业",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Medium),
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = if (isRefreshing) "正在拉取最新待办作业" else "近期截止任务已全部清空",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "查看全部",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}

@Composable
private fun HomeHomeworkRow(
    homework: HomeworkItem,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val urgencyColor = when (homework.urgency) {
        UrgencyLevel.HIGH -> MaterialTheme.colorScheme.error
        UrgencyLevel.MEDIUM -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.outlineVariant
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .bounceClickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .width(2.5.dp)
                .height(34.dp)
                .clip(CircleShape)
                .background(urgencyColor),
        )
        Spacer(modifier = Modifier.width(11.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = homework.courseName,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = homework.title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "截止 ${homework.deadlineText}",
                style = MaterialTheme.typography.bodySmall.copy(
                    fontFamily = FontFamily.Monospace,
                    fontFeatureSettings = "tnum",
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(modifier = Modifier.width(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(5.dp)
                    .clip(CircleShape)
                    .background(urgencyColor),
            )
            Spacer(modifier = Modifier.width(5.dp))
            Text(
                text = homework.remainingText,
                style = MaterialTheme.typography.labelMedium.copy(
                    fontFamily = FontFamily.Monospace,
                    fontFeatureSettings = "tnum",
                    fontWeight = FontWeight.SemiBold,
                ),
                color = if (homework.urgency == UrgencyLevel.HIGH) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
            )
        }
    }
}
