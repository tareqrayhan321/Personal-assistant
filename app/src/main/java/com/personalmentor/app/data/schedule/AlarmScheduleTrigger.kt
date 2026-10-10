package com.personalmentor.app.data.schedule

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.personalmentor.app.domain.schedule.ScheduleTrigger
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AlarmScheduleTrigger @Inject constructor(
    @ApplicationContext private val context: Context,
) : ScheduleTrigger {

    private val alarmManager: AlarmManager = context.getSystemService(AlarmManager::class.java)

    override fun arm(taskId: Long, at: Long) {
        val intent = pendingIntent(taskId, PendingIntent.FLAG_UPDATE_CURRENT) ?: return
        val exactAllowed = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()
        try {
            if (exactAllowed) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, intent)
            } else {
                // Without exact-alarm access the system may delay the run by a few minutes.
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, intent)
            }
        } catch (e: SecurityException) {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, intent)
        }
    }

    override fun cancel(taskId: Long) {
        pendingIntent(taskId, PendingIntent.FLAG_NO_CREATE)?.let {
            alarmManager.cancel(it)
            it.cancel()
        }
    }

    override fun runNow(taskId: Long) = ScheduleWork.enqueue(context, taskId)

    private fun pendingIntent(taskId: Long, flag: Int): PendingIntent? {
        val intent = Intent(context, ScheduleAlarmReceiver::class.java)
            .putExtra(ScheduleAlarmReceiver.EXTRA_TASK_ID, taskId)
        return PendingIntent.getBroadcast(context, taskId.toInt(), intent, flag or PendingIntent.FLAG_IMMUTABLE)
    }
}
