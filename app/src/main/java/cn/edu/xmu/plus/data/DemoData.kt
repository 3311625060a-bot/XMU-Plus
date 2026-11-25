package cn.edu.xmu.plus.data

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable

@Serializable
enum class HomeworkStatus(val label: String) {
    PENDING("待提交"),
    SUBMITTED("已提交"),
    GRADED("已批改"),
}

@Serializable
enum class UrgencyLevel {
    HIGH,
    MEDIUM,
    LOW,
    NONE,
}

@Serializable
@Immutable
data class AttachmentFile(
    val id: Long,
    val name: String,
    val sizeBytes: Long = 0L,
    val type: String = "",
)

@Serializable
@Immutable
data class SemesterItem(
    val id: Long,
    val name: String,
    val code: String = "",
    val isActive: Boolean = false,
)

@Serializable
@Immutable
data class CourseItem(
    val id: Long,
    val name: String,
    val teacher: String,
    val code: String,
    val semester: String,
    val location: String,
    val schedule: String,
    val isPinned: Boolean,
    val unreadCount: Int,
    val pendingHomeworkCount: Int,
    val accentIndex: Int,
)

@Serializable
@Immutable
data class ActiveRollcall(
    val id: Long,
    val courseName: String,
    val teacher: String,
    val type: String, // "number", "qr", "radar", etc.
    val startedAt: String,
    val courseId: Long = 0L,
    val status: String = "",
    val isRadar: Boolean = false,
    val isNumber: Boolean = false,
)

@Serializable
@Immutable
data class HomeworkItem(
    val id: Long,
    val courseId: Long,
    val courseName: String,
    val title: String,
    val deadlineText: String,
    val remainingText: String,
    val urgency: UrgencyLevel,
    val status: HomeworkStatus,
    val description: String,
    val attachments: List<String>,
    val allowResubmit: Boolean = true,
    val submittedAt: String? = null,
    val submittedContent: String? = null,
    val submittedFiles: List<String> = emptyList(),
    val score: String? = null,
    val teacherComment: String? = null,
    val endTimeIso: String? = null,
    val attachmentFiles: List<AttachmentFile> = emptyList(),
    val submittedAttachmentFiles: List<AttachmentFile> = emptyList(),
    val submissionId: Long? = null,
    val isGroupHomework: Boolean = false,
    val groupId: Long? = null,
    val fullScore: String? = null,
    val duplicateRate: String? = null,
    val gradedAt: String? = null,
    val graderName: String? = null,
    val submissionCount: Int = 0,
)

@Serializable
@Immutable
data class CoursewareItem(
    val id: Long,
    val courseId: Long,
    val title: String,
    val fileType: String,
    val sizeText: String,
    val updatedAt: String,
    val uploadId: Long = 0L,
    val referenceId: Long = 0L,
)

@Serializable
@Immutable
data class BulletinItem(
    val id: Long,
    val courseId: Long,
    val title: String,
    val summary: String,
    val publishedAt: String,
    val isUnread: Boolean,
)

object DemoData {
    val activeRollcalls = listOf(
        ActiveRollcall(
            id = 90214L,
            courseName = "数据结构与算法",
            teacher = "林教授",
            type = "number",
            startedAt = "2 分钟前发起",
        ),
    )

    val courses = listOf(
        CourseItem(
            id = 107546L,
            name = "数据结构与算法",
            teacher = "林教授",
            code = "CS2014",
            semester = "2026-2027 第一学期",
            location = "海韵教学楼 102",
            schedule = "周二 3-4 节 · 周四 1-2 节",
            isPinned = true,
            unreadCount = 2,
            pendingHomeworkCount = 1,
            accentIndex = 0,
        ),
        CourseItem(
            id = 107812L,
            name = "操作系统原理",
            teacher = "陈副教授",
            code = "CS3005",
            semester = "2026-2027 第一学期",
            location = "翔安学武楼 A204",
            schedule = "周一 5-6 节 · 周三 3-4 节",
            isPinned = true,
            unreadCount = 1,
            pendingHomeworkCount = 1,
            accentIndex = 1,
        ),
        CourseItem(
            id = 107903L,
            name = "人工智能导论",
            teacher = "王教授",
            code = "AI1002",
            semester = "2026-2027 第一学期",
            location = "信息学院 210",
            schedule = "周三 7-8 节",
            isPinned = false,
            unreadCount = 0,
            pendingHomeworkCount = 1,
            accentIndex = 2,
        ),
        CourseItem(
            id = 107420L,
            name = "概率论与数理统计",
            teacher = "张副教授",
            code = "MA2011",
            semester = "2026-2027 第一学期",
            location = "公共教学楼 305",
            schedule = "周一 1-2 节 · 周五 3-4 节",
            isPinned = false,
            unreadCount = 0,
            pendingHomeworkCount = 0,
            accentIndex = 3,
        ),
        CourseItem(
            id = 107388L,
            name = "学术英语写作进阶",
            teacher = "Dr. Miller",
            code = "EN2040",
            semester = "2026-2027 第一学期",
            location = "南强二 401",
            schedule = "周四 5-6 节",
            isPinned = false,
            unreadCount = 1,
            pendingHomeworkCount = 0,
            accentIndex = 4,
        ),
    )

    val homeworks = listOf(
        HomeworkItem(
            id = 1040310L,
            courseId = 107546L,
            courseName = "数据结构与算法",
            title = "实验四：红黑树与 AVL 平衡性对比分析",
            deadlineText = "明天 23:59",
            remainingText = "剩 26 小时",
            urgency = UrgencyLevel.HIGH,
            status = HomeworkStatus.PENDING,
            description = "请使用 C++ 或 Kotlin 实现 AVL 树与红黑树的插入/删除操作，并在 10^5 随机序列下对比旋转次数与树高分布。\n\n要求提交：\n1. 核心源码压缩包或 PDF 实验报告；\n2. 简述两种树在频繁写入场景下的工程取舍。",
            attachments = listOf("Lab4_实验指导书.pdf", "benchmark_data.csv"),
        ),
        HomeworkItem(
            id = 1040455L,
            courseId = 107812L,
            courseName = "操作系统原理",
            title = "第四章作业：页式虚拟存储与 LRU 置换模拟",
            deadlineText = "11 月 27 日 23:59",
            remainingText = "剩 3 天",
            urgency = UrgencyLevel.MEDIUM,
            status = HomeworkStatus.PENDING,
            description = "编写模拟程序计算 FIFO、LRU 与 Clock 算法在不同物理页框数下的缺页率，并附上结果截图与简要分析。",
            attachments = listOf("OS_HW4_题目说明.docx"),
        ),
        HomeworkItem(
            id = 1040512L,
            courseId = 107903L,
            courseName = "人工智能导论",
            title = "A* 搜索与启发式函数可采纳性证明",
            deadlineText = "11 月 30 日 22:00",
            remainingText = "剩 6 天",
            urgency = UrgencyLevel.LOW,
            status = HomeworkStatus.PENDING,
            description = "完成课后习题 3.4、3.8，并给出八数码问题中曼哈顿距离满足一致性（Consistency）的严格推导。",
            attachments = emptyList(),
        ),
        HomeworkItem(
            id = 1039811L,
            courseId = 107420L,
            courseName = "概率论与数理统计",
            title = "第六次习题：中心极限定理与大数定律",
            deadlineText = "11 月 22 日 23:59",
            remainingText = "按时提交",
            urgency = UrgencyLevel.NONE,
            status = HomeworkStatus.SUBMITTED,
            description = "完成教材 P142 习题 1~6，拍照整理为单个 PDF 上传。",
            attachments = listOf("Ch6_参考习题集.pdf"),
            submittedAt = "11 月 22 日 20:14",
            submittedContent = "已完成全部 6 道大题推导并附详细步骤。",
            submittedFiles = listOf("概率论第六次作业_39120262204972.pdf"),
        ),
        HomeworkItem(
            id = 1039205L,
            courseId = 107388L,
            courseName = "学术英语写作进阶",
            title = "Abstract Critique & Peer Review Draft",
            deadlineText = "11 月 18 日 23:59",
            remainingText = "已出分",
            urgency = UrgencyLevel.NONE,
            status = HomeworkStatus.GRADED,
            description = "Select one IEEE/ACM conference abstract in your major and write a 350-word structural critique.",
            attachments = listOf("Critique_Rubric.pdf"),
            submittedAt = "11 月 18 日 19:40",
            submittedContent = "Critique submitted on structure, gap statement, and concise methodology.",
            submittedFiles = listOf("Abstract_Critique_Final.pdf"),
            score = "95 / 100",
            teacherComment = "Clear rhetorical analysis and strong academic tone. Great transition between background and research gap.",
        ),
    )

    val coursewares = listOf(
        CoursewareItem(1L, 107546L, "04_平衡二叉树与红黑树.pdf", "PDF", "4.2 MB", "昨天更新"),
        CoursewareItem(2L, 107546L, "03_散列表与冲突解决.pptx", "PPTX", "6.8 MB", "上周更新"),
        CoursewareItem(3L, 107812L, "Ch4_虚拟内存与页面置换.pdf", "PDF", "5.1 MB", "2 天前"),
        CoursewareItem(4L, 107903L, "Lecture05_Informed_Search.pdf", "PDF", "3.6 MB", "3 天前"),
        CoursewareItem(5L, 107420L, "第6章_大数定律与中心极限定理.pdf", "PDF", "2.9 MB", "上周"),
    )

    val bulletins = listOf(
        BulletinItem(
            id = 1L,
            courseId = 107546L,
            title = "本周四实验课机房调整通知",
            summary = "因海韵机房设备例行检修，本周四第 1-2 节上机地点调整至海韵教学楼 205，请大家互相转告。",
            publishedAt = "今天 14:20",
            isUnread = true,
        ),
        BulletinItem(
            id = 2L,
            courseId = 107812L,
            title = "期中随堂测验成绩已公布",
            summary = "期中测验参考答案已上传至课件区，对评分有疑问可在周五答疑时间联系助教复核。",
            publishedAt = "昨天 18:05",
            isUnread = true,
        ),
        BulletinItem(
            id = 3L,
            courseId = 107388L,
            title = "Presentation Topic Selection Open",
            summary = "Please confirm your 5-minute lightning talk topic before next Wednesday.",
            publishedAt = "3 天前",
            isUnread = false,
        ),
    )
}
