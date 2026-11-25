package cn.edu.xmu.plus.network

import cn.edu.xmu.plus.auth.SessionExpiredException
import cn.edu.xmu.plus.auth.XmuAuthClient
import cn.edu.xmu.plus.data.ActiveRollcall
import cn.edu.xmu.plus.data.AttachmentFile
import cn.edu.xmu.plus.data.AttachmentPreviewInfo
import cn.edu.xmu.plus.data.BulletinItem
import cn.edu.xmu.plus.data.CourseItem
import cn.edu.xmu.plus.data.CoursewareItem
import cn.edu.xmu.plus.data.HomeworkItem
import cn.edu.xmu.plus.data.HomeworkStatus
import cn.edu.xmu.plus.data.RollcallHistoryItem
import cn.edu.xmu.plus.data.RollcallRecordStatus
import cn.edu.xmu.plus.data.SemesterItem
import cn.edu.xmu.plus.data.TimeFormatUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.io.IOException
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

sealed interface CheckInApiResult {
    data class Success(val message: String) : CheckInApiResult
    data class Error(
        val message: String,
        val errorCode: String? = null,
        val isWrongCode: Boolean = false,
    ) : CheckInApiResult
}

class TronClassApi(
    private val authClient: XmuAuthClient,
) {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()
    private val baseUrl = XmuAuthClient.LNT_BASE_URL
    private val cdnDownloadClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .followRedirects(false)
            .followSslRedirects(false)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(45, TimeUnit.SECONDS)
            .build()
    }

    suspend fun getOngoingRollcalls(): Result<List<ActiveRollcall>> = withContext(Dispatchers.IO) {
        getJson("$baseUrl/api/radar/rollcalls?api_version=1.1.0").map { root ->
            val arr = root.optObj()?.optArr("rollcalls") ?: root.optArr() ?: return@map emptyList()
            arr.mapNotNull { elem ->
                val obj = elem.optObj() ?: return@mapNotNull null
                val id = obj.optLong("rollcall_id") ?: obj.optLong("id") ?: return@mapNotNull null
                val courseId = obj.optLong("course_id") ?: 0L
                val status = obj.optStr("rollcall_status").ifBlank { obj.optStr("status") }
                val isExpired = obj.optBool("is_expired") ?: false
                if (isExpired || status.equals("on_call", ignoreCase = true)) {
                    return@mapNotNull null
                }
                val isNumber = obj.optBool("is_number") ?: false
                val isRadar = obj.optBool("is_radar") ?: false
                val rawType = obj.optStr("type")
                val source = obj.optStr("source")
                val resolvedType = when {
                    isNumber || rawType == "number" || source == "number" -> "number"
                    rawType.contains("qr", ignoreCase = true) || source.contains("qr", ignoreCase = true) -> "qr"
                    isRadar -> "radar"
                    else -> rawType.ifBlank { "number" }
                }
                val courseName = obj.optStr("course_title")
                    .ifBlank { obj.optStr("course_name") }
                    .ifBlank { obj.optStr("title") }
                    .ifBlank { "课堂点名 #$id" }
                val teacher = obj.optStr("created_by_name")
                    .ifBlank { obj.optStr("teacher_name") }
                    .ifBlank { "任课教师" }
                val startedAt = TimeFormatUtils.formatRelativeTime(
                    obj.optStr("rollcall_time").ifBlank { obj.optStr("published_at") },
                )
                ActiveRollcall(
                    id = id,
                    courseName = courseName,
                    teacher = teacher,
                    type = resolvedType,
                    startedAt = "${startedAt}发起",
                    courseId = courseId,
                    status = status,
                    isRadar = isRadar,
                    isNumber = isNumber,
                )
            }
        }
    }

    suspend fun answerNumberRollcall(
        rollcallId: Long,
        numberCode: String,
        deviceId: String,
    ): CheckInApiResult = withContext(Dispatchers.IO) {
        val body = buildJsonObject {
            put("deviceId", deviceId)
            put("numberCode", numberCode)
        }.toString().toRequestBody(jsonMediaType)

        executeCheckInPut(
            url = "$baseUrl/api/rollcall/$rollcallId/answer_number_rollcall",
            body = body,
            defaultSuccessMessage = "数字密码签到成功",
        )
    }

    suspend fun answerQrRollcall(
        rollcallId: Long,
        qrData: String,
        deviceId: String,
    ): CheckInApiResult = withContext(Dispatchers.IO) {
        val body = buildJsonObject {
            put("data", qrData)
            put("deviceId", deviceId)
        }.toString().toRequestBody(jsonMediaType)

        executeCheckInPut(
            url = "$baseUrl/api/rollcall/$rollcallId/answer_qr_rollcall",
            body = body,
            defaultSuccessMessage = "扫码签到成功 · 已记录本次考勤",
        )
    }

    private fun executeCheckInPut(
        url: String,
        body: okhttp3.RequestBody,
        defaultSuccessMessage: String,
    ): CheckInApiResult {
        return try {
            val req = Request.Builder().url(url).put(body).build()
            authClient.apiClient.newCall(req).execute().use { resp ->
                val raw = resp.body?.string().orEmpty()
                if (resp.code == 401 || isAuthRedirect(resp.code, resp.header("Location"))) {
                    return CheckInApiResult.Error("登录会话已过期，请重新登录后再试")
                }
                val obj = runCatching { json.parseToJsonElement(raw).optObj() }.getOrNull()
                if (resp.isSuccessful) {
                    val status = obj?.optStr("status").orEmpty()
                    if (status.isBlank() || status == "on_call" || status == "on_call_fine") {
                        return CheckInApiResult.Success(defaultSuccessMessage)
                    }
                }
                val errCode = obj?.optStr("error_code")?.ifBlank { null }
                    ?: obj?.optStr("code")?.ifBlank { null }
                    ?: obj?.optStr("message")?.ifBlank { null }
                val mapped = mapRollcallError(resp.code, errCode)
                CheckInApiResult.Error(
                    message = mapped,
                    errorCode = errCode,
                    isWrongCode = errCode == "wrong_number_code",
                )
            }
        } catch (_: IOException) {
            CheckInApiResult.Error("网络连接失败，请检查网络后重试")
        } catch (_: Exception) {
            CheckInApiResult.Error("签到请求异常，请稍后重试")
        }
    }

    private fun mapRollcallError(httpCode: Int, errorCode: String?): String {
        return when (errorCode) {
            "wrong_number_code" -> "签到口令不正确，请核对后重输"
            "qr_code_expired" -> "课堂二维码已过期，请扫描老师屏幕上的最新二维码"
            "invalid_qr_create_time", "invalid_create_time_hash" -> "二维码无效或已刷新，请重新扫描"
            "rollcall_device_already_in_use" -> "当前设备已被其他账号用于本次签到"
            "rollcall_already_closed" -> "本次点名已结束"
            "rollcall_unknown_student" -> "您不在本次点名课程名单中"
            "not_found_qr_rollcall" -> "未找到对应的二维码签到活动"
            "no_request_data" -> "二维码缺少校验数据，请重新扫描"
            else -> when (httpCode) {
                404 -> "本次点名已结束或不存在"
                400 -> "签到校验未通过，请重试"
                else -> "签到失败（HTTP $httpCode）"
            }
        }
    }

    suspend fun getMySemesters(): Result<List<SemesterItem>> = withContext(Dispatchers.IO) {
        getJson("$baseUrl/api/my-semesters").map { root ->
            val arr = root.optObj()?.optArr("semesters") ?: root.optArr() ?: return@map emptyList()
            arr.mapNotNull { elem ->
                val obj = elem.optObj() ?: return@mapNotNull null
                val id = obj.optLong("id") ?: return@mapNotNull null
                val name = obj.optStr("name").ifBlank { obj.optStr("code") }.ifBlank { "学期 #$id" }
                val code = obj.optStr("code")
                val isActive = obj.optBool("is_active") ?: false
                SemesterItem(id = id, name = name, code = code, isActive = isActive)
            }
        }
    }

    suspend fun getUserCourses(
        userId: Long,
        semesterId: Long? = null,
        semesterNames: Map<Long, String> = emptyMap(),
    ): Result<List<CourseItem>> = withContext(Dispatchers.IO) {
        val conditionsJson = buildJsonObject {
            put("role", buildJsonArray { })
            if (semesterId != null) {
                put("semester_id", buildJsonArray { add(JsonPrimitive(semesterId)) })
            } else {
                put("semester_id", buildJsonArray { })
            }
            put("academic_year_id", buildJsonArray { })
        }.toString()
        val encodedConditions = URLEncoder.encode(conditionsJson, "UTF-8")
        val fields = URLEncoder.encode(
            "id,name,course_code,instructors(id,name),semester_id,academic_year_id,course_attributes,department(id,name),start_date,end_date",
            "UTF-8",
        )
        val url =
            "$baseUrl/api/users/$userId/courses?page=1&page_size=50&sort=all&keyword=&conditions=$encodedConditions&fields=$fields"

        getJson(url).map { root ->
            val arr = root.optObj()?.optArr("courses") ?: root.optArr() ?: return@map emptyList()
            arr.mapIndexedNotNull { idx, elem ->
                val obj = elem.optObj() ?: return@mapIndexedNotNull null
                val id = obj.optLong("id") ?: return@mapIndexedNotNull null
                val name = obj.optStr("name").ifBlank { "未命名课程" }
                val code = obj.optStr("course_code").ifBlank { "COURSE-$id" }
                val teachers = obj.optArr("instructors")
                    ?.mapNotNull { it.optObj()?.optStr("name")?.takeIf(String::isNotBlank) }
                    ?.joinToString(" / ")
                    .orEmpty()
                    .ifBlank { "厦大教师" }
                val semId = obj.optLong("semester_id")
                val semesterLabel = semId?.let { semesterNames[it] }.orEmpty().ifBlank { "当前学期" }
                val attrs = obj.optObj("course_attributes")
                val teachingClass = attrs?.optStr("teaching_class_name").orEmpty()
                val deptName = obj.optObj("department")?.optStr("name").orEmpty()
                val location = teachingClass.ifBlank { deptName }.ifBlank { "厦门大学畅课平台" }
                val schedule = listOfNotNull(
                    obj.optStr("start_date").takeIf { it.isNotBlank() }?.take(10),
                    obj.optStr("end_date").takeIf { it.isNotBlank() }?.take(10),
                ).joinToString(" ~ ").ifBlank { "在线教学课程" }

                CourseItem(
                    id = id,
                    name = name,
                    teacher = teachers,
                    code = code,
                    semester = semesterLabel,
                    location = location,
                    schedule = schedule,
                    isPinned = false,
                    unreadCount = 0,
                    pendingHomeworkCount = 0,
                    accentIndex = idx,
                )
            }
        }
    }

    suspend fun getTodos(): Result<List<HomeworkItem>> = withContext(Dispatchers.IO) {
        getJson("$baseUrl/api/todos").map { root ->
            val arr = root.optObj()?.optArr("todo_list") ?: root.optArr() ?: return@map emptyList()
            arr.mapNotNull { elem ->
                val obj = elem.optObj() ?: return@mapNotNull null
                val type = obj.optStr("type")
                if (type.isNotBlank() && !type.contains("homework", ignoreCase = true)) {
                    return@mapNotNull null
                }
                val id = obj.optLong("id") ?: obj.optLong("activity_id") ?: return@mapNotNull null
                val courseId = obj.optLong("course_id") ?: 0L
                val courseName = obj.optStr("course_name").ifBlank { "课程 #$courseId" }
                val title = obj.optStr("title").ifBlank { "作业 #$id" }
                val endTime = obj.optStr("end_time").ifBlank { null }
                val (remaining, urgency) = TimeFormatUtils.computeRemainingAndUrgency(
                    endTime,
                    HomeworkStatus.PENDING,
                )
                HomeworkItem(
                    id = id,
                    courseId = courseId,
                    courseName = courseName,
                    title = title,
                    deadlineText = TimeFormatUtils.formatDeadline(endTime),
                    remainingText = remaining,
                    urgency = urgency,
                    status = HomeworkStatus.PENDING,
                    description = "点击查看作业要求与附件详情",
                    attachments = emptyList(),
                    endTimeIso = endTime,
                )
            }
        }
    }

    suspend fun getCourseHomeworks(courseId: Long, courseName: String): Result<List<HomeworkItem>> =
        withContext(Dispatchers.IO) {
            val url = "$baseUrl/api/courses/$courseId/homework-activities?page=1&page_size=50&conditions=%7B%7D"
            getJson(url).map { root ->
                val arr = root.optObj()?.optArr("homework_activities") ?: root.optArr() ?: return@map emptyList()
                arr.mapNotNull { elem ->
                    val obj = elem.optObj() ?: return@mapNotNull null
                    parseHomeworkObject(obj = obj, fallbackCourseId = courseId, fallbackCourseName = courseName)
                }
            }
        }

    suspend fun getHomeworkDetail(
        homeworkId: Long,
        userId: Long,
        fallback: HomeworkItem?,
    ): Result<HomeworkItem> = withContext(Dispatchers.IO) {
        val activityRes = getJson("$baseUrl/api/activities/$homeworkId?sub_course_id=0")
        val actObj = activityRes.getOrNull()?.optObj()
        if (actObj == null && fallback == null) {
            return@withContext Result.failure(activityRes.exceptionOrNull() ?: IOException("无法获取作业详情"))
        }

        // Best-effort mark read
        runCatching {
            val readReq = Request.Builder()
                .url("$baseUrl/api/course/activities-read/$homeworkId")
                .post("{}".toRequestBody(jsonMediaType))
                .build()
            authClient.apiClient.newCall(readReq).execute().close()
        }

        val isGroup = actObj?.optBool("submit_by_group") ?: fallback?.isGroupHomework ?: false
        val subListRes = getJson("$baseUrl/api/activities/$homeworkId/students/$userId/submission_list")
        val subListObj = subListRes.getOrNull()?.optObj()
        val subArr = subListObj?.optArr("list")
            ?: subListObj?.optArr("submissions")
            ?: subListRes.getOrNull()?.optArr()
        val latestSub = subArr?.firstOrNull()?.optObj()

        val scoreRes = getJson("$baseUrl/api/activities/$homeworkId/students/$userId/homework-score")
        val scoreObj = scoreRes.getOrNull()?.optObj()

        val baseItem = if (actObj != null) {
            parseHomeworkObject(
                obj = actObj,
                fallbackCourseId = fallback?.courseId ?: 0L,
                fallbackCourseName = fallback?.courseName.orEmpty().ifBlank { "课程作业" },
            )
        } else {
            fallback
        } ?: return@withContext Result.failure(IOException("无法解析作业详情"))

        val submissionId = latestSub?.optLong("id") ?: baseItem.submissionId
        val submittedAtRaw = latestSub?.optStr("created_at")?.ifBlank { latestSub.optStr("updated_at") }
        val submittedContent = TimeFormatUtils.stripHtmlToPlainText(
            latestSub?.optStr("comment").orEmpty(),
        ).ifBlank { baseItem.submittedContent }
        val submittedAttachmentFiles = parseUploads(latestSub?.optArr("uploads"))
            .ifEmpty { baseItem.submittedAttachmentFiles }

        val rawScore = scoreObj?.optScore("final_score")
            ?: scoreObj?.optScore("score")
            ?: latestSub?.optScore("score")
            ?: baseItem.score
        val fullScore = actObj?.optScore("score")
            ?: scoreObj?.optScore("full_score")
            ?: actObj?.optObj("data")?.optScore("full_score")
            ?: baseItem.fullScore
        val comment = scoreObj?.optStr("instructor_comment")?.ifBlank { null }
            ?: latestSub?.optStr("instructor_comment")?.ifBlank { null }
            ?: baseItem.teacherComment
        val rawDupRate = latestSub?.optScore("duplicate_rate")
            ?: latestSub?.optScore("similarity")
            ?: latestSub?.optScore("plagiarism_rate")
            ?: latestSub?.optScore("repeat_rate")
            ?: scoreObj?.optScore("duplicate_rate")
            ?: scoreObj?.optScore("similarity")
            ?: latestSub?.optObj("check_result")?.optScore("duplicate_rate")
            ?: baseItem.duplicateRate
        val normalizedDupRate = rawDupRate?.let { raw ->
            val clean = raw.removeSuffix("%").trim()
            val num = clean.toDoubleOrNull()
            when {
                raw.contains("%") -> raw
                num != null && num in 0.0..1.0 -> "${(num * 100).toInt()}%"
                num != null -> "${clean}%"
                else -> raw
            }
        }
        val graderName = scoreObj?.optStr("grader_name")?.ifBlank { null }
            ?: scoreObj?.optStr("instructor_name")?.ifBlank { null }
            ?: scoreObj?.optObj("grader")?.optStr("name")?.ifBlank { null }
            ?: latestSub?.optObj("grader")?.optStr("name")?.ifBlank { null }
            ?: baseItem.graderName
        val gradedAtRaw = scoreObj?.optStr("updated_at")?.ifBlank { scoreObj.optStr("created_at") }
            ?: scoreObj?.optStr("graded_at")?.ifBlank { null }
        val gradedAt = gradedAtRaw?.takeIf { it.isNotBlank() }?.let { TimeFormatUtils.formatDeadline(it) }
            ?: baseItem.gradedAt
        val submissionCount = subArr?.size ?: baseItem.submissionCount

        val finalStatus = when {
            !rawScore.isNullOrBlank() -> HomeworkStatus.GRADED
            submissionId != null || !submittedAtRaw.isNullOrBlank() -> HomeworkStatus.SUBMITTED
            else -> baseItem.status
        }
        val (remaining, urgency) = TimeFormatUtils.computeRemainingAndUrgency(baseItem.endTimeIso, finalStatus)

        Result.success(
            baseItem.copy(
                status = finalStatus,
                remainingText = remaining,
                urgency = urgency,
                isGroupHomework = isGroup,
                submissionId = submissionId,
                submittedAt = submittedAtRaw?.let { TimeFormatUtils.formatDeadline(it) } ?: baseItem.submittedAt,
                submittedContent = submittedContent,
                submittedAttachmentFiles = submittedAttachmentFiles,
                submittedFiles = submittedAttachmentFiles.map { it.name }.ifEmpty { baseItem.submittedFiles },
                score = rawScore,
                teacherComment = comment,
                fullScore = fullScore,
                duplicateRate = normalizedDupRate,
                gradedAt = gradedAt,
                graderName = graderName,
                submissionCount = submissionCount,
            ),
        )
    }

    suspend fun getCourseBulletins(courseId: Long): Result<List<BulletinItem>> = withContext(Dispatchers.IO) {
        getJson("$baseUrl/api/courses/$courseId/bulletins?page=1&page_size=20").map { root ->
            val arr = root.optObj()?.optArr("bulletins") ?: root.optArr() ?: return@map emptyList()
            arr.mapNotNull { elem ->
                val obj = elem.optObj() ?: return@mapNotNull null
                val id = obj.optLong("id") ?: return@mapNotNull null
                val title = obj.optStr("title").ifBlank { "课程公告" }
                val summary = TimeFormatUtils.stripHtmlToPlainText(obj.optStr("content"))
                    .ifBlank { "点击查看详情" }
                val publishedAt = TimeFormatUtils.formatRelativeTime(
                    obj.optStr("created_at").ifBlank { obj.optStr("updated_at") },
                )
                val isUnread = !(obj.optBool("is_read") ?: true)
                BulletinItem(
                    id = id,
                    courseId = courseId,
                    title = title,
                    summary = summary,
                    publishedAt = publishedAt,
                    isUnread = isUnread,
                )
            }
        }
    }

    suspend fun getCoursewares(courseId: Long): Result<List<CoursewareItem>> = withContext(Dispatchers.IO) {
        getJson("$baseUrl/api/course/$courseId/coursewares?page=1&page_size=50&conditions=%7B%7D").map { root ->
            val arr = root.optObj()?.optArr("activities") ?: root.optArr() ?: return@map emptyList()
            val items = mutableListOf<CoursewareItem>()
            arr.forEach { elem ->
                val obj = elem.optObj() ?: return@forEach
                val activityId = obj.optLong("id") ?: 0L
                val activityTitle = obj.optStr("title").ifBlank { "课程资料" }
                val updatedAt = TimeFormatUtils.formatRelativeTime(
                    obj.optStr("updated_at").ifBlank { obj.optStr("created_at") },
                )
                val uploads = obj.optArr("uploads")
                if (uploads != null && uploads.isNotEmpty()) {
                    uploads.forEach { uElem ->
                        val uObj = uElem.optObj() ?: return@forEach
                        val uploadId = uObj.optLong("id") ?: 0L
                        val fileName = uObj.optStr("name").ifBlank { activityTitle }
                        val sizeBytes = uObj.optLong("size") ?: 0L
                        val ext = fileName.substringAfterLast('.', "").uppercase().ifBlank { "FILE" }
                        items.add(
                            CoursewareItem(
                                id = if (uploadId > 0L) uploadId else activityId,
                                courseId = courseId,
                                title = fileName,
                                fileType = ext,
                                sizeText = TimeFormatUtils.formatFileSize(sizeBytes),
                                updatedAt = updatedAt,
                                uploadId = uploadId,
                                referenceId = uObj.optLong("reference_id") ?: 0L,
                            ),
                        )
                    }
                } else {
                    items.add(
                        CoursewareItem(
                            id = activityId,
                            courseId = courseId,
                            title = activityTitle,
                            fileType = obj.optStr("type").uppercase().ifBlank { "DOC" },
                            sizeText = "在线资料",
                            updatedAt = updatedAt,
                        ),
                    )
                }
            }
            items
        }
    }

    suspend fun getUploadDownloadUrl(uploadId: Long): Result<String> = withContext(Dispatchers.IO) {
        if (uploadId <= 0L) {
            return@withContext Result.failure(IllegalArgumentException("无效的文件 ID"))
        }
        getJson("$baseUrl/api/uploads/$uploadId/url?preview=true").map { root ->
            val obj = root.optObj()
            val rawUrl = obj?.optStr("url").orEmpty().ifBlank { "$baseUrl/api/uploads/$uploadId/blob?preview=true" }
            if (rawUrl.startsWith("http")) rawUrl else "$baseUrl$rawUrl"
        }.recover {
            "$baseUrl/api/uploads/$uploadId/blob?preview=true"
        }
    }

    suspend fun resolveAttachmentPreview(
        uploadId: Long,
        hintFileName: String,
    ): Result<AttachmentPreviewInfo> = withContext(Dispatchers.IO) {
        if (uploadId <= 0L) {
            return@withContext Result.failure(IllegalArgumentException("无效的文件 ID"))
        }
        try {
            coroutineScope {
                val infoDeferred = async { getJson("$baseUrl/api/uploads/$uploadId") }
                val wpsDeferred = async { getJson("$baseUrl/api/uploads/$uploadId/preview/private-wps") }
                val aliyunDeferred = async { getJson("$baseUrl/api/aliyun-office/token?upload_id=$uploadId&is_mobile=true") }
                val directDeferred = async { getUploadDownloadUrl(uploadId) }

                val infoObj = infoDeferred.await().getOrNull()?.optObj()
                val resolvedName = infoObj?.optStr("name")?.ifBlank { null } ?: hintFileName
                val sizeBytes = infoObj?.optLong("size") ?: 0L
                val rawType = infoObj?.optStr("type").orEmpty()
                val refId = infoObj?.optLong("reference_id") ?: 0L
                val ext = resolvedName.substringAfterLast('.', "").lowercase()

                val isDocCandidate = rawType.equals("document", ignoreCase = true) ||
                    ext in setOf("pdf", "doc", "docx", "ppt", "pptx", "xls", "xlsx", "wps", "rtf")

                var pdfPreviewUrl: String? = null
                if (isDocCandidate) {
                    val docUrlRes = if (refId > 0L) {
                        getJson("$baseUrl/api/uploads/reference/document/$refId/url?preview=true")
                    } else {
                        getJson("$baseUrl/api/uploads/document/$uploadId/url?preview=true")
                    }
                    val docRaw = docUrlRes.getOrNull()?.optObj()?.optStr("url").orEmpty()
                        .ifBlank {
                            if (refId > 0L) {
                                getJson("$baseUrl/api/uploads/document/$uploadId/url?preview=true")
                                    .getOrNull()?.optObj()?.optStr("url").orEmpty()
                            } else {
                                ""
                            }
                        }
                    if (docRaw.isNotBlank()) {
                        pdfPreviewUrl = if (docRaw.startsWith("http")) docRaw else "$baseUrl$docRaw"
                    }
                }

                val wpsUrl = wpsDeferred.await().getOrNull()?.optObj()
                    ?.optStr("preview_url")
                    ?.takeIf { it.isNotBlank() }
                    ?.let { if (it.startsWith("http")) it else "$baseUrl$it" }

                val aliyunObj = aliyunDeferred.await().getOrNull()?.optObj()
                val aliyunPreviewUrl = aliyunObj?.let { obj ->
                    obj.optStr("PreviewURL")
                        .ifBlank { obj.optStr("WebofficeURL") }
                        .ifBlank { obj.optStr("WebofficeUrl") }
                        .ifBlank { obj.optStr("EditURL") }
                }?.takeIf { it.isNotBlank() }
                val aliyunAccessToken = aliyunObj?.optStr("AccessToken")?.takeIf { it.isNotBlank() }

                val directUrl = directDeferred.await().getOrNull()
                    ?: "$baseUrl/api/uploads/$uploadId/blob?preview=true"

                val onlineUrl = wpsUrl ?: aliyunPreviewUrl

                val effectivePdfUrl = when {
                    ext == "pdf" -> pdfPreviewUrl ?: directUrl
                    pdfPreviewUrl != null -> pdfPreviewUrl
                    else -> null
                }

                val resolvedFileType = rawType.ifBlank {
                    ext.uppercase().ifBlank { "FILE" }
                }

                Result.success(
                    AttachmentPreviewInfo(
                        uploadId = uploadId,
                        fileName = resolvedName,
                        sizeBytes = sizeBytes,
                        fileType = resolvedFileType,
                        onlinePreviewUrl = onlineUrl,
                        pdfPreviewUrl = effectivePdfUrl,
                        directDownloadUrl = directUrl,
                        aliyunOfficeTokenJson = if (aliyunAccessToken != null && aliyunPreviewUrl != null) {
                            aliyunObj.toString()
                        } else {
                            null
                        },
                    ),
                )
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun downloadUrlToFile(
        url: String,
        destFile: File,
        maxBytes: Long = 64L * 1024 * 1024,
    ): Result<File> = withContext(Dispatchers.IO) {
        try {
            destFile.parentFile?.mkdirs()
            var currentUrl = url
            for (hop in 0 until 6) {
                val parsed = currentUrl.toHttpUrlOrNull()
                    ?: return@withContext Result.failure(IOException("无效的文件地址"))
                val host = parsed.host
                val isXmuHost = host.equals("xmu.edu.cn", ignoreCase = true) ||
                    host.endsWith(".xmu.edu.cn", ignoreCase = true)
                val client = if (isXmuHost) authClient.apiClient else cdnDownloadClient
                val req = Request.Builder().url(parsed).get().build()

                client.newCall(req).execute().use { resp ->
                    if (isXmuHost && (resp.code == 401 || isAuthRedirect(resp.code, resp.header("Location")))) {
                        return@withContext Result.failure(SessionExpiredException())
                    }
                    if (resp.code in 300..399) {
                        val loc = resp.header("Location").orEmpty()
                        if (loc.isBlank()) {
                            return@withContext Result.failure(IOException("重定向地址为空"))
                        }
                        currentUrl = parsed.resolve(loc)?.toString() ?: loc
                        return@use
                    }
                    if (!resp.isSuccessful) {
                        return@withContext Result.failure(IOException("下载失败（HTTP ${resp.code}）"))
                    }
                    val body = resp.body ?: return@withContext Result.failure(IOException("文件内容为空"))
                    val contentLength = body.contentLength()
                    if (contentLength > maxBytes) {
                        return@withContext Result.failure(IOException("文件过大（超过 ${maxBytes / (1024 * 1024)} MB），请使用系统应用打开"))
                    }
                    val tmpFile = File(destFile.parentFile, "${destFile.name}.tmp")
                    var total = 0L
                    body.byteStream().use { input ->
                        tmpFile.outputStream().use { output ->
                            val buf = ByteArray(16 * 1024)
                            while (true) {
                                val read = input.read(buf)
                                if (read < 0) break
                                total += read
                                if (total > maxBytes) {
                                    tmpFile.delete()
                                    return@withContext Result.failure(IOException("文件过大，建议使用外部应用打开"))
                                }
                                output.write(buf, 0, read)
                            }
                        }
                    }
                    if (destFile.exists()) destFile.delete()
                    if (!tmpFile.renameTo(destFile)) {
                        tmpFile.copyTo(destFile, overwrite = true)
                        tmpFile.delete()
                    }
                    return@withContext Result.success(destFile)
                }
            }
            Result.failure(IOException("重定向次数过多"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getCourseRollcallsOfStudent(
        courseId: Long,
        courseName: String,
        userId: Long,
    ): Result<List<RollcallHistoryItem>> = withContext(Dispatchers.IO) {
        getJson("$baseUrl/api/course/$courseId/student/$userId/rollcalls").map { root ->
            val arr = root.optObj()?.optArr("rollcalls") ?: root.optArr() ?: return@map emptyList()
            arr.mapNotNull { elem ->
                val obj = elem.optObj() ?: return@mapNotNull null
                val id = obj.optLong("rollcall_id") ?: obj.optLong("id") ?: return@mapNotNull null
                val title = obj.optStr("title").ifBlank { "课堂点名 #$id" }
                val rollcallStatus = obj.optStr("rollcall_status").ifBlank { "finished" }
                val isNumber = obj.optBool("is_number") ?: false
                val isRadar = obj.optBool("is_radar") ?: false
                val rawType = obj.optStr("type")
                val source = obj.optStr("source")
                val method = when {
                    isNumber || source == "number" -> "数字口令"
                    rawType.contains("qr", ignoreCase = true) || source.contains("qr", ignoreCase = true) -> "二维码签到"
                    isRadar || source == "radar" -> "雷达签到"
                    rawType == "self_registration" || source == "self_registration" -> "自主签到"
                    rawType == "auto_attendance" || source == "autoAttendance" -> "自动考勤"
                    else -> "教师点名"
                }

                val timeIso = obj.optStr("rollcall_time")
                    .ifBlank { obj.optStr("published_at") }
                    .ifBlank { obj.optStr("updated_at") }
                    .ifBlank { null }
                val timeText = if (timeIso != null) TimeFormatUtils.formatDeadline(timeIso) else "时间未知"

                val rawStatus = obj.optStr("status")
                val rawStatusType = obj.optStr("student_status")
                val rawDetail = obj.optStr("student_status_detail")

                val (mappedStatus, note) = mapRollcallHistoryStatus(
                    rollcallStatus = rollcallStatus,
                    rawStatus = rawStatus,
                    rawStatusType = rawStatusType,
                    rawDetail = rawDetail,
                )

                RollcallHistoryItem(
                    id = id,
                    courseId = courseId,
                    courseName = courseName,
                    title = title,
                    rollcallTimeText = timeText,
                    rollcallTimeIso = timeIso,
                    rollcallMethod = method,
                    rollcallStatus = rollcallStatus,
                    studentStatus = mappedStatus,
                    detailNote = note,
                )
            }
        }
    }

    private fun mapRollcallHistoryStatus(
        rollcallStatus: String,
        rawStatus: String,
        rawStatusType: String,
        rawDetail: String,
    ): Pair<RollcallRecordStatus, String> {
        val cleanDetail = TimeFormatUtils.stripHtmlToPlainText(rawDetail)
        if (rollcallStatus.equals("in_progress", ignoreCase = true) &&
            (rawStatus.isBlank() || rawStatus == "absent")
        ) {
            return RollcallRecordStatus.IN_PROGRESS to "点名进行中"
        }
        return when (rawStatus.lowercase()) {
            "on_call_fine", "on_call" -> RollcallRecordStatus.ON_CALL to cleanDetail
            "on_call_arrive_late" -> RollcallRecordStatus.LATE to cleanDetail.ifBlank { "迟到" }
            "on_call_leave_early" -> RollcallRecordStatus.EARLY_LEAVE to cleanDetail.ifBlank { "早退" }
            "on_call_arrive_late_leave_early" -> RollcallRecordStatus.LATE_AND_EARLY to cleanDetail.ifBlank { "迟到且早退" }
            "on_personal_leave" -> RollcallRecordStatus.ON_LEAVE to cleanDetail.ifBlank { "事假" }
            "on_sick_leave" -> RollcallRecordStatus.ON_LEAVE to cleanDetail.ifBlank { "病假" }
            "on_public_leave" -> RollcallRecordStatus.ON_LEAVE to cleanDetail.ifBlank { "公假" }
            "on_physiological_leave" -> RollcallRecordStatus.ON_LEAVE to cleanDetail.ifBlank { "生理假" }
            "on_funeral_leave" -> RollcallRecordStatus.ON_LEAVE to cleanDetail.ifBlank { "丧假" }
            "on_mental_health_leave" -> RollcallRecordStatus.ON_LEAVE to cleanDetail.ifBlank { "心理假" }
            "on_leave" -> RollcallRecordStatus.ON_LEAVE to cleanDetail.ifBlank { "请假" }
            "absent" -> RollcallRecordStatus.ABSENT to cleanDetail
            else -> when (rawStatusType.lowercase()) {
                "on_call" -> RollcallRecordStatus.ON_CALL to cleanDetail
                "on_leave" -> RollcallRecordStatus.ON_LEAVE to cleanDetail.ifBlank { "请假" }
                "absent" -> RollcallRecordStatus.ABSENT to cleanDetail
                else -> RollcallRecordStatus.UNKNOWN to cleanDetail
            }
        }
    }

    suspend fun uploadHomeworkFile(
        fileName: String,
        mimeType: String,
        bytes: ByteArray,
        onProgress: (Float) -> Unit = {},
    ): Result<AttachmentFile> = withContext(Dispatchers.IO) {
        try {
            onProgress(0.10f)
            val metaBody = buildJsonObject {
                put("name", fileName)
                put("size", bytes.size)
                put("parent_type", "homework_submission")
                put("parent_id", 0)
            }.toString().toRequestBody(jsonMediaType)

            val metaReq = Request.Builder()
                .url("$baseUrl/api/uploads")
                .post(metaBody)
                .build()

            val metaObj = authClient.apiClient.newCall(metaReq).execute().use { resp ->
                if (resp.code == 401 || isAuthRedirect(resp.code, resp.header("Location"))) {
                    throw SessionExpiredException()
                }
                if (!resp.isSuccessful) {
                    throw IOException("申请上传失败（HTTP ${resp.code}）")
                }
                val raw = resp.body?.string().orEmpty()
                json.parseToJsonElement(raw).optObj() ?: throw IOException("上传响应格式异常")
            }

            val uploadId = metaObj.optLong("id") ?: throw IOException("未获取到文件 ID")
            val key = metaObj.optStr("key")
            val storageType = metaObj.optStr("storage_type")
            val transcoder = metaObj.optStr("transcoder")
            val rawUploadUrl = metaObj.optStr("upload_url").ifBlank { metaObj.optStr("url") }
            val resolvedUploadUrl = if (rawUploadUrl.startsWith("http")) rawUploadUrl else "$baseUrl$rawUploadUrl"
            onProgress(0.25f)

            val safeMedia = runCatching { mimeType.toMediaType() }
                .getOrElse { "application/octet-stream".toMediaType() }

            when {
                resolvedUploadUrl.contains(".qiniup.com") ||
                    resolvedUploadUrl.contains("up.qbox.me") ||
                    resolvedUploadUrl.contains(".qiniu.com") -> {
                    val encodedKey = URLEncoder.encode(key, "UTF-8")
                    val tokenObj = getJson("$baseUrl/api/uptoken?key=$encodedKey").getOrThrow().optObj()
                    val upToken = tokenObj?.optStr("uptoken").orEmpty()
                    if (upToken.isBlank()) throw IOException("获取七牛上传凭证失败")

                    val multipart = MultipartBody.Builder()
                        .setType(MultipartBody.FORM)
                        .addFormDataPart("key", key)
                        .addFormDataPart("token", upToken)
                        .addFormDataPart("file", fileName, bytes.toRequestBody(safeMedia))
                        .build()

                    val upReq = Request.Builder().url(resolvedUploadUrl).post(multipart).build()
                    authClient.apiClient.newCall(upReq).execute().use { upResp ->
                        if (!upResp.isSuccessful) throw IOException("上传至云存储失败（HTTP ${upResp.code}）")
                    }
                }

                storageType.equals("S3", ignoreCase = true) && transcoder.equals("WRPC", ignoreCase = true) -> {
                    val putReq = Request.Builder()
                        .url(resolvedUploadUrl)
                        .put(bytes.toRequestBody(safeMedia))
                        .build()
                    authClient.apiClient.newCall(putReq).execute().use { putResp ->
                        if (!putResp.isSuccessful) throw IOException("S3 上传失败（HTTP ${putResp.code}）")
                    }
                    val cbReq = Request.Builder()
                        .url("$baseUrl/api/uploads/$uploadId/callback")
                        .post("{}".toRequestBody(jsonMediaType))
                        .build()
                    authClient.apiClient.newCall(cbReq).execute().close()
                }

                else -> {
                    val chunkSize = 4 * 1024 * 1024
                    if (bytes.size <= chunkSize) {
                        val multipart = MultipartBody.Builder()
                            .setType(MultipartBody.FORM)
                            .addFormDataPart("file", fileName, bytes.toRequestBody(safeMedia))
                            .build()
                        val upReq = Request.Builder().url(resolvedUploadUrl).post(multipart).build()
                        authClient.apiClient.newCall(upReq).execute().use { upResp ->
                            if (!upResp.isSuccessful) throw IOException("文件上传失败（HTTP ${upResp.code}）")
                        }
                    } else {
                        var offset = 0
                        while (offset < bytes.size) {
                            val end = minOf(offset + chunkSize, bytes.size)
                            val slice = bytes.copyOfRange(offset, end)
                            val multipart = MultipartBody.Builder()
                                .setType(MultipartBody.FORM)
                                .addFormDataPart("file", fileName, slice.toRequestBody(safeMedia))
                                .build()
                            val upReq = Request.Builder()
                                .url(resolvedUploadUrl)
                                .header("Content-Range", "bytes $offset-${end - 1}/${bytes.size}")
                                .post(multipart)
                                .build()
                            authClient.apiClient.newCall(upReq).execute().use { upResp ->
                                if (!upResp.isSuccessful) throw IOException("分片上传失败（HTTP ${upResp.code}）")
                            }
                            offset = end
                            onProgress(0.25f + 0.65f * (offset.toFloat() / bytes.size))
                        }
                        val doneReq = Request.Builder()
                            .url("$baseUrl/api/uploads/$uploadId/uploaded")
                            .put("{}".toRequestBody(jsonMediaType))
                            .build()
                        authClient.apiClient.newCall(doneReq).execute().close()
                    }
                }
            }

            onProgress(1.0f)
            Result.success(
                AttachmentFile(
                    id = uploadId,
                    name = fileName,
                    sizeBytes = bytes.size.toLong(),
                    type = fileName.substringAfterLast('.', "").uppercase(),
                ),
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun submitHomework(
        homeworkId: Long,
        comment: String,
        uploadIds: List<Long>,
        existingSubmissionId: Long? = null,
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val payload = buildJsonObject {
                put("comment", comment)
                put("is_draft", false)
                put("slides", buildJsonArray { })
                put(
                    "uploads",
                    buildJsonArray {
                        uploadIds.forEach { add(JsonPrimitive(it)) }
                    },
                )
                if (existingSubmissionId != null) {
                    put("submission_id", existingSubmissionId)
                }
            }.toString().toRequestBody(jsonMediaType)

            val reqBuilder = Request.Builder()
            val request = if (existingSubmissionId == null) {
                reqBuilder.url("$baseUrl/api/course/activities/$homeworkId/submissions").post(payload).build()
            } else {
                reqBuilder.url("$baseUrl/api/course/activities/$homeworkId/submissions?retract=true").put(payload).build()
            }

            authClient.apiClient.newCall(request).execute().use { resp ->
                if (resp.code == 401 || isAuthRedirect(resp.code, resp.header("Location"))) {
                    return@withContext Result.failure(SessionExpiredException())
                }
                if (resp.isSuccessful || resp.code == 201) {
                    Result.success(Unit)
                } else {
                    Result.failure(IOException("提交作业失败（HTTP ${resp.code}）"))
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun parseHomeworkObject(
        obj: JsonObject,
        fallbackCourseId: Long,
        fallbackCourseName: String,
    ): HomeworkItem? {
        val id = obj.optLong("id") ?: return null
        val courseId = obj.optLong("course_id") ?: fallbackCourseId
        val title = obj.optStr("title").ifBlank { "作业 #$id" }
        val endTime = obj.optStr("end_time").ifBlank { null }
        val dataObj = obj.optObj("data")
        val rawDesc = dataObj?.optStr("description")
            ?.ifBlank { obj.optStr("description") }
            .orEmpty()
        val description = TimeFormatUtils.stripHtmlToPlainText(rawDesc).ifBlank { "暂无详细文字要求" }
        val attachmentFiles = parseUploads(obj.optArr("uploads"))
        val submitted = obj.optBool("submitted") ?: false
        val scorePublished = obj.optBool("score_published") ?: false
        val score = obj.optScore("score")
        val status = when {
            !score.isNullOrBlank() || scorePublished -> HomeworkStatus.GRADED
            submitted -> HomeworkStatus.SUBMITTED
            else -> HomeworkStatus.PENDING
        }
        val (remaining, urgency) = TimeFormatUtils.computeRemainingAndUrgency(endTime, status)
        val allowResubmit = obj.optBool("allow_resubmit") ?: true
        val isGroup = obj.optBool("submit_by_group") ?: false

        return HomeworkItem(
            id = id,
            courseId = courseId,
            courseName = fallbackCourseName,
            title = title,
            deadlineText = TimeFormatUtils.formatDeadline(endTime),
            remainingText = remaining,
            urgency = urgency,
            status = status,
            description = description,
            attachments = attachmentFiles.map { it.name },
            allowResubmit = allowResubmit,
            score = score,
            endTimeIso = endTime,
            attachmentFiles = attachmentFiles,
            isGroupHomework = isGroup,
        )
    }

    private fun parseUploads(arr: JsonArray?): List<AttachmentFile> {
        if (arr == null) return emptyList()
        return arr.mapNotNull { elem ->
            val obj = elem.optObj() ?: return@mapNotNull null
            val id = obj.optLong("id") ?: obj.optLong("upload_id") ?: return@mapNotNull null
            val name = obj.optStr("name").ifBlank { obj.optStr("file_name") }.ifBlank { "附件_$id" }
            val size = obj.optLong("size") ?: 0L
            AttachmentFile(
                id = id,
                name = name,
                sizeBytes = size,
                type = name.substringAfterLast('.', "").uppercase(),
            )
        }
    }

    private fun getJson(url: String): Result<JsonElement> {
        return try {
            val req = Request.Builder().url(url).get().build()
            authClient.apiClient.newCall(req).execute().use { resp ->
                if (resp.code == 401 || isAuthRedirect(resp.code, resp.header("Location"))) {
                    return Result.failure(SessionExpiredException())
                }
                if (!resp.isSuccessful) {
                    return Result.failure(IOException("HTTP ${resp.code}"))
                }
                val raw = resp.body?.string().orEmpty()
                if (raw.isBlank()) {
                    return Result.success(JsonObject(emptyMap()))
                }
                Result.success(json.parseToJsonElement(raw))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun isAuthRedirect(code: Int, location: String?): Boolean {
        if (code !in 300..399) return false
        val loc = location.orEmpty()
        return loc.contains("c-identity") || loc.contains("ids.xmu.edu.cn")
    }

    private fun JsonElement?.optObj(): JsonObject? =
        if (this != null && this !is JsonNull) runCatching { jsonObject }.getOrNull() else null

    private fun JsonElement?.optArr(): JsonArray? =
        if (this != null && this !is JsonNull) runCatching { jsonArray }.getOrNull() else null

    private fun JsonObject.optObj(key: String): JsonObject? = this[key].optObj()

    private fun JsonObject.optArr(key: String): JsonArray? = this[key].optArr()

    private fun JsonObject.optStr(key: String): String {
        val elem = this[key] ?: return ""
        if (elem is JsonNull) return ""
        return runCatching { elem.jsonPrimitive.contentOrNull }.getOrNull().orEmpty()
    }

    private fun JsonObject.optLong(key: String): Long? {
        val elem = this[key] ?: return null
        if (elem is JsonNull) return null
        return runCatching {
            elem.jsonPrimitive.longOrNull ?: elem.jsonPrimitive.contentOrNull?.toLongOrNull()
        }.getOrNull()
    }

    private fun JsonObject.optBool(key: String): Boolean? {
        val elem = this[key] ?: return null
        if (elem is JsonNull) return null
        return runCatching { elem.jsonPrimitive.booleanOrNull }.getOrNull()
    }

    private fun JsonObject.optScore(key: String): String? {
        val elem = this[key] ?: return null
        if (elem is JsonNull) return null
        val prim = runCatching { elem.jsonPrimitive }.getOrNull() ?: return null
        val str = prim.contentOrNull?.trim()
        if (!str.isNullOrEmpty() && str != "null") return str
        val num = prim.doubleOrNull ?: return null
        return if (num % 1.0 == 0.0) num.toInt().toString() else num.toString()
    }
}
