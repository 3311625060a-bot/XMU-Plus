package cn.edu.xmu.plus.ui.screens

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Badge
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.edu.xmu.plus.BuildConfig
import cn.edu.xmu.plus.auth.AuthState
import cn.edu.xmu.plus.data.HomeworkStatus
import cn.edu.xmu.plus.data.ThemeSettings
import cn.edu.xmu.plus.data.XmuDashboardState
import cn.edu.xmu.plus.ui.components.AmbientGlowBackground
import cn.edu.xmu.plus.ui.components.FrostedGlassCard
import cn.edu.xmu.plus.ui.components.FrostedHairlineDivider
import cn.edu.xmu.plus.ui.components.bounceClickable
import cn.edu.xmu.plus.ui.components.groupedCardShape
import cn.edu.xmu.plus.ui.components.optionalSharedBounds

@OptIn(ExperimentalSharedTransitionApi::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ProfileScreen(
    themeSettings: ThemeSettings,
    authState: AuthState,
    dashboardState: XmuDashboardState,
    isTabletLayout: Boolean = false,
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
    onOpenLogin: () -> Unit,
    onRefreshProfile: () -> Unit,
    onLogout: () -> Unit,
    onOpenCourses: () -> Unit,
    onOpenPendingHomework: () -> Unit,
    onOpenGradedHomework: () -> Unit,
    onOpenSettings: (String) -> Unit,
) {
    var showLogoutDialog by remember { mutableStateOf(false) }
    var showAboutDialog by remember { mutableStateOf(false) }

    val pendingCount = remember(dashboardState.homeworks) {
        dashboardState.homeworks.count { it.status == HomeworkStatus.PENDING }
    }
    val gradedCount = remember(dashboardState.homeworks) {
        dashboardState.homeworks.count { it.status == HomeworkStatus.GRADED }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AmbientGlowBackground()

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding(),
            contentPadding = PaddingValues(
                start = 20.dp,
                end = 20.dp,
                top = 12.dp,
                bottom = if (isTabletLayout) 28.dp else 118.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                Text(
                    text = "我的",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                )
            }

            // Hero Identity Card
            item {
                FrostedGlassCard(
                    shape = RoundedCornerShape(22.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(52.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center,
                        ) {
                            when (authState) {
                                AuthState.Checking -> LoadingIndicator(modifier = Modifier.size(24.dp))
                                is AuthState.LoggedIn -> Icon(
                                    imageVector = Icons.Rounded.VerifiedUser,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(24.dp),
                                )
                                AuthState.LoggedOut -> Icon(
                                    imageVector = Icons.Rounded.School,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(24.dp),
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = when (authState) {
                                    is AuthState.LoggedIn -> authState.profile.name
                                    AuthState.Checking -> "正在恢复会话…"
                                    AuthState.LoggedOut -> "厦大同学"
                                },
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = when (authState) {
                                    is AuthState.LoggedIn -> buildString {
                                        append(authState.profile.userNo.ifBlank { "已认证" })
                                        append(" · ")
                                        append(authState.profile.role)
                                        if (authState.profile.department.isNotBlank()) {
                                            append(" · ")
                                            append(authState.profile.department)
                                        }
                                        if (authState.isRefreshing) {
                                            append("（同步中）")
                                        }
                                    }
                                    AuthState.Checking -> "正在验证 Keystore 加密会话"
                                    AuthState.LoggedOut -> "统一身份认证 · 未登录"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        when (authState) {
                            is AuthState.LoggedIn -> FilledTonalButton(
                                onClick = { showLogoutDialog = true },
                                shape = RoundedCornerShape(14.dp),
                            ) {
                                Text("退出登录")
                            }
                            AuthState.LoggedOut -> FilledTonalButton(
                                onClick = onOpenLogin,
                                shape = RoundedCornerShape(14.dp),
                            ) {
                                Text("去登录")
                            }
                            AuthState.Checking -> Unit
                        }
                    }
                }
            }

            // 3-Column Academic Bento Strip
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    ProfileMetricCard(
                        label = "在修课程",
                        value = dashboardState.courses.size.toString(),
                        onClick = onOpenCourses,
                        modifier = Modifier.weight(1f),
                    )
                    ProfileMetricCard(
                        label = "待交作业",
                        value = pendingCount.toString(),
                        onClick = onOpenPendingHomework,
                        modifier = Modifier.weight(1f),
                    )
                    ProfileMetricCard(
                        label = "已批改",
                        value = gradedCount.toString(),
                        onClick = onOpenGradedHomework,
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            // Unified Wireframe Grouped Menu Stack
            item {
                FrostedGlassCard(
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column {
                        val appearanceShape = groupedCardShape(0, 4)
                        val appearanceSharedModifier = Modifier.optionalSharedBounds(
                            sharedTransitionScope = sharedTransitionScope,
                            animatedVisibilityScope = animatedVisibilityScope,
                            key = "settings_card_appearance",
                            clipShape = appearanceShape,
                        )

                        val generalShape = groupedCardShape(1, 4)
                        val generalSharedModifier = Modifier.optionalSharedBounds(
                            sharedTransitionScope = sharedTransitionScope,
                            animatedVisibilityScope = animatedVisibilityScope,
                            key = "settings_card_general",
                            clipShape = generalShape,
                        )

                        ProfileMenuRow(
                            icon = Icons.Rounded.Palette,
                            title = "外观与点缀色调",
                            subtitle = "${themeSettings.accentTone.displayName} · ${themeSettings.darkMode.label} · ${themeSettings.dockStyle.label}",
                            modifier = appearanceSharedModifier,
                            trailingContent = {
                                Box(
                                    modifier = Modifier
                                        .size(12.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.primary),
                                )
                            },
                            onClick = { onOpenSettings("appearance") },
                        )

                        FrostedHairlineDivider(startIndent = 64.dp)

                        ProfileMenuRow(
                            icon = Icons.Rounded.Settings,
                            title = "应用设置",
                            subtitle = "切页动效、提醒通知与平板适配",
                            modifier = generalSharedModifier,
                            onClick = { onOpenSettings("general") },
                        )

                        FrostedHairlineDivider(startIndent = 64.dp)

                        ProfileMenuRow(
                            icon = Icons.Rounded.Badge,
                            title = "统一身份认证账号",
                            subtitle = when (authState) {
                                is AuthState.LoggedIn -> "${authState.profile.name} (${authState.profile.userNo}) · 点击刷新会话"
                                AuthState.Checking -> "正在检查本地加密会话…"
                                AuthState.LoggedOut -> "点击登录厦大统一身份认证（支持滑块网页兜底）"
                            },
                            onClick = {
                                when (authState) {
                                    is AuthState.LoggedIn -> onRefreshProfile()
                                    AuthState.LoggedOut -> onOpenLogin()
                                    AuthState.Checking -> Unit
                                }
                            },
                        )

                        FrostedHairlineDivider(startIndent = 64.dp)

                        ProfileMenuRow(
                            icon = Icons.Rounded.Info,
                            title = "关于 XMU Plus",
                            subtitle = "v${BuildConfig.VERSION_NAME} · MIT · 金将军的迈巴赫保养员",
                            onClick = { showAboutDialog = true },
                        )
                    }
                }
            }
        }
    }

    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            title = { Text("退出统一身份认证？") },
            text = {
                Text("将清除本地加密保存的平台会话 Cookie 与 x-session-id，退出后可随时重新登录。")
            },
            dismissButton = {
                TextButton(onClick = { showLogoutDialog = false }) {
                    Text("取消")
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showLogoutDialog = false
                        onLogout()
                    },
                ) {
                    Text("确认退出")
                }
            },
        )
    }

    if (showAboutDialog) {
        val uriHandler = LocalUriHandler.current
        val context = LocalContext.current
        val repoUrl = "https://github.com/3311625060a-bot/XMU-Plus"
        val openRepo = {
            runCatching { uriHandler.openUri(repoUrl) }.onFailure {
                Toast.makeText(context, "无法打开浏览器，请手动访问 GitHub 仓库", Toast.LENGTH_SHORT).show()
            }
            Unit
        }

        AlertDialog(
            onDismissRequest = { showAboutDialog = false },
            title = {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("关于 XMU Plus")
                    Text(
                        text = "v${BuildConfig.VERSION_NAME} · MIT License",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontFamily = FontFamily.Monospace,
                            fontFeatureSettings = "tnum",
                        ),
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "为厦门大学数字化教学平台（畅课 TronClass）打造的第三方原生 Android 客户端，基于 MIT 协议开源。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(
                                width = 0.6.dp,
                                color = MaterialTheme.colorScheme.outlineVariant,
                                shape = RoundedCornerShape(14.dp),
                            ),
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Text(
                                text = "作者：金将军的迈巴赫保养员",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Text(
                                text = "开源协议：MIT License",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                text = repoUrl,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 10.5.sp,
                                ),
                                maxLines = 1,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.clickable(onClick = openRepo),
                            )
                        }
                    }
                    Text(
                        text = "如果这个小助手帮到了你，欢迎去 GitHub 点个 Star 支持一下 ⭐",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showAboutDialog = false }) {
                    Text("关闭")
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        openRepo()
                        showAboutDialog = false
                    },
                ) {
                    Text("前往 GitHub 给个 Star")
                }
            },
        )
    }
}

@Composable
private fun ProfileMetricCard(
    label: String,
    value: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    FrostedGlassCard(
        modifier = modifier.bounceClickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 13.dp, horizontal = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontSize = 24.sp,
                    fontFamily = FontFamily.Monospace,
                    fontFeatureSettings = "tnum",
                    fontWeight = FontWeight.Bold,
                ),
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(modifier = Modifier.height(3.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
                    modifier = Modifier.size(13.dp),
                )
            }
        }
    }
}

@Composable
private fun ProfileMenuRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    trailingContent: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .bounceClickable(onClick = onClick)
            .padding(horizontal = 15.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(RoundedCornerShape(9.dp))
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(17.dp),
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (trailingContent != null) {
            trailingContent()
            Spacer(modifier = Modifier.width(6.dp))
        }
        Icon(
            imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            modifier = Modifier.size(18.dp),
        )
    }
}
