package cn.edu.xmu.plus.reminder

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import cn.edu.xmu.plus.MainActivity
import cn.edu.xmu.plus.R
import cn.edu.xmu.plus.data.TimeFormatUtils
import cn.edu.xmu.plus.data.WidgetDdlItem

data class DdlAlertCandidate(
    val item: WidgetDdlItem,
    val bucket: String,
    val title: String,
    val body: String,
)

object DdlNotificationHelper {

    const val CHANNEL_ID = "xmu_ddl_alerts"
    private const val PREFS_NAME = "xmu_ddl_notified"
    private const val THREE_HOURS_MS = 3L * 60L * 60L * 1000L
    private const val TWENTY_FOUR_HOURS_MS = 24L * 60L * 60L * 1000L

    fun ensureChannel(context: Context) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "作业截止提醒",
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = "在畅课作业截止前 24 小时与 3 小时提醒提交"
        }
        manager.createNotificationChannel(channel)
    }

    fun evaluateUrgentAlerts(
        items: List<WidgetDdlItem>,
        nowMillis: Long = System.currentTimeMillis(),
    ): List<DdlAlertCandidate> {
        return items.mapNotNull { item ->
            val endMillis = TimeFormatUtils.parseIsoInstant(item.endTimeIso)?.toEpochMilli()
                ?: return@mapNotNull null
            val remainingMs = endMillis - nowMillis
            if (remainingMs <= 0L || remainingMs > TWENTY_FOUR_HOURS_MS) {
                return@mapNotNull null
            }
            val (bucket, prefix) = if (remainingMs <= THREE_HOURS_MS) {
                "3h" to "紧急截止"
            } else {
                "24h" to "即将截止"
            }
            val minutesLeft = (remainingMs / 60_000L).coerceAtLeast(1L)
            val countdown = when {
                minutesLeft < 60L -> "仅剩 ${minutesLeft} 分钟"
                else -> "剩 ${minutesLeft / 60L} 小时"
            }
            DdlAlertCandidate(
                item = item,
                bucket = bucket,
                title = "$prefix · ${item.courseName}",
                body = "${item.title}（$countdown，截止 ${item.deadlineText}）",
            )
        }
    }

    fun dispatchPendingReminders(context: Context, items: List<WidgetDdlItem>): Int {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) return 0
        }

        ensureChannel(context)
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val alerts = evaluateUrgentAlerts(items)
        if (alerts.isEmpty()) return 0

        val launchIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val contentIntent = PendingIntent.getActivity(
            context,
            0,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        var sentCount = 0
        val manager = NotificationManagerCompat.from(context)
        alerts.forEach { alert ->
            val dedupKey = "${alert.item.id}_${alert.bucket}_${alert.item.endTimeIso.orEmpty()}"
            if (prefs.getBoolean(dedupKey, false)) return@forEach

            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle(alert.title)
                .setContentText(alert.body)
                .setStyle(NotificationCompat.BigTextStyle().bigText(alert.body))
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true)
                .setContentIntent(contentIntent)
                .build()

            runCatching {
                manager.notify((alert.item.id % Int.MAX_VALUE).toInt(), notification)
                prefs.edit().putBoolean(dedupKey, true).apply()
                sentCount++
            }
        }
        return sentCount
    }
}
