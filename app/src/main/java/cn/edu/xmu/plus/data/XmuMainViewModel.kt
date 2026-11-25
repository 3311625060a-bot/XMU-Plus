package cn.edu.xmu.plus.data

import android.app.Application
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.compose.runtime.Immutable
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import cn.edu.xmu.plus.auth.AuthState
import cn.edu.xmu.plus.auth.SessionStore
import cn.edu.xmu.plus.auth.XmuAuthClient
import cn.edu.xmu.plus.debug.DebugLogStore
import cn.edu.xmu.plus.network.CheckInApiResult
import cn.edu.xmu.plus.network.TronClassApi
import cn.edu.xmu.plus.reminder.DdlNotificationHelper
import cn.edu.xmu.plus.reminder.DdlReminderWorker
import cn.edu.xmu.plus.widget.DdlGlanceWidget
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Immutable
data class PickedLocalFile(
    val uriString: String,
    val name: String,
    val sizeBytes: Long,
    val mimeType: String,
)

@Immutable
data class XmuDashboardState(
    val isDemoMode: Boolean = true,
    val isRefreshing: Boolean = false,
    val activeRollcalls: List<ActiveRollcall> = DemoData.activeRollcalls,
    val semesters: List<SemesterItem> = emptyList(),
    val selectedSemesterId: Long? = null,
    val courses: List<CourseItem> = DemoData.courses,
    val homeworks: List<HomeworkItem> = DemoData.homeworks,
    val lastSyncText: String? = null,
    val errorMessage: String? = null,
)

@Immutable
data class CourseDetailUiState(
    val isLoading: Boolean = false,
    val homeworks: List<HomeworkItem> = emptyList(),
    val coursewares: List<CoursewareItem> = emptyList(),
    val bulletins: List<BulletinItem> = emptyList(),
    val errorMessage: String? = null,
)

@Immutable
data class HomeworkDetailUiState(
    val isLoading: Boolean = false,
    val detail: HomeworkItem? = null,
    val errorMessage: String? = null,
)

class XmuMainViewModel(application: Application) : AndroidViewModel(application) {

    private val cacheStore = OfflineCacheStore(application.applicationContext)

    private var api: TronClassApi? = null
    private var sessionStore: SessionStore? = null
    private var currentUserId: Long? = null

    private val _uiState = MutableStateFlow(XmuDashboardState())
    val uiState: StateFlow<XmuDashboardState> = _uiState.asStateFlow()

    private val _courseDetails = MutableStateFlow<Map<Long, CourseDetailUiState>>(emptyMap())
    val courseDetails: StateFlow<Map<Long, CourseDetailUiState>> = _courseDetails.asStateFlow()

    private val _homeworkDetails = MutableStateFlow<Map<Long, HomeworkDetailUiState>>(emptyMap())
    val homeworkDetails: StateFlow<Map<Long, HomeworkDetailUiState>> = _homeworkDetails.asStateFlow()

    private val _isIndexingSearch = MutableStateFlow(false)
    val isIndexingSearch: StateFlow<Boolean> = _isIndexingSearch.asStateFlow()

    private var remindersEnabled = false

    fun updateReminderPreference(enabled: Boolean) {
        remindersEnabled = enabled
        val ctx = getApplication<Application>().applicationContext
        if (enabled) {
            DdlReminderWorker.schedulePeriodic(ctx)
            DdlReminderWorker.runOnceNow(ctx)
        } else {
            DdlReminderWorker.cancelPeriodic(ctx)
        }
    }

    fun bindAuth(
        authClient: XmuAuthClient,
        sessionStore: SessionStore,
        authState: AuthState,
    ) {
        this.api = TronClassApi(authClient)
        this.sessionStore = sessionStore

        when (authState) {
            is AuthState.LoggedIn -> {
                val userId = authState.profile.id
                if (currentUserId != userId) {
                    currentUserId = userId
                    loadCachedDashboardThenRefresh(userId)
                }
            }

            AuthState.LoggedOut -> {
                if (currentUserId != null) {
                    currentUserId = null
                    viewModelScope.launch {
                        cacheStore.clearAll()
                        publishWidgetSnapshot(isLoggedIn = false, homeworks = emptyList(), updatedAtText = "")
                    }
                }
                _courseDetails.value = emptyMap()
                _homeworkDetails.value = emptyMap()
                _uiState.value = XmuDashboardState(isDemoMode = true)
            }

            AuthState.Checking -> Unit
        }
    }

    private fun loadCachedDashboardThenRefresh(userId: Long) {
        viewModelScope.launch {
            val cached = cacheStore.getJson(dashboardCacheKey(userId), DashboardCacheSnapshot.serializer())
            if (cached != null) {
                val syncLabel = "离线缓存 · ${formatClock(cached.updatedAtMillis)}"
                _uiState.update {
                    it.copy(
                        isDemoMode = false,
                        semesters = cached.data.semesters,
                        selectedSemesterId = cached.data.selectedSemesterId,
                        courses = cached.data.courses,
                        homeworks = cached.data.homeworks,
                        activeRollcalls = emptyList(),
                        lastSyncText = syncLabel,
                    )
                }
                // Warm in-memory courseDetails from offline SQLite cache for instant search
                val restoredDetails = LinkedHashMap<Long, CourseDetailUiState>()
                cached.data.courses.forEach { course ->
                    val detail = cacheStore.getJson(
                        "course_detail_${course.id}",
                        CourseDetailCacheSnapshot.serializer(),
                    )?.data
                    if (detail != null) {
                        restoredDetails[course.id] = CourseDetailUiState(
                            isLoading = false,
                            homeworks = detail.homeworks,
                            coursewares = detail.coursewares,
                            bulletins = detail.bulletins,
                        )
                    }
                }
                if (restoredDetails.isNotEmpty()) {
                    _courseDetails.update { it + restoredDetails }
                }
                publishWidgetSnapshot(
                    isLoggedIn = true,
                    homeworks = cached.data.homeworks,
                    updatedAtText = syncLabel,
                )
            } else {
                _uiState.update {
                    it.copy(
                        isDemoMode = false,
                        activeRollcalls = emptyList(),
                        courses = emptyList(),
                        homeworks = emptyList(),
                    )
                }
            }
            refreshAll()
        }
    }

    fun refreshAll() {
        val currentApi = api ?: return
        val userId = currentUserId ?: return

        viewModelScope.launch {
            _uiState.update { it.copy(isRefreshing = true, errorMessage = null) }
            try {
                coroutineScope {
                    val rollcallsDeferred = async { currentApi.getOngoingRollcalls() }
                    val semestersDeferred = async { currentApi.getMySemesters() }
                    val todosDeferred = async { currentApi.getTodos() }

                    val semesters = semestersDeferred.await().getOrElse { _uiState.value.semesters }
                    val targetSemesterId = _uiState.value.selectedSemesterId
                        ?: semesters.firstOrNull { it.isActive }?.id
                        ?: semesters.firstOrNull()?.id
                    val semesterNames = semesters.associate { it.id to it.name }

                    val courses = currentApi.getUserCourses(
                        userId = userId,
                        semesterId = targetSemesterId,
                        semesterNames = semesterNames,
                    ).getOrElse { _uiState.value.courses }

                    val todos = todosDeferred.await().getOrElse { emptyList() }
                    val rollcalls = rollcallsDeferred.await().getOrElse { emptyList() }

                    // Fan out homework-activities across courses (bounded concurrency = 5)
                    val sem = Semaphore(5)
                    val courseHomeworks = courses.take(15).map { course ->
                        async {
                            sem.withPermit {
                                currentApi.getCourseHomeworks(course.id, course.name)
                                    .getOrElse { emptyList() }
                            }
                        }
                    }.awaitAll().flatten()

                    val mergedMap = LinkedHashMap<Long, HomeworkItem>()
                    todos.forEach { mergedMap[it.id] = it }
                    courseHomeworks.forEach { hw -> mergedMap[hw.id] = hw }

                    val sortedHomeworks = mergedMap.values.sortedWith(
                        compareBy<HomeworkItem>(
                            {
                                when (it.status) {
                                    HomeworkStatus.PENDING -> 0
                                    HomeworkStatus.SUBMITTED -> 1
                                    HomeworkStatus.GRADED -> 2
                                }
                            },
                            { TimeFormatUtils.parseIsoInstant(it.endTimeIso)?.toEpochMilli() ?: Long.MAX_VALUE },
                        ),
                    )

                    val pendingByCourse = sortedHomeworks
                        .filter { it.status == HomeworkStatus.PENDING }
                        .groupingBy { it.courseId }
                        .eachCount()

                    val enrichedCourses = courses.map { c ->
                        c.copy(pendingHomeworkCount = pendingByCourse[c.id] ?: 0)
                    }

                    cacheStore.putJson(
                        key = dashboardCacheKey(userId),
                        value = DashboardCacheSnapshot(
                            semesters = semesters,
                            selectedSemesterId = targetSemesterId,
                            courses = enrichedCourses,
                            homeworks = sortedHomeworks,
                        ),
                        serializer = DashboardCacheSnapshot.serializer(),
                    )

                    val syncLabel = "刚刚同步 · ${formatClock(System.currentTimeMillis())}"
                    _uiState.update {
                        it.copy(
                            isDemoMode = false,
                            isRefreshing = false,
                            activeRollcalls = rollcalls,
                            semesters = semesters,
                            selectedSemesterId = targetSemesterId,
                            courses = enrichedCourses,
                            homeworks = sortedHomeworks,
                            lastSyncText = syncLabel,
                            errorMessage = null,
                        )
                    }
                    publishWidgetSnapshot(
                        isLoggedIn = true,
                        homeworks = sortedHomeworks,
                        updatedAtText = syncLabel,
                    )
                    DebugLogStore.log(
                        "SYNC",
                        "semesters=${semesters.size}, courses=${enrichedCourses.size}, homeworks=${sortedHomeworks.size}, rollcalls=${rollcalls.size}",
                    )
                }
            } catch (e: Exception) {
                DebugLogStore.log("SYNC", "refreshAll failed: ${e.message}", "ERR")
                _uiState.update {
                    it.copy(
                        isRefreshing = false,
                        errorMessage = e.message ?: "同步失败，请稍后重试",
                    )
                }
            }
        }
    }

    fun selectSemester(semesterId: Long?) {
        _uiState.update { it.copy(selectedSemesterId = semesterId) }
        refreshAll()
    }

    fun refreshRollcalls() {
        val currentApi = api ?: return
        if (currentUserId == null) return
        viewModelScope.launch {
            currentApi.getOngoingRollcalls().onSuccess { list ->
                _uiState.update { it.copy(activeRollcalls = list) }
            }
        }
    }

    fun loadCourseDetail(course: CourseItem) {
        val currentApi = api ?: return
        if (currentUserId == null) {
            _courseDetails.update { map ->
                map + (course.id to CourseDetailUiState(
                    isLoading = false,
                    homeworks = DemoData.homeworks.filter { it.courseId == course.id },
                    coursewares = DemoData.coursewares.filter { it.courseId == course.id },
                    bulletins = DemoData.bulletins.filter { it.courseId == course.id },
                ))
            }
            return
        }

        viewModelScope.launch {
            val cacheKey = "course_detail_${course.id}"
            val cached = cacheStore.getJson(cacheKey, CourseDetailCacheSnapshot.serializer())
            if (cached != null) {
                _courseDetails.update { map ->
                    map + (course.id to CourseDetailUiState(
                        isLoading = true,
                        homeworks = cached.data.homeworks,
                        coursewares = cached.data.coursewares,
                        bulletins = cached.data.bulletins,
                    ))
                }
            } else {
                _courseDetails.update { map ->
                    map + (course.id to CourseDetailUiState(isLoading = true))
                }
            }

            coroutineScope {
                val hwDeferred = async { currentApi.getCourseHomeworks(course.id, course.name) }
                val cwDeferred = async { currentApi.getCoursewares(course.id) }
                val blDeferred = async { currentApi.getCourseBulletins(course.id) }

                val hws = hwDeferred.await().getOrElse { cached?.data?.homeworks.orEmpty() }
                val cws = cwDeferred.await().getOrElse { cached?.data?.coursewares.orEmpty() }
                val bls = blDeferred.await().getOrElse { cached?.data?.bulletins.orEmpty() }

                cacheStore.putJson(
                    key = cacheKey,
                    value = CourseDetailCacheSnapshot(homeworks = hws, coursewares = cws, bulletins = bls),
                    serializer = CourseDetailCacheSnapshot.serializer(),
                )

                _courseDetails.update { map ->
                    map + (course.id to CourseDetailUiState(
                        isLoading = false,
                        homeworks = hws,
                        coursewares = cws,
                        bulletins = bls,
                    ))
                }
            }
        }
    }

    fun loadHomeworkDetail(homeworkId: Long) {
        val currentApi = api ?: return
        val userId = currentUserId ?: return

        val fallback = _uiState.value.homeworks.find { it.id == homeworkId }
            ?: _homeworkDetails.value[homeworkId]?.detail

        viewModelScope.launch {
            val cacheKey = "hw_detail_${userId}_$homeworkId"
            val cached = cacheStore.getJson(cacheKey, HomeworkItem.serializer())
            val initial = cached?.data ?: fallback
            _homeworkDetails.update { map ->
                map + (homeworkId to HomeworkDetailUiState(isLoading = true, detail = initial))
            }

            currentApi.getHomeworkDetail(homeworkId, userId, initial).fold(
                onSuccess = { rich ->
                    cacheStore.putJson(cacheKey, rich, HomeworkItem.serializer())
                    _homeworkDetails.update { map ->
                        map + (homeworkId to HomeworkDetailUiState(isLoading = false, detail = rich))
                    }
                    _uiState.update { state ->
                        state.copy(
                            homeworks = state.homeworks.map { if (it.id == homeworkId) rich else it },
                        )
                    }
                },
                onFailure = { err ->
                    _homeworkDetails.update { map ->
                        map + (homeworkId to HomeworkDetailUiState(
                            isLoading = false,
                            detail = initial,
                            errorMessage = err.message,
                        ))
                    }
                },
            )
        }
    }

    suspend fun submitPasscodeCheckIn(rollcallId: Long, code: String): CheckInApiResult {
        if (_uiState.value.isDemoMode || currentUserId == null) {
            delay(500)
            return if (code == "0000") {
                CheckInApiResult.Error("签到码不正确（演示：0000 触发错误抖动）", isWrongCode = true)
            } else {
                CheckInApiResult.Success("密码签到成功（演示预览模式）")
            }
        }
        val currentApi = api ?: return CheckInApiResult.Error("网络客户端未就绪")
        val store = sessionStore ?: return CheckInApiResult.Error("会话存储未就绪")
        val deviceId = store.getOrCreateDeviceId()
        DebugLogStore.log("ROLLCALL", "提交密码签到 rollcallId=$rollcallId, digits=${code.length}")
        val result = currentApi.answerNumberRollcall(rollcallId, code, deviceId)
        when (result) {
            is CheckInApiResult.Success -> {
                DebugLogStore.log("ROLLCALL", "密码签到成功: ${result.message}")
                refreshRollcalls()
            }
            is CheckInApiResult.Error -> {
                DebugLogStore.log("ROLLCALL", "密码签到失败 code=${result.errorCode} msg=${result.message}", "ERR")
            }
        }
        return result
    }

    suspend fun submitQrCheckIn(rawQrContent: String): CheckInApiResult {
        val formatKind = when {
            rawQrContent.trim().startsWith("{") -> "JSON"
            rawQrContent.contains("_p=") -> "URL_JSON"
            rawQrContent.contains("p=") -> "URL_COMPACT"
            else -> "UNKNOWN"
        }
        val parsed = QrRollcallParser.parse(rawQrContent)
        if (parsed == null) {
            DebugLogStore.log("QR", "解析失败 format=$formatKind len=${rawQrContent.length}", "ERR")
            return CheckInApiResult.Error("未识别为有效的厦大畅课签到二维码")
        }
        DebugLogStore.log(
            "QR",
            "解析成功 format=$formatKind rollcallId=${parsed.rollcallId} courseId=${parsed.courseId} dataLen=${parsed.data.length}",
        )

        if (_uiState.value.isDemoMode || currentUserId == null) {
            delay(500)
            return CheckInApiResult.Success("已解析点名 #${parsed.rollcallId}（演示预览模式）")
        }

        val currentApi = api ?: return CheckInApiResult.Error("网络客户端未就绪")
        val store = sessionStore ?: return CheckInApiResult.Error("会话存储未就绪")
        val deviceId = store.getOrCreateDeviceId()
        val result = currentApi.answerQrRollcall(parsed.rollcallId, parsed.data, deviceId)
        when (result) {
            is CheckInApiResult.Success -> {
                DebugLogStore.log("QR", "扫码签到成功: ${result.message}")
                refreshRollcalls()
            }
            is CheckInApiResult.Error -> {
                DebugLogStore.log("QR", "扫码签到失败 code=${result.errorCode} msg=${result.message}", "ERR")
            }
        }
        return result
    }

    suspend fun submitHomeworkWithFiles(
        context: Context,
        homeworkId: Long,
        comment: String,
        pickedFiles: List<PickedLocalFile>,
        existingSubmissionId: Long?,
        onProgress: (Float, String) -> Unit,
    ): Result<Unit> {
        if (_uiState.value.isDemoMode || currentUserId == null) {
            for (i in 1..10) {
                onProgress(i / 10f, "演示模式模拟上传…")
                delay(70)
            }
            return Result.success(Unit)
        }

        val currentApi = api ?: return Result.failure(IllegalStateException("网络客户端未就绪"))
        val uploadedIds = mutableListOf<Long>()
        val total = pickedFiles.size.coerceAtLeast(1)

        pickedFiles.forEachIndexed { index, file ->
            onProgress(
                (index.toFloat() / total) * 0.8f,
                "正在上传附件 (${index + 1}/${pickedFiles.size})：${file.name}",
            )
            val bytes = readUriBytes(context, Uri.parse(file.uriString)).getOrElse {
                return Result.failure(it)
            }
            val uploaded = currentApi.uploadHomeworkFile(
                fileName = file.name,
                mimeType = file.mimeType,
                bytes = bytes,
                onProgress = { filePct ->
                    onProgress(
                        ((index + filePct) / total) * 0.8f,
                        "正在上传附件 (${index + 1}/${pickedFiles.size})：${file.name}",
                    )
                },
            ).getOrElse { return Result.failure(it) }
            uploadedIds.add(uploaded.id)
        }

        onProgress(0.90f, "正在提交作业至畅课平台…")
        val submitRes = currentApi.submitHomework(
            homeworkId = homeworkId,
            comment = comment,
            uploadIds = uploadedIds,
            existingSubmissionId = existingSubmissionId,
        )
        if (submitRes.isSuccess) {
            onProgress(1.0f, "提交成功")
            loadHomeworkDetail(homeworkId)
            refreshAll()
        }
        return submitRes
    }

    suspend fun resolveAttachmentUrl(uploadId: Long): Result<String> {
        val currentApi = api ?: return Result.failure(IllegalStateException("请先登录统一身份认证"))
        return currentApi.getUploadDownloadUrl(uploadId)
    }

    fun resolvePickedFile(context: Context, uri: Uri): PickedLocalFile {
        var name = "附件_${System.currentTimeMillis()}"
        var size = 0L
        context.contentResolver.query(
            uri,
            arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE),
            null,
            null,
            null,
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (nameIdx >= 0) name = cursor.getString(nameIdx) ?: name
                if (sizeIdx >= 0) size = cursor.getLong(sizeIdx)
            }
        }
        val mime = context.contentResolver.getType(uri) ?: "application/octet-stream"
        return PickedLocalFile(
            uriString = uri.toString(),
            name = name,
            sizeBytes = size,
            mimeType = mime,
        )
    }

    private suspend fun readUriBytes(context: Context, uri: Uri): Result<ByteArray> =
        withContext(Dispatchers.IO) {
            runCatching {
                context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                    ?: error("无法读取选中的文件")
            }
        }

    fun indexAllCoursesForSearch() {
        val currentApi = api ?: return
        if (currentUserId == null || _isIndexingSearch.value) return
        val courses = _uiState.value.courses
        if (courses.isEmpty()) return

        viewModelScope.launch {
            _isIndexingSearch.value = true
            try {
                val sem = Semaphore(4)
                val existing = _courseDetails.value
                val targets = courses.filter { existing[it.id] == null }
                if (targets.isNotEmpty()) {
                    coroutineScope {
                        val loaded = targets.map { course ->
                            async {
                                sem.withPermit {
                                    val cacheKey = "course_detail_${course.id}"
                                    val hws = currentApi.getCourseHomeworks(course.id, course.name).getOrElse { emptyList() }
                                    val cws = currentApi.getCoursewares(course.id).getOrElse { emptyList() }
                                    val bls = currentApi.getCourseBulletins(course.id).getOrElse { emptyList() }
                                    cacheStore.putJson(
                                        key = cacheKey,
                                        value = CourseDetailCacheSnapshot(
                                            homeworks = hws,
                                            coursewares = cws,
                                            bulletins = bls,
                                        ),
                                        serializer = CourseDetailCacheSnapshot.serializer(),
                                    )
                                    course.id to CourseDetailUiState(
                                        isLoading = false,
                                        homeworks = hws,
                                        coursewares = cws,
                                        bulletins = bls,
                                    )
                                }
                            }
                        }.awaitAll()
                        _courseDetails.update { it + loaded }
                    }
                }
            } finally {
                _isIndexingSearch.value = false
            }
        }
    }

    private suspend fun publishWidgetSnapshot(
        isLoggedIn: Boolean,
        homeworks: List<HomeworkItem>,
        updatedAtText: String,
    ) {
        val pending = homeworks
            .filter { it.status == HomeworkStatus.PENDING }
            .map { hw ->
                WidgetDdlItem(
                    id = hw.id,
                    courseName = hw.courseName,
                    title = hw.title,
                    deadlineText = hw.deadlineText,
                    remainingText = hw.remainingText,
                    endTimeIso = hw.endTimeIso,
                    urgency = hw.urgency,
                )
            }
        val snapshot = WidgetDdlSnapshot(
            isLoggedIn = isLoggedIn,
            pendingCount = pending.size,
            items = pending.take(8),
            updatedAtText = updatedAtText,
        )
        cacheStore.putJson(
            key = DdlReminderWorker.WIDGET_SNAPSHOT_KEY,
            value = snapshot,
            serializer = WidgetDdlSnapshot.serializer(),
        )
        val ctx = getApplication<Application>().applicationContext
        DdlGlanceWidget.updateAllWidgets(ctx)
        if (remindersEnabled && isLoggedIn && pending.isNotEmpty()) {
            DdlNotificationHelper.dispatchPendingReminders(ctx, pending)
        }
    }

    private fun dashboardCacheKey(userId: Long) = "dashboard_v1_$userId"

    private fun formatClock(millis: Long): String =
        SimpleDateFormat("HH:mm", Locale.CHINA).format(Date(millis))
}
