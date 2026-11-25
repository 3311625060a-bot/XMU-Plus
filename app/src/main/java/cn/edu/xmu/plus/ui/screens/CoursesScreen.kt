package cn.edu.xmu.plus.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoStories
import androidx.compose.material.icons.rounded.Campaign
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.edu.xmu.plus.data.CourseDetailUiState
import cn.edu.xmu.plus.data.CourseItem
import cn.edu.xmu.plus.data.DemoData
import cn.edu.xmu.plus.data.RollcallHistoryUiState
import cn.edu.xmu.plus.data.XmuDashboardState
import cn.edu.xmu.plus.ui.components.AmbientGlowBackground
import cn.edu.xmu.plus.ui.components.FrostedGlassCard
import cn.edu.xmu.plus.ui.components.FrostedSearchBar
import cn.edu.xmu.plus.ui.components.SheetGrabber
import cn.edu.xmu.plus.ui.components.bounceClickable

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun CoursesScreen(
    dashboardState: XmuDashboardState,
    courseDetails: Map<Long, CourseDetailUiState>,
    rollcallHistory: RollcallHistoryUiState,
    onSelectSemester: (Long?) -> Unit,
    onRefreshAll: () -> Unit,
    onLoadCourseDetail: (CourseItem) -> Unit,
    onLoadRollcallHistory: (Boolean) -> Unit,
    onOpenAttachment: (Long, String) -> Unit,
    onOpenHomeworkDetail: (Long, String) -> Unit,
    isTabletLayout: Boolean = false,
    initialCourseId: Long? = null,
    onConsumeInitialCourse: () -> Unit = {},
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedCourse by remember { mutableStateOf<CourseItem?>(null) }
    var semesterMenuExpanded by remember { mutableStateOf(false) }
    val pullState = rememberPullToRefreshState()
    val listState = rememberLazyListState()
    val scrollOffsetPx by remember {
        derivedStateOf {
            if (listState.firstVisibleItemIndex == 0) {
                listState.firstVisibleItemScrollOffset.toFloat()
            } else {
                550f
            }
        }
    }

    LaunchedEffect(initialCourseId, dashboardState.courses) {
        if (initialCourseId != null) {
            val found = dashboardState.courses.find { it.id == initialCourseId }
            if (found != null) {
                selectedCourse = found
            }
            onConsumeInitialCourse()
        }
    }

    val currentSemesterName = remember(dashboardState.semesters, dashboardState.selectedSemesterId) {
        dashboardState.semesters.find { it.id == dashboardState.selectedSemesterId }?.name
            ?: dashboardState.courses.firstOrNull()?.semester
            ?: "全部学期"
    }

    val filteredCourses = remember(searchQuery, dashboardState.courses) {
        if (searchQuery.isBlank()) {
            dashboardState.courses
        } else {
            dashboardState.courses.filter {
                it.name.contains(searchQuery, ignoreCase = true) ||
                    it.teacher.contains(searchQuery, ignoreCase = true) ||
                    it.code.contains(searchQuery, ignoreCase = true)
            }
        }
    }

    val activeTabletCourse = remember(isTabletLayout, selectedCourse, filteredCourses) {
        if (!isTabletLayout) null
        else filteredCourses.find { it.id == selectedCourse?.id } ?: filteredCourses.firstOrNull()
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AmbientGlowBackground(scrollOffsetPx = { scrollOffsetPx })

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
                    Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "我的课程",
                                style = MaterialTheme.typography.headlineMedium,
                            )
                            Box {
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = MaterialTheme.colorScheme.surfaceContainerLowest,
                                    border = BorderStroke(0.6.dp, MaterialTheme.colorScheme.outlineVariant),
                                    modifier = Modifier.clickable(enabled = dashboardState.semesters.isNotEmpty()) {
                                        semesterMenuExpanded = true
                                    },
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Text(
                                            text = currentSemesterName,
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.onSurface,
                                        )
                                        if (dashboardState.semesters.isNotEmpty()) {
                                            Spacer(modifier = Modifier.width(2.dp))
                                            Icon(
                                                imageVector = Icons.Rounded.KeyboardArrowDown,
                                                contentDescription = "切换学期",
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.size(16.dp),
                                            )
                                        }
                                    }
                                }
                                DropdownMenu(
                                    expanded = semesterMenuExpanded,
                                    onDismissRequest = { semesterMenuExpanded = false },
                                ) {
                                    dashboardState.semesters.forEach { sem ->
                                        DropdownMenuItem(
                                            text = { Text(sem.name) },
                                            onClick = {
                                                semesterMenuExpanded = false
                                                onSelectSemester(sem.id)
                                            },
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                        FrostedSearchBar(
                            query = searchQuery,
                            onQueryChange = { searchQuery = it },
                            placeholder = "搜索课程名、教师或代码",
                        )
                    }

                    if (filteredCourses.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(24.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = if (dashboardState.isRefreshing) "正在同步本学期课程…" else "未找到匹配的课程",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    } else {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier
                                .fillMaxSize()
                                .clipToBounds(),
                            contentPadding = PaddingValues(
                                start = 20.dp,
                                end = 20.dp,
                                top = 4.dp,
                                bottom = if (isTabletLayout) 28.dp else 106.dp,
                            ),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            itemsIndexed(filteredCourses, key = { _, course -> course.id }) { _, course ->
                                CourseListCard(
                                    course = course,
                                    shape = RoundedCornerShape(20.dp),
                                    isSelected = isTabletLayout && activeTabletCourse?.id == course.id,
                                    onClick = { selectedCourse = course },
                                )
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
                    if (activeTabletCourse != null) {
                        CourseDetailTabsContent(
                            course = activeTabletCourse,
                            detailState = courseDetails[activeTabletCourse.id],
                            rollcallHistory = rollcallHistory,
                            isDemoMode = dashboardState.isDemoMode,
                            onLoadDetail = { onLoadCourseDetail(activeTabletCourse) },
                            onLoadRollcallHistory = onLoadRollcallHistory,
                            onOpenAttachment = onOpenAttachment,
                            onOpenHomework = { hwId -> onOpenHomeworkDetail(hwId, "course") },
                            onClose = null,
                        )
                    } else {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = "请在左侧选择一门课程查看资料",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }

    if (!isTabletLayout) {
        selectedCourse?.let { course ->
            CoursePreviewSheet(
                course = course,
                detailState = courseDetails[course.id],
                rollcallHistory = rollcallHistory,
                isDemoMode = dashboardState.isDemoMode,
                onLoadDetail = { onLoadCourseDetail(course) },
                onLoadRollcallHistory = onLoadRollcallHistory,
                onOpenAttachment = onOpenAttachment,
                onDismiss = { selectedCourse = null },
                onOpenHomework = { hwId ->
                    selectedCourse = null
                    onOpenHomeworkDetail(hwId, "course")
                },
            )
        }
    }
}

@Composable
private fun CourseListCard(
    course: CourseItem,
    shape: RoundedCornerShape,
    isSelected: Boolean = false,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
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
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(13.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Rounded.AutoStories,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(19.dp),
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = course.name,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    if (course.isPinned) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Icon(
                            imageVector = Icons.Rounded.PushPin,
                            contentDescription = "置顶",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(14.dp),
                        )
                    }
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "${course.teacher} · ${course.schedule}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Rounded.LocationOn,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(12.dp),
                    )
                    Spacer(modifier = Modifier.width(3.dp))
                    Text(
                        text = course.location,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            val badgeCount = if (course.pendingHomeworkCount > 0) course.pendingHomeworkCount else course.unreadCount
            if (badgeCount > 0) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(5.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary),
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(
                        text = badgeCount.toString(),
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontSize = 15.sp,
                            fontFamily = FontFamily.Monospace,
                            fontFeatureSettings = "tnum",
                            fontWeight = FontWeight.Bold,
                        ),
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CoursePreviewSheet(
    course: CourseItem,
    detailState: CourseDetailUiState?,
    rollcallHistory: RollcallHistoryUiState,
    isDemoMode: Boolean,
    onLoadDetail: () -> Unit,
    onLoadRollcallHistory: (Boolean) -> Unit,
    onOpenAttachment: (Long, String) -> Unit,
    onDismiss: () -> Unit,
    onOpenHomework: (Long) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        dragHandle = { SheetGrabber() },
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.96f),
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
    ) {
        CourseDetailTabsContent(
            course = course,
            detailState = detailState,
            rollcallHistory = rollcallHistory,
            isDemoMode = isDemoMode,
            onLoadDetail = onLoadDetail,
            onLoadRollcallHistory = onLoadRollcallHistory,
            onOpenAttachment = onOpenAttachment,
            onOpenHomework = onOpenHomework,
            onClose = onDismiss,
            modifier = Modifier.navigationBarsPadding(),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CourseDetailTabsContent(
    course: CourseItem,
    detailState: CourseDetailUiState?,
    rollcallHistory: RollcallHistoryUiState,
    isDemoMode: Boolean,
    onLoadDetail: () -> Unit,
    onLoadRollcallHistory: (Boolean) -> Unit,
    onOpenAttachment: (Long, String) -> Unit,
    onOpenHomework: (Long) -> Unit,
    onClose: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    var tabIndex by remember(course.id) { mutableIntStateOf(0) }
    val tabs = listOf("作业", "课件", "公告", "考勤")

    LaunchedEffect(course.id) {
        onLoadDetail()
    }
    LaunchedEffect(course.id, tabIndex) {
        if (tabIndex == 3) {
            onLoadRollcallHistory(false)
        }
    }

    val courseHomeworks = detailState?.homeworks
        ?: if (isDemoMode) DemoData.homeworks.filter { it.courseId == course.id } else emptyList()
    val courseWares = detailState?.coursewares
        ?: if (isDemoMode) DemoData.coursewares.filter { it.courseId == course.id } else emptyList()
    val courseBulletins = detailState?.bulletins
        ?: if (isDemoMode) DemoData.bulletins.filter { it.courseId == course.id } else emptyList()
    val courseRollcalls = remember(rollcallHistory.records, course.id) {
        rollcallHistory.records.filter { it.courseId == course.id }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = if (onClose == null) 18.dp else 0.dp, bottom = 24.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(course.name, style = MaterialTheme.typography.headlineSmall)
                Text(
                    text = "${course.teacher} · ${course.code} · ${course.location}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (onClose != null) {
                IconButton(onClick = onClose) {
                    Icon(Icons.Rounded.Close, contentDescription = "关闭")
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))
        PrimaryTabRow(
            selectedTabIndex = tabIndex,
            containerColor = if (onClose == null) Color.Transparent else MaterialTheme.colorScheme.surfaceContainerLow,
        ) {
            tabs.forEachIndexed { index, title ->
                Tab(
                    selected = tabIndex == index,
                    onClick = { tabIndex = index },
                    text = { Text(title) },
                )
            }
        }

        if (detailState?.isLoading == true) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            when (tabIndex) {
                0 -> if (courseHomeworks.isEmpty()) {
                    EmptyTabHint(if (detailState?.isLoading == true) "正在加载课程作业…" else "该课程暂无作业")
                } else {
                    courseHomeworks.forEach { hw ->
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .bounceClickable { onOpenHomework(hw.id) },
                            shape = RoundedCornerShape(18.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerLowest,
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Text(hw.title, style = MaterialTheme.typography.titleSmall)
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "${hw.status.label} · 截止 ${hw.deadlineText}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }

                1 -> if (courseWares.isEmpty()) {
                    EmptyTabHint(if (detailState?.isLoading == true) "正在加载课件…" else "该课程暂无课件")
                } else {
                    courseWares.forEach { cw ->
                        Surface(
                            shape = RoundedCornerShape(18.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerLowest,
                            modifier = Modifier
                                .fillMaxWidth()
                                .bounceClickable {
                                    if (cw.uploadId > 0L || isDemoMode) {
                                        onOpenAttachment(if (cw.uploadId > 0L) cw.uploadId else cw.id, cw.title)
                                    }
                                },
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    Icons.Rounded.Description,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(cw.title, style = MaterialTheme.typography.titleSmall)
                                    Text(
                                        text = "${cw.fileType} · ${cw.sizeText} · ${cw.updatedAt}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }

                2 -> if (courseBulletins.isEmpty()) {
                    EmptyTabHint(if (detailState?.isLoading == true) "正在加载公告…" else "该课程暂无新公告")
                } else {
                    courseBulletins.forEach { b ->
                        Surface(
                            shape = RoundedCornerShape(18.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerLowest,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.Rounded.Campaign,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.tertiary,
                                        modifier = Modifier.size(16.dp),
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(b.title, style = MaterialTheme.typography.titleSmall)
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = b.summary,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }

                else -> if (courseRollcalls.isEmpty()) {
                    EmptyTabHint(if (rollcallHistory.isLoading) "正在同步签到记录…" else "该课程暂无点名记录")
                } else {
                    courseRollcalls.forEach { rc ->
                        RollcallRecordCard(record = rc, showCourseName = false)
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyTabHint(text: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 28.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
