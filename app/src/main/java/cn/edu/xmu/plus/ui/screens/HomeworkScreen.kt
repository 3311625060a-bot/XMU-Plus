package cn.edu.xmu.plus.ui.screens

import android.net.Uri
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.rounded.AttachFile
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cn.edu.xmu.plus.data.HomeworkDetailUiState
import cn.edu.xmu.plus.data.HomeworkItem
import cn.edu.xmu.plus.data.HomeworkStatus
import cn.edu.xmu.plus.data.PickedLocalFile
import cn.edu.xmu.plus.data.TimeFormatUtils
import cn.edu.xmu.plus.data.XmuDashboardState
import cn.edu.xmu.plus.ui.components.AmbientGlowBackground
import cn.edu.xmu.plus.ui.components.ExpressiveGlidingSegmentedBar
import cn.edu.xmu.plus.ui.components.FrostedGlassCard
import cn.edu.xmu.plus.ui.components.bounceClickable
import cn.edu.xmu.plus.ui.components.optionalSharedBounds

enum class HomeworkFilter(val label: String) {
    ALL("全部"),
    PENDING("待提交"),
    SUBMITTED("已提交"),
    GRADED("已批改"),
}

enum class HomeworkSortOrder(val label: String, val shortLabel: String) {
    PENDING_FIRST("待交优先 · 紧迫度排序", "待交优先"),
    DEADLINE_ASC("截止时间 · 最早优先", "截止最早"),
    DEADLINE_DESC("截止时间 · 最新优先", "最新截止"),
    BY_COURSE("课程名称 · 分组排序", "按课程"),
}

private fun sortHomeworks(
    items: List<HomeworkItem>,
    order: HomeworkSortOrder,
): List<HomeworkItem> {
    if (items.size <= 1) return items
    fun deadlineMillisAsc(item: HomeworkItem): Long =
        TimeFormatUtils.parseIsoInstant(item.endTimeIso)?.toEpochMilli() ?: Long.MAX_VALUE

    fun deadlineMillisDesc(item: HomeworkItem): Long =
        TimeFormatUtils.parseIsoInstant(item.endTimeIso)?.toEpochMilli() ?: Long.MIN_VALUE

    return when (order) {
        HomeworkSortOrder.PENDING_FIRST -> items.sortedWith(
            compareBy<HomeworkItem>(
                { it.status.ordinal },
                { it.urgency.ordinal },
                { deadlineMillisAsc(it) },
                { it.id },
            ),
        )
        HomeworkSortOrder.DEADLINE_ASC -> items.sortedWith(
            compareBy<HomeworkItem>(
                { deadlineMillisAsc(it) },
                { it.urgency.ordinal },
                { it.status.ordinal },
                { it.id },
            ),
        )
        HomeworkSortOrder.DEADLINE_DESC -> items.sortedWith(
            compareByDescending<HomeworkItem> { deadlineMillisDesc(it) }
                .thenBy { it.status.ordinal }
                .thenBy { it.id },
        )
        HomeworkSortOrder.BY_COURSE -> items.sortedWith(
            compareBy<HomeworkItem>(
                { it.courseName },
                { it.status.ordinal },
                { deadlineMillisAsc(it) },
                { it.id },
            ),
        )
    }
}

@kotlin.OptIn(
    ExperimentalSharedTransitionApi::class,
    ExperimentalMaterial3Api::class,
    ExperimentalMaterial3ExpressiveApi::class,
)
@Composable
fun HomeworkScreen(
    dashboardState: XmuDashboardState,
    selectedFilter: HomeworkFilter,
    onSelectFilter: (HomeworkFilter) -> Unit,
    onRefreshAll: () -> Unit,
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
    onOpenHomeworkDetail: (Long, String) -> Unit,
    isTabletLayout: Boolean = false,
    homeworkDetails: Map<Long, HomeworkDetailUiState> = emptyMap(),
    onLoadHomeworkDetail: (Long) -> Unit = {},
    onOpenAttachment: (Long, String) -> Unit = { _, _ -> },
    onResolvePickedFile: (Uri) -> PickedLocalFile,
    onSubmitHomework: suspend (Long, String, List<PickedLocalFile>, Long?, (Float, String) -> Unit) -> Result<Unit>,
) {
    val pullState = rememberPullToRefreshState()
    var sortOrder by remember { mutableStateOf(HomeworkSortOrder.PENDING_FIRST) }
    var sortMenuExpanded by remember { mutableStateOf(false) }

    val filtered = remember(selectedFilter, sortOrder, dashboardState.homeworks) {
        val subset = when (selectedFilter) {
            HomeworkFilter.ALL -> dashboardState.homeworks
            HomeworkFilter.PENDING -> dashboardState.homeworks.filter { it.status == HomeworkStatus.PENDING }
            HomeworkFilter.SUBMITTED -> dashboardState.homeworks.filter { it.status == HomeworkStatus.SUBMITTED }
            HomeworkFilter.GRADED -> dashboardState.homeworks.filter { it.status == HomeworkStatus.GRADED }
        }
        sortHomeworks(subset, sortOrder)
    }

    var tabletSelectedId by remember { mutableStateOf<Long?>(null) }
    val activeDetailItem = remember(filtered, tabletSelectedId, isTabletLayout) {
        if (!isTabletLayout) null
        else filtered.find { it.id == tabletSelectedId } ?: filtered.firstOrNull()
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AmbientGlowBackground(scrollOffsetPx = { 0f })

        Row(modifier = Modifier.fillMaxSize()) {
            PullToRefreshBox(
                isRefreshing = dashboardState.isRefreshing,
                onRefresh = onRefreshAll,
                state = pullState,
                indicator = {
                    PullToRefreshDefaults.LoadingIndicator(
                        state = pullState,
                        isRefreshing = dashboardState.isRefreshing,
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .statusBarsPadding(),
                    )
                },
                modifier = if (isTabletLayout) {
                    Modifier
                        .weight(0.45f)
                        .fillMaxHeight()
                } else {
                    Modifier.fillMaxSize()
                },
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .statusBarsPadding(),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "作业中心",
                                style = MaterialTheme.typography.headlineMedium,
                                color = MaterialTheme.colorScheme.onBackground,
                            )
                            Text(
                                text = if (dashboardState.isDemoMode) {
                                    "演示数据 · 支持筛选与排序切换"
                                } else {
                                    "共 ${dashboardState.homeworks.size} 项 · ${sortOrder.shortLabel}"
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Box {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.surfaceContainer,
                                border = BorderStroke(0.8.dp, MaterialTheme.colorScheme.outlineVariant),
                                modifier = Modifier.bounceClickable { sortMenuExpanded = true },
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 11.dp, vertical = 7.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Rounded.Sort,
                                        contentDescription = "排序方式",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(16.dp),
                                    )
                                    Spacer(modifier = Modifier.width(5.dp))
                                    Text(
                                        text = sortOrder.shortLabel,
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurface,
                                    )
                                    Spacer(modifier = Modifier.width(2.dp))
                                    Icon(
                                        imageVector = Icons.Rounded.KeyboardArrowDown,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(15.dp),
                                    )
                                }
                            }

                            DropdownMenu(
                                expanded = sortMenuExpanded,
                                onDismissRequest = { sortMenuExpanded = false },
                            ) {
                                HomeworkSortOrder.entries.forEach { option ->
                                    val isCurrent = option == sortOrder
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                text = option.label,
                                                fontWeight = if (isCurrent) FontWeight.SemiBold else FontWeight.Normal,
                                                color = if (isCurrent) {
                                                    MaterialTheme.colorScheme.primary
                                                } else {
                                                    MaterialTheme.colorScheme.onSurface
                                                },
                                            )
                                        },
                                        leadingIcon = {
                                            if (isCurrent) {
                                                Icon(
                                                    imageVector = Icons.Rounded.Check,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.size(18.dp),
                                                )
                                            }
                                        },
                                        onClick = {
                                            sortOrder = option
                                            sortMenuExpanded = false
                                        },
                                    )
                                }
                            }
                        }
                    }

                    ExpressiveGlidingSegmentedBar(
                        items = HomeworkFilter.entries,
                        selected = selectedFilter,
                        onSelect = onSelectFilter,
                        labelProvider = { it.label },
                        height = 38.dp,
                        modifier = Modifier.padding(horizontal = 20.dp),
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    AnimatedContent(
                        targetState = selectedFilter to sortOrder,
                        transitionSpec = {
                            fadeIn(tween(110)) togetherWith fadeOut(tween(80))
                        },
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .clipToBounds(),
                        label = "hwFilterTab",
                    ) { (tabFilter, tabSort) ->
                        val tabFiltered = remember(tabFilter, tabSort, dashboardState.homeworks) {
                            val subset = when (tabFilter) {
                                HomeworkFilter.ALL -> dashboardState.homeworks
                                HomeworkFilter.PENDING -> dashboardState.homeworks.filter { it.status == HomeworkStatus.PENDING }
                                HomeworkFilter.SUBMITTED -> dashboardState.homeworks.filter { it.status == HomeworkStatus.SUBMITTED }
                                HomeworkFilter.GRADED -> dashboardState.homeworks.filter { it.status == HomeworkStatus.GRADED }
                            }
                            sortHomeworks(subset, tabSort)
                        }
                        val tabListState = rememberLazyListState()

                        if (tabFiltered.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 24.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = if (dashboardState.isRefreshing) "正在同步课程作业…" else "该分类下暂无作业",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        } else {
                            LazyColumn(
                                state = tabListState,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clipToBounds(),
                                contentPadding = PaddingValues(
                                    start = 20.dp,
                                    end = 20.dp,
                                    top = 2.dp,
                                    bottom = if (isTabletLayout) 28.dp else 106.dp,
                                ),
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                itemsIndexed(tabFiltered, key = { _, item -> item.id }) { _, item ->
                                    val shape = RoundedCornerShape(20.dp)
                                    val isSelected = isTabletLayout && activeDetailItem?.id == item.id
                                    val sharedModifier = if (isTabletLayout) {
                                        Modifier
                                    } else {
                                        Modifier.optionalSharedBounds(
                                            sharedTransitionScope = sharedTransitionScope,
                                            animatedVisibilityScope = animatedVisibilityScope,
                                            key = "hw_card_list_${item.id}",
                                            clipShape = shape,
                                        )
                                    }
                                    HomeworkListCard(
                                        item = item,
                                        shape = shape,
                                        isSelected = isSelected,
                                        onClick = {
                                            if (isTabletLayout) {
                                                tabletSelectedId = item.id
                                            } else {
                                                onOpenHomeworkDetail(item.id, "list")
                                            }
                                        },
                                        modifier = sharedModifier,
                                    )
                                }
                            }
                        }
                    }
                }
            }

            if (isTabletLayout) {
                FrostedGlassCard(
                    shape = RoundedCornerShape(24.dp),
                    modifier = Modifier
                        .weight(0.55f)
                        .fillMaxHeight()
                        .statusBarsPadding()
                        .navigationBarsPadding()
                        .padding(top = 12.dp, end = 20.dp, bottom = 14.dp),
                ) {
                    if (activeDetailItem != null) {
                        HomeworkDetailPane(
                            homeworkId = activeDetailItem.id,
                            isDemoMode = dashboardState.isDemoMode,
                            detailState = homeworkDetails[activeDetailItem.id],
                            fallbackHomework = activeDetailItem,
                            onLoadHomeworkDetail = onLoadHomeworkDetail,
                            onOpenAttachment = onOpenAttachment,
                            onResolvePickedFile = onResolvePickedFile,
                            onSubmitHomework = onSubmitHomework,
                            showBackButton = false,
                        )
                    } else {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = "请在左侧选择一项作业查看详情",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeworkListCard(
    item: HomeworkItem,
    shape: RoundedCornerShape,
    isSelected: Boolean = false,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val statusColor = when (item.status) {
        HomeworkStatus.PENDING -> MaterialTheme.colorScheme.error
        HomeworkStatus.SUBMITTED -> MaterialTheme.colorScheme.secondary
        HomeworkStatus.GRADED -> MaterialTheme.colorScheme.primary
    }

    FrostedGlassCard(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (isSelected) {
                    Modifier.border(
                        BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
                        shape,
                    )
                } else {
                    Modifier
                },
            )
            .bounceClickable(onClick = onClick),
        shape = shape,
        containerColor = if (isSelected) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
        } else {
            null
        },
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Left narrow vertical status indicator bar
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height(44.dp)
                    .clip(CircleShape)
                    .background(statusColor),
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = item.courseName,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f),
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(5.dp)
                                .clip(CircleShape)
                                .background(statusColor),
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            text = item.score?.let { sc ->
                                val suffix = item.fullScore?.takeIf { it.isNotBlank() && !sc.contains("/") }
                                    ?.let { "/$it" }
                                    .orEmpty()
                                "$sc$suffix"
                            } ?: item.status.label,
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontFamily = FontFamily.Monospace,
                                fontFeatureSettings = "tnum",
                                fontWeight = FontWeight.SemiBold,
                            ),
                            color = statusColor,
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "${item.deadlineText} · ${item.remainingText}",
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = FontFamily.Monospace,
                            fontFeatureSettings = "tnum",
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (item.attachments.isNotEmpty()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Rounded.AttachFile,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(13.dp),
                            )
                            Spacer(modifier = Modifier.width(2.dp))
                            Text(
                                text = "${item.attachments.size}",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontFamily = FontFamily.Monospace,
                                ),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}
