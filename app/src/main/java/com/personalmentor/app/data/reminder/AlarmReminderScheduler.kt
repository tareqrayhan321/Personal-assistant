package com.personalmentor.app.data.reminder

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.personalmentor.app.domain.model.TodoTask
import com.personalmentor.app.domain.reminder.ReminderScheduler
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AlarmReminderScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
) : ReminderScheduler {

    private val alarmManager: AlarmManager = context.getSystemService(AlarmManager::class.java)

    override fun canScheduleExact(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()

    override fun schedule(task: TodoTask) {
        val triggerAt = task.remindAt ?: return
        if (triggerAt <= System.currentTimeMillis()) return
        arm(pendingIntent(task.id, PendingIntent.FLAG_UPDATE_CURRENT, task.title, snooze = false) ?: return, triggerAt)
    }

    override fun scheduleSnooze(task: TodoTask, at: Long) {
        if (at <= System.currentTimeMillis()) return
        arm(pendingIntent(task.id, PendingIntent.FLAG_UPDATE_CURRENT, task.title, snooze = true) ?: return, at)
    }

    private fun arm(intent: PendingIntent, triggerAt: Long) {
        try {
            if (canScheduleExact()) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, intent)
            } else {
                // Not allowed to use exact alarms: the system may delay this one by a few minutes.
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, intent)
            }
        } catch (e: SecurityException) {
            // Permission revoked between the check and the call.
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, intent)
        }
    }

    override fun cancel(taskId: Long) {
        listOf(false, true).forEach { snooze ->
            pendingIntent(taskId, PendingIntent.FLAG_NO_CREATE, title = "", snooze = snooze)?.let {
                alarmManager.cancel(it)
                it.cancel()
            }
        }
    }

    private fun pendingIntent(taskId: Long, flag: Int, title: String, snooze: Boolean): PendingIntent? {
        val intent = Intent(context, ReminderReceiver::class.java)
            .putExtra(ReminderReceiver.EXTRA_TASK_ID, taskId)
            .putExtra(ReminderReceiver.EXTRA_TITLE, title)
            .putExtra(ReminderReceiver.EXTRA_SNOOZE, snooze)
        // Distinct request codes keep the snooze alarm from replacing the regular one.
        val requestCode = if (snooze) -taskId.toInt() - 1 else taskId.toInt()
        return PendingIntent.getBroadcast(context, requestCode, intent, flag or PendingIntent.FLAG_IMMUTABLE)
    }
}
