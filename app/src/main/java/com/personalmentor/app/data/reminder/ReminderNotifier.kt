package com.personalmentor.app.data.reminder

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.personalmentor.app.MainActivity

object ReminderNotifier {
    const val CHANNEL_ID = "task_reminders"

    /** Snooze buttons shown on the notification (minutes). Android allows at most 3 actions. */
    private val SNOOZE_OPTIONS_MINUTES = listOf(10, 60)

    fun createChannel(context: Context) {
        val channel = NotificationChannel(CHANNEL_ID, "Task reminders", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "Reminders for your tasks"
        }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    // Notifications are skipped (not crashed) when the user has denied POST_NOTIFICATIONS.
    @SuppressLint("MissingPermission")
    fun show(context: Context, taskId: Long, title: String) {
        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled()) return

        val openApp = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle("Reminder")
            .setContentText(title)
            .setStyle(NotificationCompat.BigTextStyle().bigText(title))
            .setContentIntent(openApp)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)

        SNOOZE_OPTIONS_MINUTES.forEach { minutes ->
            builder.addAction(0, snoozeLabel(minutes), actionIntent(context, taskId, ReminderActionReceiver.ACTION_SNOOZE, minutes))
        }
        builder.addAction(0, "Done", actionIntent(context, taskId, ReminderActionReceiver.ACTION_DONE, null))

        manager.notify(taskId.toInt(), builder.build())
    }

    private fun snoozeLabel(minutes: Int) =
        if (minutes < 60) "Snooze $minutes min" else "Snooze ${minutes / 60} h"

    private fun actionIntent(context: Context, taskId: Long, action: String, minutes: Int?): PendingIntent {
        val intent = Intent(context, ReminderActionReceiver::class.java)
            .setAction(action)
            // The data URI makes each button's PendingIntent unique (extras alone are ignored when matching).
            .setData(Uri.parse("reminder://task/$taskId/${action.substringAfterLast('.')}/${minutes ?: 0}"))
            .putExtra(ReminderReceiver.EXTRA_TASK_ID, taskId)
        minutes?.let { intent.putExtra(ReminderActionReceiver.EXTRA_MINUTES, it) }
        return PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }
}
