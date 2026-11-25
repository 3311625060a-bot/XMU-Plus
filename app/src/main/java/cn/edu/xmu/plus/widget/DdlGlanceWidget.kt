package cn.edu.xmu.plus.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import cn.edu.xmu.plus.MainActivity
import cn.edu.xmu.plus.data.OfflineCacheStore
import cn.edu.xmu.plus.data.UrgencyLevel
import cn.edu.xmu.plus.data.WidgetDdlItem
import cn.edu.xmu.plus.data.WidgetDdlSnapshot
import cn.edu.xmu.plus.reminder.DdlReminderWorker

class DdlGlanceWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val snapshot = OfflineCacheStore(context)
            .getJson(DdlReminderWorker.WIDGET_SNAPSHOT_KEY, WidgetDdlSnapshot.serializer())
            ?.data
            ?: WidgetDdlSnapshot()

        provideContent {
            GlanceTheme {
                DdlWidgetContent(snapshot = snapshot)
            }
        }
    }

    companion object {
        suspend fun updateAllWidgets(context: Context) {
            runCatching {
                DdlGlanceWidget().updateAll(context.applicationContext)
            }
        }
    }
}

class RefreshDdlWidgetAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters,
    ) {
        DdlGlanceWidget.updateAllWidgets(context)
    }
}

@Composable
private fun DdlWidgetContent(snapshot: WidgetDdlSnapshot) {
    val surfaceColor = ColorProvider(Color(0xFFF6F7F9))
    val cardColor = ColorProvider(Color(0xFFFFFFFF))
    val primaryColor = ColorProvider(Color(0xFF566E85))
    val onSurfaceColor = ColorProvider(Color(0xFF1F262D))
    val mutedColor = ColorProvider(Color(0xFF64707C))
    val badgeBg = ColorProvider(Color(0xFFD9E3ED))
    val urgentBg = ColorProvider(Color(0xFFF6DFE2))
    val urgentFg = ColorProvider(Color(0xFF9C4B55))

    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(surfaceColor)
            .cornerRadius(24.dp)
            .padding(14.dp)
            .clickable(actionStartActivity<MainActivity>()),
    ) {
        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "XMU Plus · 待交作业",
                style = TextStyle(
                    color = onSurfaceColor,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                ),
            )
            Spacer(modifier = GlanceModifier.defaultWeight())
            Box(
                modifier = GlanceModifier
                    .background(badgeBg)
                    .cornerRadius(10.dp)
                    .padding(horizontal = 8.dp, vertical = 3.dp)
                    .clickable(actionRunCallback<RefreshDdlWidgetAction>()),
            ) {
                Text(
                    text = if (snapshot.isLoggedIn) "${snapshot.pendingCount} 项" else "未登录",
                    style = TextStyle(
                        color = primaryColor,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                    ),
                )
            }
        }

        Spacer(modifier = GlanceModifier.height(8.dp))

        when {
            !snapshot.isLoggedIn -> {
                WidgetEmptyCard(
                    cardColor = cardColor,
                    textColor = mutedColor,
                    message = "点击打开 XMU Plus 登录并同步作业截止日期",
                )
            }

            snapshot.items.isEmpty() -> {
                WidgetEmptyCard(
                    cardColor = cardColor,
                    textColor = mutedColor,
                    message = "当前没有待提交作业 · ${snapshot.updatedAtText.ifBlank { "已同步" }}",
                )
            }

            else -> {
                Column(modifier = GlanceModifier.fillMaxWidth()) {
                    snapshot.items.take(3).forEachIndexed { index, item ->
                        if (index > 0) {
                            Spacer(modifier = GlanceModifier.height(6.dp))
                        }
                        WidgetHomeworkRow(
                            item = item,
                            cardColor = cardColor,
                            titleColor = onSurfaceColor,
                            mutedColor = mutedColor,
                            badgeBg = if (item.urgency == UrgencyLevel.HIGH) urgentBg else badgeBg,
                            badgeFg = if (item.urgency == UrgencyLevel.HIGH) urgentFg else primaryColor,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun WidgetHomeworkRow(
    item: WidgetDdlItem,
    cardColor: ColorProvider,
    titleColor: ColorProvider,
    mutedColor: ColorProvider,
    badgeBg: ColorProvider,
    badgeFg: ColorProvider,
) {
    Row(
        modifier = GlanceModifier
            .fillMaxWidth()
            .background(cardColor)
            .cornerRadius(14.dp)
            .padding(horizontal = 10.dp, vertical = 8.dp)
            .clickable(actionStartActivity<MainActivity>()),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = GlanceModifier.defaultWeight()) {
            Text(
                text = item.title,
                maxLines = 1,
                style = TextStyle(
                    color = titleColor,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                ),
            )
            Text(
                text = "${item.courseName} · ${item.deadlineText}",
                maxLines = 1,
                style = TextStyle(
                    color = mutedColor,
                    fontSize = 11.sp,
                ),
            )
        }
        Spacer(modifier = GlanceModifier.width(8.dp))
        Box(
            modifier = GlanceModifier
                .background(badgeBg)
                .cornerRadius(8.dp)
                .padding(horizontal = 6.dp, vertical = 3.dp),
        ) {
            Text(
                text = item.remainingText,
                style = TextStyle(
                    color = badgeFg,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium,
                ),
            )
        }
    }
}

@Composable
private fun WidgetEmptyCard(
    cardColor: ColorProvider,
    textColor: ColorProvider,
    message: String,
) {
    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(cardColor)
            .cornerRadius(16.dp)
            .padding(12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = message,
            style = TextStyle(
                color = textColor,
                fontSize = 12.sp,
            ),
        )
    }
}
