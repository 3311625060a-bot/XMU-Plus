package cn.edu.xmu.plus.reminder

import cn.edu.xmu.plus.data.UrgencyLevel
import cn.edu.xmu.plus.data.WidgetDdlItem
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

class DdlAlertEvaluatorTest {

    @Test
    fun classifiesThreeHourAndTwentyFourHourUrgentDeadlines() {
        val now = Instant.parse("2026-11-25T10:00:00Z")
        val items = listOf(
            WidgetDdlItem(
                id = 1L,
                courseName = "线性代数I",
                title = "2小时后截止",
                deadlineText = "今天 20:00",
                remainingText = "剩 2 小时",
                endTimeIso = now.plusSeconds(2 * 3600).toString(),
                urgency = UrgencyLevel.HIGH,
            ),
            WidgetDdlItem(
                id = 2L,
                courseName = "微积分I-1",
                title = "12小时后截止",
                deadlineText = "明天 06:00",
                remainingText = "剩 12 小时",
                endTimeIso = now.plusSeconds(12 * 3600).toString(),
                urgency = UrgencyLevel.MEDIUM,
            ),
            WidgetDdlItem(
                id = 3L,
                courseName = "操作系统",
                title = "3天后截止",
                deadlineText = "3天后",
                remainingText = "剩 3 天",
                endTimeIso = now.plusSeconds(72 * 3600).toString(),
                urgency = UrgencyLevel.LOW,
            ),
            WidgetDdlItem(
                id = 4L,
                courseName = "已过期",
                title = "昨天截止",
                deadlineText = "昨天",
                remainingText = "已截止",
                endTimeIso = now.minusSeconds(3600).toString(),
                urgency = UrgencyLevel.NONE,
            ),
        )

        val alerts = DdlNotificationHelper.evaluateUrgentAlerts(items, nowMillis = now.toEpochMilli())
        assertEquals(2, alerts.size)
        assertEquals("3h", alerts[0].bucket)
        assertEquals("24h", alerts[1].bucket)
    }
}
