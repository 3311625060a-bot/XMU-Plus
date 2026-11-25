package cn.edu.xmu.plus.data

enum class SearchCategory(val label: String) {
    ALL("全部"),
    HOMEWORK("作业"),
    COURSE("课程"),
    RESOURCE("课件与公告"),
}

sealed interface GlobalSearchHit {
    val stableKey: String
    val title: String
    val subtitle: String

    data class CourseHit(
        val course: CourseItem,
    ) : GlobalSearchHit {
        override val stableKey: String = "course_${course.id}"
        override val title: String = course.name
        override val subtitle: String = "${course.teacher} · ${course.code} · ${course.semester}"
    }

    data class HomeworkHit(
        val homework: HomeworkItem,
    ) : GlobalSearchHit {
        override val stableKey: String = "hw_${homework.id}"
        override val title: String = homework.title
        override val subtitle: String = "${homework.courseName} · ${homework.status.label} · ${homework.deadlineText}"
    }

    data class CoursewareHit(
        val courseName: String,
        val courseware: CoursewareItem,
    ) : GlobalSearchHit {
        override val stableKey: String = "cw_${courseware.courseId}_${courseware.id}"
        override val title: String = courseware.title
        override val subtitle: String = "$courseName · 课件 (${courseware.fileType} · ${courseware.sizeText})"
    }

    data class BulletinHit(
        val courseName: String,
        val bulletin: BulletinItem,
    ) : GlobalSearchHit {
        override val stableKey: String = "bl_${bulletin.courseId}_${bulletin.id}"
        override val title: String = bulletin.title
        override val subtitle: String = "$courseName · 公告 · ${bulletin.publishedAt}"
    }
}

object GlobalSearchEngine {
    fun search(
        query: String,
        category: SearchCategory,
        dashboardState: XmuDashboardState,
        courseDetails: Map<Long, CourseDetailUiState>,
    ): List<GlobalSearchHit> {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return emptyList()

        val courseNames = dashboardState.courses.associate { it.id to it.name }
        val results = ArrayList<GlobalSearchHit>()

        if (category == SearchCategory.ALL || category == SearchCategory.COURSE) {
            dashboardState.courses.forEach { course ->
                if (course.name.contains(trimmed, ignoreCase = true) ||
                    course.teacher.contains(trimmed, ignoreCase = true) ||
                    course.code.contains(trimmed, ignoreCase = true) ||
                    course.location.contains(trimmed, ignoreCase = true)
                ) {
                    results.add(GlobalSearchHit.CourseHit(course))
                }
            }
        }

        if (category == SearchCategory.ALL || category == SearchCategory.HOMEWORK) {
            dashboardState.homeworks.forEach { hw ->
                if (hw.title.contains(trimmed, ignoreCase = true) ||
                    hw.courseName.contains(trimmed, ignoreCase = true) ||
                    hw.description.contains(trimmed, ignoreCase = true) ||
                    hw.attachments.any { it.contains(trimmed, ignoreCase = true) }
                ) {
                    results.add(GlobalSearchHit.HomeworkHit(hw))
                }
            }
        }

        if (category == SearchCategory.ALL || category == SearchCategory.RESOURCE) {
            val loadedCoursewares = courseDetails.values.flatMap { it.coursewares }
            val coursewares = if (dashboardState.isDemoMode && loadedCoursewares.isEmpty()) {
                DemoData.coursewares
            } else {
                loadedCoursewares
            }
            coursewares.forEach { cw ->
                val cName = courseNames[cw.courseId] ?: "课程 #${cw.courseId}"
                if (cw.title.contains(trimmed, ignoreCase = true) ||
                    cw.fileType.contains(trimmed, ignoreCase = true) ||
                    cName.contains(trimmed, ignoreCase = true)
                ) {
                    results.add(GlobalSearchHit.CoursewareHit(cName, cw))
                }
            }

            val loadedBulletins = courseDetails.values.flatMap { it.bulletins }
            val bulletins = if (dashboardState.isDemoMode && loadedBulletins.isEmpty()) {
                DemoData.bulletins
            } else {
                loadedBulletins
            }
            bulletins.forEach { bl ->
                val cName = courseNames[bl.courseId] ?: "课程 #${bl.courseId}"
                if (bl.title.contains(trimmed, ignoreCase = true) ||
                    bl.summary.contains(trimmed, ignoreCase = true) ||
                    cName.contains(trimmed, ignoreCase = true)
                ) {
                    results.add(GlobalSearchHit.BulletinHit(cName, bl))
                }
            }
        }

        return results
    }
}
