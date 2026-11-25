package cn.edu.xmu.plus.data

import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

object TimeFormatUtils {

    private val shanghaiZone: ZoneId = ZoneId.of("Asia/Shanghai")
    private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm", Locale.CHINA)
    private val monthDayTimeFormatter = DateTimeFormatter.ofPattern("M 月 d 日 HH:mm", Locale.CHINA)
    private val spaceDateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss", Locale.CHINA)

    fun parseIsoInstant(raw: String?): Instant? {
        val text = raw?.trim().orEmpty()
        if (text.isEmpty()) return null
        return runCatching { Instant.parse(text) }.getOrNull()
            ?: runCatching { OffsetDateTime.parse(text).toInstant() }.getOrNull()
            ?: runCatching {
                LocalDateTime.parse(text.replace('T', ' ').take(19), spaceDateTimeFormatter)
                    .atZone(shanghaiZone)
                    .toInstant()
            }.getOrNull()
    }

    fun formatDeadline(iso: String?, now: Instant = Instant.now()): String {
        val instant = parseIsoInstant(iso) ?: return "长期开放"
        val target = instant.atZone(shanghaiZone)
        val today = now.atZone(shanghaiZone).toLocalDate()
        val targetDate = target.toLocalDate()
        return when (targetDate) {
            today -> "今天 ${target.format(timeFormatter)}"
            today.plusDays(1) -> "明天 ${target.format(timeFormatter)}"
            today.minusDays(1) -> "昨天 ${target.format(timeFormatter)}"
            else -> target.format(monthDayTimeFormatter)
        }
    }

    fun computeRemainingAndUrgency(
        iso: String?,
        status: HomeworkStatus,
        now: Instant = Instant.now(),
    ): Pair<String, UrgencyLevel> {
        if (status == HomeworkStatus.GRADED) return "已出分" to UrgencyLevel.NONE
        if (status == HomeworkStatus.SUBMITTED) return "已提交" to UrgencyLevel.NONE

        val instant = parseIsoInstant(iso) ?: return "无截止时间" to UrgencyLevel.LOW
        val minutes = Duration.between(now, instant).toMinutes()
        if (minutes <= 0) return "已截止" to UrgencyLevel.HIGH
        val hours = minutes / 60
        return when {
            hours < 1 -> "剩 ${minutes.coerceAtLeast(1)} 分钟" to UrgencyLevel.HIGH
            hours < 48 -> "剩 $hours 小时" to UrgencyLevel.HIGH
            hours < 24 * 5 -> "剩 ${(hours + 23) / 24} 天" to UrgencyLevel.MEDIUM
            else -> "剩 ${(hours + 23) / 24} 天" to UrgencyLevel.LOW
        }
    }

    fun formatRelativeTime(iso: String?, now: Instant = Instant.now()): String {
        val instant = parseIsoInstant(iso) ?: return "刚刚"
        val minutes = Duration.between(instant, now).toMinutes()
        return when {
            minutes < 1 -> "刚刚"
            minutes < 60 -> "$minutes 分钟前"
            minutes < 24 * 60 -> "${minutes / 60} 小时前"
            minutes < 7 * 24 * 60 -> "${minutes / (24 * 60)} 天前"
            else -> instant.atZone(shanghaiZone).format(monthDayTimeFormatter)
        }
    }

    fun stripHtmlToPlainText(raw: String?): String {
        val text = raw?.trim().orEmpty()
        if (text.isEmpty()) return ""
        return text
            .replace(Regex("(?i)<br\\s*/?>"), "\n")
            .replace(Regex("(?i)</(p|div|li|h[1-6])>"), "\n")
            .replace(Regex("(?i)<li[^>]*>"), "• ")
            .replace(Regex("<[^>]+>"), "")
            .replace("&nbsp;", " ")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace(Regex("[ \\t]+\\n"), "\n")
            .replace(Regex("\\n{3,}"), "\n\n")
            .trim()
    }

    fun formatFileSize(bytes: Long): String {
        if (bytes <= 0L) return "未知大小"
        val kb = bytes / 1024.0
        val mb = kb / 1024.0
        return when {
            mb >= 1.0 -> String.format(Locale.US, "%.1f MB", mb)
            kb >= 1.0 -> String.format(Locale.US, "%.0f KB", kb)
            else -> "$bytes B"
        }
    }
}
