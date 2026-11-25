package cn.edu.xmu.plus.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GlobalSearchEngineTest {

    private val sampleCourse = CourseItem(
        id = 100L,
        name = "微积分 I-1",
        teacher = "杨波",
        code = "MA101",
        semester = "2026-2027 第1学期",
        location = "海韵 102",
        schedule = "周一 1-2 节",
        isPinned = false,
        unreadCount = 0,
        pendingHomeworkCount = 1,
        accentIndex = 0,
    )

    private val sampleHw = HomeworkItem(
        id = 200L,
        courseId = 100L,
        courseName = "微积分 I-1",
        title = "极限与连续作业",
        deadlineText = "明天 23:59",
        remainingText = "剩 1 天",
        urgency = UrgencyLevel.HIGH,
        status = HomeworkStatus.PENDING,
        description = "请完成第三章习题",
        attachments = listOf("Ch3_Exercises.pdf"),
    )

    private val sampleDetail = CourseDetailUiState(
        isLoading = false,
        homeworks = listOf(sampleHw),
        coursewares = listOf(
            CoursewareItem(
                id = 300L,
                courseId = 100L,
                title = "第三章_极限讲义.pdf",
                fileType = "PDF",
                sizeText = "2.1 MB",
                updatedAt = "昨天",
                uploadId = 999L,
            ),
        ),
        bulletins = listOf(
            BulletinItem(
                id = 400L,
                courseId = 100L,
                title = "期中答疑安排",
                summary = "周五下午海韵楼答疑",
                publishedAt = "2 天前",
                isUnread = true,
            ),
        ),
    )

    private val dashboard = XmuDashboardState(
        isDemoMode = false,
        courses = listOf(sampleCourse),
        homeworks = listOf(sampleHw),
    )

    @Test
    fun searchAllMatchesCoursesHomeworksCoursewaresAndBulletins() {
        val hits = GlobalSearchEngine.search(
            query = "极限",
            category = SearchCategory.ALL,
            dashboardState = dashboard,
            courseDetails = mapOf(100L to sampleDetail),
        )
        assertEquals(2, hits.size)
        assertTrue(hits.any { it is GlobalSearchHit.HomeworkHit })
        assertTrue(hits.any { it is GlobalSearchHit.CoursewareHit })
    }

    @Test
    fun searchFiltersByCategoryAndBlankQueryReturnsEmpty() {
        assertTrue(
            GlobalSearchEngine.search(
                query = "   ",
                category = SearchCategory.ALL,
                dashboardState = dashboard,
                courseDetails = mapOf(100L to sampleDetail),
            ).isEmpty(),
        )

        val courseOnly = GlobalSearchEngine.search(
            query = "微积分",
            category = SearchCategory.COURSE,
            dashboardState = dashboard,
            courseDetails = mapOf(100L to sampleDetail),
        )
        assertEquals(1, courseOnly.size)
        assertTrue(courseOnly.first() is GlobalSearchHit.CourseHit)

        val resourceOnly = GlobalSearchEngine.search(
            query = "海韵楼",
            category = SearchCategory.RESOURCE,
            dashboardState = dashboard,
            courseDetails = mapOf(100L to sampleDetail),
        )
        assertEquals(1, resourceOnly.size)
        assertTrue(resourceOnly.first() is GlobalSearchHit.BulletinHit)
    }
}
