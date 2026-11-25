package cn.edu.xmu.plus.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.FactCheck
import androidx.compose.material.icons.rounded.Refresh
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.edu.xmu.plus.data.RollcallHistoryItem
import cn.edu.xmu.plus.data.RollcallHistoryUiState
import cn.edu.xmu.plus.data.RollcallRecordStatus
import cn.edu.xmu.plus.ui.components.AmbientGlowBackground
import cn.edu.xmu.plus.ui.components.ExpressiveGlidingSegmentedBar
import cn.edu.xmu.plus.ui.components.FrostedGlassCard
import cn.edu.xmu.plus.ui.components.FrostedIconButton

private enum class RollcallFilter(val label: String) {
    ALL("全部"),
    ON_CALL("已签到"),
    ABNORMAL("迟到/假"),
    ABSENT("缺勤"),
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun RollcallHistoryScreen(
    uiState: RollcallHistoryUiState,
    isDemoMode: Boolean,
    onRefresh: (force: Boolean) -> Unit,
    onBack: () -> Unit,
) {
    var selectedFilter by remember { mutableStateOf(RollcallFilter.ALL) }
    var selectedCourseId by remember { mutableStateOf<Long?>(null) }
    val pullState = rememberPullToRefreshState()

    LaunchedEffect(Unit) {
        onRefresh(false)
    }

    val courseChoices = remember(uiState.records) {
        uiState.records
            .distinctBy { it.courseId }
            .map { it.courseId to it.courseName }
    }

    val courseScoped = remember(uiState.records, selectedCourseId) {
        if (selectedCourseId == null) uiState.records
        else uiState.records.filter { it.courseId == selectedCourseId }
    }

    val stats = remember(courseScoped) {
        val settled = courseScoped.filter { it.studentStatus != RollcallRecordStatus.IN_PROGRESS }
        val onCall = settled.count { it.studentStatus == RollcallRecordStatus.ON_CALL }
        val abnormal = settled.count {
            it.studentStatus == RollcallRecordStatus.LATE ||
                it.studentStatus == RollcallRecordStatus.EARLY_LEAVE ||
                it.studentStatus == RollcallRecordStatus.LATE_AND_EARLY ||
                it.studentStatus == RollcallRecordStatus.ON_LEAVE
        }
        val absent = settled.count { it.studentStatus == RollcallRecordStatus.ABSENT }
        val rate = if (settled.isEmpty()) "--" else "${((onCall * 100f) / settled.size).toInt()}%"
        QuadStats(rate = rate, onCall = onCall, abnormal = abnormal, absent = absent)
    }

    val filtered = remember(courseScoped, selectedFilter) {
        when (selectedFilter) {
            RollcallFilter.ALL -> courseScoped
            RollcallFilter.ON_CALL -> courseScoped.filter { it.studentStatus == RollcallRecordStatus.ON_CALL }
            RollcallFilter.ABNORMAL -> courseScoped.filter {
                it.studentStatus == RollcallRecordStatus.LATE ||
                    it.studentStatus == RollcallRecordStatus.EARLY_LEAVE ||
                    it.studentStatus == RollcallRecordStatus.LATE_AND_EARLY ||
                    it.studentStatus == RollcallRecordStatus.ON_LEAVE
            }
            RollcallFilter.ABSENT -> courseScoped.filter { it.studentStatus == RollcallRecordStatus.ABSENT }
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            AmbientGlowBackground(animate = false)

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding(),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    FrostedIconButton(
                        icon = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = "返回",
                        onClick = onBack,
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "签到记录",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onBackground,
                        )
                        Text(
                            text = buildString {
                                append("共 ${uiState.records.size} 条")
                                if (isDemoMode) append(" · 演示数据")
                                else uiState.lastSyncText?.let { append(" · $it") }
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                        )
                    }
                    FrostedIconButton(
                        icon = Icons.Rounded.Refresh,
                        contentDescription = "刷新签到记录",
                        onClick = { onRefresh(true) },
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }

                // Summary Bento Strip
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    RollcallStatTile("出勤率", stats.rate, MaterialTheme.colorScheme.primary, Modifier.weight(1f))
                    RollcallStatTile("已签到", stats.onCall.toString(), MaterialTheme.colorScheme.primary, Modifier.weight(1f))
                    RollcallStatTile("迟到/假", stats.abnormal.toString(), MaterialTheme.colorScheme.tertiary, Modifier.weight(1f))
                    RollcallStatTile("缺勤", stats.absent.toString(), MaterialTheme.colorScheme.error, Modifier.weight(1f))
                }

                Spacer(modifier = Modifier.height(12.dp))

                ExpressiveGlidingSegmentedBar(
                    items = RollcallFilter.entries,
                    selected = selectedFilter,
                    onSelect = { selectedFilter = it },
                    labelProvider = { it.label },
                    height = 38.dp,
                    modifier = Modifier.padding(horizontal = 20.dp),
                )

                if (courseChoices.size > 1) {
                    Spacer(modifier = Modifier.height(10.dp))
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        item {
                            CourseFilterChip(
                                label = "全部课程",
                                selected = selectedCourseId == null,
                                onClick = { selectedCourseId = null },
                            )
                        }
                        items(courseChoices, key = { it.first }) { (cid, cname) ->
                            CourseFilterChip(
                                label = cname,
                                selected = selectedCourseId == cid,
                                onClick = {
                                    selectedCourseId = if (selectedCourseId == cid) null else cid
                                },
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                PullToRefreshBox(
                    isRefreshing = uiState.isLoading,
                    onRefresh = { onRefresh(true) },
                    state = pullState,
                    indicator = {
                        PullToRefreshDefaults.LoadingIndicator(
                            state = pullState,
                            isRefreshing = uiState.isLoading,
                            modifier = Modifier.align(Alignment.TopCenter),
                        )
                    },
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .clipToBounds(),
                ) {
                    if (filtered.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(24.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Rounded.FactCheck,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
                                    modifier = Modifier.size(34.dp),
                                )
                                Text(
                                    text = if (uiState.isLoading) "正在同步各门课程签到记录…" else "暂无匹配的签到记录",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxSize()
                                .clipToBounds(),
                            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 2.dp, bottom = 28.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            items(filtered, key = { "${it.courseId}_${it.id}" }) { record ->
                                RollcallRecordCard(record = record)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun RollcallRecordCard(
    record: RollcallHistoryItem,
    showCourseName: Boolean = true,
) {
    val statusColor = rollcallStatusColor(record.studentStatus)
    FrostedGlassCard(
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height(40.dp)
                    .clip(CircleShape)
                    .background(statusColor),
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                if (showCourseName) {
                    Text(
                        text = record.courseName,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                }
                Text(
                    text = record.title,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = buildString {
                        append(record.rollcallTimeText)
                        append(" · ")
                        append(record.rollcallMethod)
                        if (record.detailNote.isNotBlank() && record.detailNote != record.studentStatus.label) {
                            append(" · ")
                            append(record.detailNote)
                        }
                    },
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = FontFamily.Monospace,
                        fontFeatureSettings = "tnum",
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(modifier = Modifier.width(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(5.dp)
                        .clip(CircleShape)
                        .background(statusColor),
                )
                Spacer(modifier = Modifier.width(5.dp))
                Text(
                    text = record.studentStatus.label,
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = statusColor,
                )
            }
        }
    }
}

@Composable
private fun rollcallStatusColor(status: RollcallRecordStatus): Color {
    return when (status) {
        RollcallRecordStatus.ON_CALL -> MaterialTheme.colorScheme.primary
        RollcallRecordStatus.IN_PROGRESS -> MaterialTheme.colorScheme.primary
        RollcallRecordStatus.LATE,
        RollcallRecordStatus.EARLY_LEAVE,
        RollcallRecordStatus.LATE_AND_EARLY,
        RollcallRecordStatus.ON_LEAVE,
        -> MaterialTheme.colorScheme.tertiary
        RollcallRecordStatus.ABSENT -> MaterialTheme.colorScheme.error
        RollcallRecordStatus.UNKNOWN -> MaterialTheme.colorScheme.onSurfaceVariant
    }
}

@Composable
private fun RollcallStatTile(
    label: String,
    value: String,
    valueColor: Color,
    modifier: Modifier = Modifier,
) {
    FrostedGlassCard(
        shape = RoundedCornerShape(16.dp),
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge.copy(
                    fontSize = 19.sp,
                    fontFamily = FontFamily.Monospace,
                    fontFeatureSettings = "tnum",
                    fontWeight = FontWeight.Bold,
                ),
                color = valueColor,
                maxLines = 1,
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun CourseFilterChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (selected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerLowest
        },
        border = BorderStroke(
            width = if (selected) 0.9.dp else 0.6.dp,
            color = if (selected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.outlineVariant
            },
        ),
        modifier = Modifier.clickable(onClick = onClick),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 11.dp, vertical = 6.dp),
        )
    }
}

private data class QuadStats(
    val rate: String,
    val onCall: Int,
    val abnormal: Int,
    val absent: Int,
)
