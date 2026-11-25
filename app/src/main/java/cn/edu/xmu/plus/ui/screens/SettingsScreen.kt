package cn.edu.xmu.plus.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.core.content.ContextCompat
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.AutoAwesomeMotion
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.DynamicFeed
import androidx.compose.material.icons.rounded.Grain
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.TabletAndroid
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material.icons.rounded.Vibration
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cn.edu.xmu.plus.data.AccentTone
import cn.edu.xmu.plus.data.DarkModePreference
import cn.edu.xmu.plus.data.DockStyle
import cn.edu.xmu.plus.data.ThemeSettings
import cn.edu.xmu.plus.debug.DebugLogEntry
import cn.edu.xmu.plus.debug.DebugLogStore
import cn.edu.xmu.plus.ui.components.AmbientGlowBackground
import cn.edu.xmu.plus.ui.components.DarkModeSegmentedRow
import cn.edu.xmu.plus.ui.components.DetailOverlayClipShape
import cn.edu.xmu.plus.ui.components.DockStyleSegmentedRow
import cn.edu.xmu.plus.ui.components.FrostedGlassCard
import cn.edu.xmu.plus.ui.components.FrostedHairlineDivider
import cn.edu.xmu.plus.ui.components.FrostedIconButton
import cn.edu.xmu.plus.ui.components.FrostedToggleSwitch
import cn.edu.xmu.plus.ui.components.SheetGrabber
import cn.edu.xmu.plus.ui.components.bounceClickable
import cn.edu.xmu.plus.ui.components.optionalSharedBounds

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun SettingsScreen(
    settings: ThemeSettings,
    sourceTag: String,
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
    onSelectAccentTone: (AccentTone) -> Unit,
    onSelectDarkMode: (DarkModePreference) -> Unit,
    onSelectDockStyle: (DockStyle) -> Unit,
    onToggleHaptics: (Boolean) -> Unit,
    onToggleFilmGrain: (Boolean) -> Unit,
    onToggleSharedElement: (Boolean) -> Unit,
    onToggleDdlReminder: (Boolean) -> Unit,
    onToggleDebugMode: (Boolean) -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    var showDebugLogSheet by remember { mutableStateOf(false) }
    val debugLogs by DebugLogStore.entries.collectAsStateWithLifecycle()
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            onToggleDdlReminder(true)
        } else {
            Toast.makeText(context, "未授予通知权限，无法发送作业截止提醒", Toast.LENGTH_SHORT).show()
        }
    }
    val containerModifier = Modifier
        .fillMaxSize()
        .optionalSharedBounds(
            sharedTransitionScope = sharedTransitionScope,
            animatedVisibilityScope = animatedVisibilityScope,
            key = "settings_card_$sourceTag",
            clipShape = DetailOverlayClipShape,
        )

    Surface(
        modifier = containerModifier,
        color = MaterialTheme.colorScheme.background,
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            AmbientGlowBackground(animate = false)

            Scaffold(
                containerColor = Color.Transparent,
                topBar = {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .statusBarsPadding()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        FrostedIconButton(
                            icon = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "返回",
                            onClick = onBack,
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "外观与设置",
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.onBackground,
                            )
                            Text(
                                text = "极简精密线框 · 瑞士排版",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
            ) { innerPadding ->
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .navigationBarsPadding(),
                    contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 6.dp, bottom = 36.dp),
                    verticalArrangement = Arrangement.spacedBy(18.dp),
                ) {
                    // Live Miniature Preview Card
                    item {
                        FrostedGlassCard(
                            shape = RoundedCornerShape(20.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(11.dp),
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Box(
                                            modifier = Modifier
                                                .size(10.dp)
                                                .clip(CircleShape)
                                                .background(MaterialTheme.colorScheme.primary),
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = "${settings.accentTone.displayName} · ${settings.darkMode.label}",
                                            style = MaterialTheme.typography.titleMedium,
                                        )
                                    }
                                    Text(
                                        text = settings.dockStyle.label,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }

                                Text(
                                    text = "纯净纸白 / 曜石黑中性底色搭配 0.6dp 极细线框，莫兰迪色彩仅作为克制点缀色用于状态与核心操作。",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )

                                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Button(onClick = {}, shape = RoundedCornerShape(14.dp)) {
                                        Text("核心主按钮")
                                    }
                                    FilledTonalButton(onClick = {}, shape = RoundedCornerShape(14.dp)) {
                                        Text("次级按钮")
                                    }
                                }
                            }
                        }
                    }

                    // Restrained Morandi Accent Tone Picker
                    item {
                        Text(
                            text = "点缀色调",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onBackground,
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "克制收敛于状态小圆点、文字高亮与主按钮，保持画布纯净通透",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        AccentToneSelectorRow(
                            selected = settings.accentTone,
                            onSelect = onSelectAccentTone,
                        )
                    }

                    // Light / Dark Mode Switch
                    item {
                        Text(
                            text = "明暗主题",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onBackground,
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        DarkModeSegmentedRow(
                            selected = settings.darkMode,
                            onSelect = onSelectDarkMode,
                        )
                    }

                    // Dock Material Selector
                    item {
                        Text(
                            text = "底栏材质",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onBackground,
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "液态玻璃支持实时背景折射模糊，按住左右滑动并在松手时切换页面",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        DockStyleSegmentedRow(
                            selected = settings.dockStyle,
                            onSelect = onSelectDockStyle,
                        )
                    }

                    // Grouped Preferences
                    item(key = "preferences_section") {
                        Text(
                            text = "质感与交互",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onBackground,
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        FrostedGlassCard(
                            shape = RoundedCornerShape(20.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column {
                                Row(
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.AutoAwesomeMotion,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("共享元素容器形变", style = MaterialTheme.typography.titleSmall)
                                        Text(
                                            text = "开启后卡片原地放大展开，关闭则使用轻量流畅切页",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    FrostedToggleSwitch(
                                        checked = settings.sharedElementEnabled,
                                        onCheckedChange = onToggleSharedElement,
                                    )
                                }

                                FrostedHairlineDivider(startIndent = 52.dp)

                                Row(
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Grain,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("细腻纸质/胶片颗粒纹理", style = MaterialTheme.typography.titleSmall)
                                        Text(
                                            text = "AGSL 实时微弱噪点叠层，营造特种纸质感",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    FrostedToggleSwitch(
                                        checked = settings.filmGrainEnabled,
                                        onCheckedChange = onToggleFilmGrain,
                                    )
                                }

                                FrostedHairlineDivider(startIndent = 52.dp)

                                Row(
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Vibration,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("交互触觉反馈", style = MaterialTheme.typography.titleSmall)
                                        Text(
                                            text = "底栏滑动、按键与签到校验时触发轻震动",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    FrostedToggleSwitch(
                                        checked = settings.hapticsEnabled,
                                        onCheckedChange = onToggleHaptics,
                                    )
                                }

                                FrostedHairlineDivider(startIndent = 52.dp)

                                Row(
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.NotificationsActive,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("作业截止提醒通知", style = MaterialTheme.typography.titleSmall)
                                        Text(
                                            text = "在作业截止前 24 小时与 3 小时后台提醒",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    FrostedToggleSwitch(
                                        checked = settings.ddlReminderEnabled,
                                        onCheckedChange = { wantEnable ->
                                            if (!wantEnable) {
                                                onToggleDdlReminder(false)
                                            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                                                ContextCompat.checkSelfPermission(
                                                    context,
                                                    Manifest.permission.POST_NOTIFICATIONS,
                                                ) != PackageManager.PERMISSION_GRANTED
                                            ) {
                                                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                            } else {
                                                onToggleDdlReminder(true)
                                            }
                                        },
                                    )
                                }

                                FrostedHairlineDivider(startIndent = 52.dp)

                                Row(
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.BugReport,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("调试模式", style = MaterialTheme.typography.titleSmall)
                                        Text(
                                            text = "记录签到、扫码与接口诊断日志（自动脱敏）",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    FrostedToggleSwitch(
                                        checked = settings.debugModeEnabled,
                                        onCheckedChange = onToggleDebugMode,
                                    )
                                }

                                FrostedHairlineDivider(startIndent = 52.dp)

                                Row(
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.TabletAndroid,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("平板自适应布局", style = MaterialTheme.typography.titleSmall)
                                        Text(
                                            text = "已启用 · 屏幕宽度 ≥ 600dp 自动切换 NavigationRail 与双栏模式",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                            }
                        }

                        if (settings.debugModeEnabled) {
                            Spacer(modifier = Modifier.height(10.dp))
                            Surface(
                                shape = RoundedCornerShape(22.dp),
                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.72f),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .bounceClickable { showDebugLogSheet = true },
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Terminal,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "查看调试日志",
                                            style = MaterialTheme.typography.titleSmall,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        )
                                        Text(
                                            text = "已记录 ${debugLogs.size} 条 · 点击查看或一键复制（Tag: XmuPlusDebug）",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                                        )
                                    }
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showDebugLogSheet) {
        DebugLogViewerSheet(
            logs = debugLogs,
            onDismiss = { showDebugLogSheet = false },
        )
    }
}

@Composable
private fun AccentToneSelectorRow(
    selected: AccentTone,
    onSelect: (AccentTone) -> Unit,
) {
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        AccentTone.entries.forEach { tone ->
            val isSelected = tone == selected
            val dotColor = if (isDark) tone.darkPrimary else tone.lightPrimary
            val cardShape = RoundedCornerShape(16.dp)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(cardShape)
                    .background(
                        if (isSelected) {
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f)
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerLowest
                        },
                    )
                    .border(
                        width = if (isSelected) 1.dp else 0.6.dp,
                        color = if (isSelected) dotColor else MaterialTheme.colorScheme.outlineVariant,
                        shape = cardShape,
                    )
                    .bounceClickable(pressedScale = 0.97f) { onSelect(tone) }
                    .padding(vertical = 10.dp, horizontal = 4.dp),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(14.dp)
                            .clip(CircleShape)
                            .background(dotColor),
                    )
                    Text(
                        text = tone.displayName,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isSelected) {
                            MaterialTheme.colorScheme.onSurface
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DebugLogViewerSheet(
    logs: List<DebugLogEntry>,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val context = LocalContext.current
    @Suppress("DEPRECATION")
    val clipboard = LocalClipboardManager.current

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
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 20.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text("实时调试日志", style = MaterialTheme.typography.headlineSmall)
                    Text(
                        text = "共 ${logs.size} 条 · 最新日志在最上方",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Rounded.Close, contentDescription = "关闭")
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                FilledTonalButton(
                    onClick = {
                        clipboard.setText(AnnotatedString(DebugLogStore.exportText()))
                        Toast.makeText(context, "已复制全部日志", Toast.LENGTH_SHORT).show()
                    },
                    enabled = logs.isNotEmpty(),
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Rounded.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("复制全部")
                }
                FilledTonalButton(
                    onClick = { DebugLogStore.clear() },
                    enabled = logs.isNotEmpty(),
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Rounded.DeleteOutline, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("清空日志")
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            if (logs.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "暂无日志，进行刷新或扫码/密码签到后将自动记录",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                val reversed = remember(logs) { logs.asReversed() }
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(380.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    items(reversed, key = { it.id }) { entry ->
                        val isErr = entry.level == "ERR"
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = if (isErr) {
                                MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.7f)
                            } else {
                                MaterialTheme.colorScheme.surfaceContainerLowest
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text(
                                    text = "${entry.timeText}  [${entry.tag}]",
                                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                                    color = if (isErr) {
                                        MaterialTheme.colorScheme.onErrorContainer
                                    } else {
                                        MaterialTheme.colorScheme.primary
                                    },
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = entry.message,
                                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                                    color = if (isErr) {
                                        MaterialTheme.colorScheme.onErrorContainer
                                    } else {
                                        MaterialTheme.colorScheme.onSurface
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
