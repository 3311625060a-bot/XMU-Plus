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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.FactCheck
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Badge
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.NewReleases
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.SystemUpdate
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
import androidx.compose.runtime.rememberCoroutineScope
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.edu.xmu.plus.BuildConfig
import cn.edu.xmu.plus.auth.AuthState
import cn.edu.xmu.plus.data.AppUpdateChecker
import cn.edu.xmu.plus.data.HomeworkStatus
import cn.edu.xmu.plus.data.ThemeSettings
import cn.edu.xmu.plus.data.UpdateCheckResult
import cn.edu.xmu.plus.data.XmuDashboardState
import kotlinx.coroutines.launch
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
    onOpenRollcallHistory: () -> Unit,
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

            // Hero Identity Card (2-tier Swiss editorial layout)
            item {
                FrostedGlassCard(
                    shape = RoundedCornerShape(22.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(RoundedCornerShape(13.dp))
                                    .background(MaterialTheme.colorScheme.primaryContainer),
                                contentAlignment = Alignment.Center,
                            ) {
                                when (authState) {
                                    AuthState.Checking -> LoadingIndicator(modifier = Modifier.size(22.dp))
                                    is AuthState.LoggedIn -> Icon(
                                        imageVector = Icons.Rounded.VerifiedUser,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(22.dp),
                                    )
                                    AuthState.LoggedOut -> Icon(
                                        imageVector = Icons.Rounded.School,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(22.dp),
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    Text(
                                        text = when (authState) {
                                            is AuthState.LoggedIn -> authState.profile.name
                                            AuthState.Checking -> "正在恢复会话…"
                                            AuthState.LoggedOut -> "厦大同学"
                                        },
                                        style = MaterialTheme.typography.titleLarge,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    if (authState is AuthState.LoggedIn && authState.isRefreshing) {
                                        Text(
                                            text = "· 同步中",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.primary,
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = when (authState) {
                                        is AuthState.LoggedIn -> buildString {
                                            append(authState.profile.userNo.ifBlank { "已认证" })
                                            append(" · ")
                                            append(authState.profile.role)
                                        }
                                        AuthState.Checking -> "正在验证 Keystore 加密会话"
                                        AuthState.LoggedOut -> "统一身份认证 · 未登录"
                                    },
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        fontFamily = if (authState is AuthState.LoggedIn) {
                                            FontFamily.Monospace
                                        } else {
                                            FontFamily.Default
                                        },
                                        fontFeatureSettings = "tnum",
                                    ),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            when (authState) {
                                is AuthState.LoggedIn -> Surface(
                                    shape = RoundedCornerShape(11.dp),
                                    color = MaterialTheme.colorScheme.surfaceContainer,
                                    modifier = Modifier
                                        .border(
                                            width = 0.6.dp,
                                            color = MaterialTheme.colorScheme.outlineVariant,
                                            shape = RoundedCornerShape(11.dp),
                                        )
                                        .bounceClickable { showLogoutDialog = true },
                                ) {
                                    Text(
                                        text = "退出登录",
                                        style = MaterialTheme.typography.labelMedium.copy(
                                            fontWeight = FontWeight.Medium,
                                        ),
                                        color = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                                    )
                                }
                                AuthState.LoggedOut -> FilledTonalButton(
                                    onClick = onOpenLogin,
                                    shape = RoundedCornerShape(11.dp),
                                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                                ) {
                                    Text("去登录", style = MaterialTheme.typography.labelMedium)
                                }
                                AuthState.Checking -> Unit
                            }
                        }

                        if (authState is AuthState.LoggedIn && authState.profile.department.isNotBlank()) {
                            Spacer(modifier = Modifier.height(12.dp))
                            FrostedHairlineDivider()
                            Spacer(modifier = Modifier.height(10.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.School,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.78f),
                                    modifier = Modifier.size(15.dp),
                                )
                                Text(
                                    text = authState.profile.department,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
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
                        val settingsShape = groupedCardShape(0, 4)
                        val generalSharedModifier = Modifier.optionalSharedBounds(
                            sharedTransitionScope = sharedTransitionScope,
                            animatedVisibilityScope = animatedVisibilityScope,
                            key = "settings_card_general",
                            clipShape = settingsShape,
                        )

                        ProfileMenuRow(
                            icon = Icons.Rounded.Settings,
                            title = "应用设置",
                            subtitle = "${themeSettings.accentTone.displayName} · ${themeSettings.darkMode.label} · 提醒与动效",
                            modifier = generalSharedModifier,
                            trailingContent = {
                                Box(
                                    modifier = Modifier
                                        .size(12.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.primary),
                                )
                            },
                            onClick = { onOpenSettings("general") },
                        )

                        FrostedHairlineDivider(startIndent = 64.dp)

                        ProfileMenuRow(
                            icon = Icons.AutoMirrored.Rounded.FactCheck,
                            title = "签到记录查询",
                            subtitle = "查看各门课程出勤率、历史点名与请假记录",
                            onClick = onOpenRollcallHistory,
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
        AboutXmuPlusDialog(
            onDismiss = { showAboutDialog = false },
        )
    }
}

private sealed interface AboutUpdateUiState {
    data object Idle : AboutUpdateUiState
    data object Checking : AboutUpdateUiState
    data class Done(val result: UpdateCheckResult) : AboutUpdateUiState
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun AboutXmuPlusDialog(
    onDismiss: () -> Unit,
) {
    val uriHandler = LocalUriHandler.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repoUrl = AppUpdateChecker.REPO_URL
    var updateState by remember { mutableStateOf<AboutUpdateUiState>(AboutUpdateUiState.Idle) }

    val openUriSafe: (String, String) -> Unit = { url, errMsg ->
        runCatching { uriHandler.openUri(url) }.onFailure {
            Toast.makeText(context, errMsg, Toast.LENGTH_SHORT).show()
        }
    }

    val runUpdateCheck = {
        if (updateState !is AboutUpdateUiState.Checking) {
            updateState = AboutUpdateUiState.Checking
            scope.launch {
                val res = AppUpdateChecker.checkForUpdate(BuildConfig.VERSION_NAME)
                updateState = AboutUpdateUiState.Done(res)
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("关于 XMU Plus")
                Text(
                    text = "v${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}) · MIT License",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontFamily = FontFamily.Monospace,
                        fontFeatureSettings = "tnum",
                    ),
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
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
                            text = repoUrl.removePrefix("https://"),
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.clickable {
                                openUriSafe(repoUrl, "无法打开浏览器，请手动访问 GitHub 仓库")
                            },
                        )
                    }
                }

                // App update check card via GitHub Releases
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
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f),
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.SystemUpdate,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp),
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "版本更新检测",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                            }
                            if (updateState !is AboutUpdateUiState.Checking) {
                                FilledTonalButton(
                                    onClick = runUpdateCheck,
                                    shape = RoundedCornerShape(10.dp),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                    modifier = Modifier.height(30.dp),
                                ) {
                                    Text(
                                        text = if (updateState is AboutUpdateUiState.Idle) "检测更新" else "重新检测",
                                        style = MaterialTheme.typography.labelSmall,
                                    )
                                }
                            }
                        }

                        when (val st = updateState) {
                            AboutUpdateUiState.Idle -> {
                                Text(
                                    text = "当前版本 v${BuildConfig.VERSION_NAME} · 从 GitHub Releases 获取最新发布",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }

                            AboutUpdateUiState.Checking -> {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    LoadingIndicator(modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "正在连接 GitHub Releases…",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }

                            is AboutUpdateUiState.Done -> {
                                when (val res = st.result) {
                                    is UpdateCheckResult.UpdateAvailable -> {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                imageVector = Icons.Rounded.NewReleases,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(16.dp),
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = buildString {
                                                    append("发现新版本 ${res.latestVersion}")
                                                    if (res.isPreRelease) append(" (Preview)")
                                                    if (res.publishedDate.isNotBlank()) append(" · ${res.publishedDate}")
                                                },
                                                style = MaterialTheme.typography.labelMedium,
                                                color = MaterialTheme.colorScheme.primary,
                                            )
                                        }
                                        if (res.releaseNotes.isNotBlank()) {
                                            Text(
                                                text = res.releaseNotes.take(260),
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 5,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                        }
                                        val downloadTarget = res.apkDownloadUrl ?: res.releasePageUrl
                                        FilledTonalButton(
                                            onClick = { openUriSafe(downloadTarget, "无法打开更新链接") },
                                            shape = RoundedCornerShape(10.dp),
                                            modifier = Modifier.fillMaxWidth(),
                                        ) {
                                            Text(if (res.apkDownloadUrl != null) "下载最新安装包" else "前往 GitHub Releases 下载")
                                        }
                                    }

                                    is UpdateCheckResult.UpToDate -> {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                imageVector = Icons.Rounded.CheckCircle,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(16.dp),
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = if (!res.hasAnyRelease) {
                                                    "当前 v${res.currentVersion}（正式版尚未发布至 Releases）"
                                                } else {
                                                    "当前已是最新版本 v${res.currentVersion}（远端 ${res.latestTag}）"
                                                },
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurface,
                                            )
                                        }
                                    }

                                    is UpdateCheckResult.Error -> {
                                        Text(
                                            text = res.message,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.error,
                                        )
                                        Text(
                                            text = "查看 GitHub Releases 发布页 →",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.clickable {
                                                openUriSafe(res.releasePageUrl, "无法打开浏览器")
                                            },
                                        )
                                    }
                                }
                            }
                        }
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
            TextButton(onClick = onDismiss) {
                Text("关闭")
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    openUriSafe(repoUrl, "无法打开浏览器，请手动访问 GitHub 仓库")
                    onDismiss()
                },
            ) {
                Text("前往 GitHub 给个 Star")
            }
        },
    )
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
