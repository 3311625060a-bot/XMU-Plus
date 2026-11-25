package cn.edu.xmu.plus.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import java.time.Instant

class TimeFormatUtilsTest {

    @Test
    fun parseIsoInstant_handlesUtcAndOffset() {
        assertNotNull(TimeFormatUtils.parseIsoInstant("2026-11-26T15:59:00Z"))
        assertNotNull(TimeFormatUtils.parseIsoInstant("2026-11-26T23:59:00+08:00"))
    }

    @Test
    fun computeRemainingAndUrgency_classifiesCorrectly() {
        val now = Instant.parse("2026-11-25T00:00:00Z")
        val urgent = TimeFormatUtils.computeRemainingAndUrgency("2026-11-25T10:00:00Z", HomeworkStatus.PENDING, now)
        assertEquals(UrgencyLevel.HIGH, urgent.second)
        assertEquals("剩 10 小时", urgent.first)

        val medium = TimeFormatUtils.computeRemainingAndUrgency("2026-11-28T00:00:00Z", HomeworkStatus.PENDING, now)
        assertEquals(UrgencyLevel.MEDIUM, medium.second)

        val graded = TimeFormatUtils.computeRemainingAndUrgency("2026-11-25T10:00:00Z", HomeworkStatus.GRADED, now)
        assertEquals(UrgencyLevel.NONE, graded.second)
        assertEquals("已出分", graded.first)
    }

    @Test
    fun stripHtmlToPlainText_cleansTagsAndEntities() {
        val html = "<p>请提交&nbsp;PDF&amp;源码<br/>要求：1&lt;2</p>"
        val clean = TimeFormatUtils.stripHtmlToPlainText(html)
        assertEquals("请提交 PDF&源码\n要求：1<2", clean)
    }
}
