package cn.edu.xmu.plus.reminder

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import cn.edu.xmu.plus.data.OfflineCacheStore
import cn.edu.xmu.plus.data.WidgetDdlSnapshot
import cn.edu.xmu.plus.widget.DdlGlanceWidget
import java.util.concurrent.TimeUnit

class DdlReminderWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val cache = OfflineCacheStore(applicationContext)
        val snapshot = cache.getJson(WIDGET_SNAPSHOT_KEY, WidgetDdlSnapshot.serializer())?.data
        if (snapshot != null && snapshot.isLoggedIn && snapshot.items.isNotEmpty()) {
            DdlNotificationHelper.dispatchPendingReminders(applicationContext, snapshot.items)
        }
        DdlGlanceWidget.updateAllWidgets(applicationContext)
        return Result.success()
    }

    companion object {
        const val WIDGET_SNAPSHOT_KEY = "widget_ddl_snapshot"
        private const val PERIODIC_WORK_NAME = "xmu_plus_ddl_periodic"
        private const val IMMEDIATE_WORK_NAME = "xmu_plus_ddl_now"

        fun schedulePeriodic(context: Context) {
            val req = PeriodicWorkRequestBuilder<DdlReminderWorker>(6, TimeUnit.HOURS).build()
            WorkManager.getInstance(context.applicationContext).enqueueUniquePeriodicWork(
                PERIODIC_WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                req,
            )
        }

        fun cancelPeriodic(context: Context) {
            WorkManager.getInstance(context.applicationContext).cancelUniqueWork(PERIODIC_WORK_NAME)
        }

        fun runOnceNow(context: Context) {
            val req = OneTimeWorkRequestBuilder<DdlReminderWorker>().build()
            WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
                IMMEDIATE_WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                req,
            )
        }
    }
}
