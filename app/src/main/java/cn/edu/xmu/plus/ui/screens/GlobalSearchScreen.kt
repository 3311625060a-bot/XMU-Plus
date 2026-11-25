package cn.edu.xmu.plus.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Assignment
import androidx.compose.material.icons.rounded.AutoStories
import androidx.compose.material.icons.rounded.Campaign
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cn.edu.xmu.plus.data.CourseDetailUiState
import cn.edu.xmu.plus.data.GlobalSearchEngine
import cn.edu.xmu.plus.data.GlobalSearchHit
import cn.edu.xmu.plus.data.SearchCategory
import cn.edu.xmu.plus.data.XmuDashboardState
import cn.edu.xmu.plus.ui.components.AmbientGlowBackground
import cn.edu.xmu.plus.ui.components.ExpressiveGlidingSegmentedBar
import cn.edu.xmu.plus.ui.components.FrostedGlassCard
import cn.edu.xmu.plus.ui.components.FrostedIconButton
import cn.edu.xmu.plus.ui.components.FrostedSearchBar
import cn.edu.xmu.plus.ui.components.bounceClickable

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun GlobalSearchScreen(
    dashboardState: XmuDashboardState,
    courseDetails: Map<Long, CourseDetailUiState>,
    isIndexingSearch: Boolean,
    onIndexAllResources: () -> Unit,
    onOpenHomeworkDetail: (Long) -> Unit,
    onOpenCourse: (Long) -> Unit,
    onOpenAttachment: (Long, String) -> Unit,
    onBack: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var category by remember { mutableStateOf(SearchCategory.ALL) }

    val uncachedCourseCount = remember(dashboardState.courses, courseDetails) {
        if (dashboardState.isDemoMode) 0
        else dashboardState.courses.count { courseDetails[it.id] == null }
    }

    val hits = remember(query, category, dashboardState, courseDetails) {
        GlobalSearchEngine.search(
            query = query,
            category = category,
            dashboardState = dashboardState,
            courseDetails = courseDetails,
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AmbientGlowBackground(animate = false)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding(),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
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
                        text = "跨课程全局搜索",
                        style = MaterialTheme.typography.titleLarge,
                    )
                    Text(
                        text = "检索课程、作业、课件与公告",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            FrostedSearchBar(
                query = query,
                onQueryChange = { query = it },
                placeholder = "输入关键词，如 微积分、实验、PDF…",
                modifier = Modifier.padding(horizontal = 20.dp),
            )

            Spacer(modifier = Modifier.height(12.dp))

            ExpressiveGlidingSegmentedBar(
                items = SearchCategory.entries,
                selected = category,
                onSelect = { category = it },
                labelProvider = { it.label },
                height = 38.dp,
                modifier = Modifier.padding(horizontal = 20.dp),
            )

            if (uncachedCourseCount > 0 || isIndexingSearch) {
                Spacer(modifier = Modifier.height(10.dp))
                FrostedGlassCard(
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (isIndexingSearch) {
                                    "正在加载全部课程的课件与公告…"
                                } else {
                                    "尚有 $uncachedCourseCount 门课程的课件/公告未缓存"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (isIndexingSearch) {
                            LoadingIndicator(modifier = Modifier.size(22.dp))
                        } else {
                            FilledTonalButton(
                                onClick = onIndexAllResources,
                                shape = RoundedCornerShape(12.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Sync,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("索引全部")
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            when {
                query.isBlank() -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Rounded.Search,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.55f),
                                modifier = Modifier.size(44.dp),
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = "输入关键词即可跨课程检索",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "已加载 ${dashboardState.courses.size} 门课程 · ${dashboardState.homeworks.size} 项作业 · ${courseDetails.size} 门课程资料",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }

                hits.isEmpty() -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "未找到与“$query”相关的结果",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 28.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        itemsIndexed(hits, key = { _, hit -> hit.stableKey }) { _, hit ->
                            SearchHitRow(
                                hit = hit,
                                shape = RoundedCornerShape(18.dp),
                                onClick = {
                                    when (hit) {
                                        is GlobalSearchHit.HomeworkHit -> onOpenHomeworkDetail(hit.homework.id)
                                        is GlobalSearchHit.CourseHit -> onOpenCourse(hit.course.id)
                                        is GlobalSearchHit.CoursewareHit -> {
                                            if (hit.courseware.uploadId > 0L) {
                                                onOpenAttachment(hit.courseware.uploadId, hit.courseware.title)
                                            } else {
                                                onOpenCourse(hit.courseware.courseId)
                                            }
                                        }
                                        is GlobalSearchHit.BulletinHit -> onOpenCourse(hit.bulletin.courseId)
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

@Composable
private fun SearchHitRow(
    hit: GlobalSearchHit,
    shape: RoundedCornerShape,
    onClick: () -> Unit,
) {
    val (icon, tintBg, tintFg) = when (hit) {
        is GlobalSearchHit.CourseHit -> Triple(
            Icons.Rounded.AutoStories,
            MaterialTheme.colorScheme.primaryContainer,
            MaterialTheme.colorScheme.onPrimaryContainer,
        )
        is GlobalSearchHit.HomeworkHit -> Triple(
            Icons.AutoMirrored.Rounded.Assignment,
            MaterialTheme.colorScheme.tertiaryContainer,
            MaterialTheme.colorScheme.onTertiaryContainer,
        )
        is GlobalSearchHit.CoursewareHit -> Triple(
            Icons.Rounded.Description,
            MaterialTheme.colorScheme.secondaryContainer,
            MaterialTheme.colorScheme.onSecondaryContainer,
        )
        is GlobalSearchHit.BulletinHit -> Triple(
            Icons.Rounded.Campaign,
            MaterialTheme.colorScheme.secondaryContainer,
            MaterialTheme.colorScheme.onSecondaryContainer,
        )
    }

    FrostedGlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .bounceClickable(onClick = onClick),
        shape = shape,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(tintBg),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = tintFg,
                    modifier = Modifier.size(18.dp),
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = hit.title,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = hit.subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
