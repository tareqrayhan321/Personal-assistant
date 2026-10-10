package com.personalmentor.app.data.schedule

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.personalmentor.app.MainActivity

/** Notifications of scheduled runs: progress (foreground), result, and "approval needed". */
object ScheduleNotifier {
    private const val PROGRESS_CHANNEL = "scheduled_progress"
    private const val RESULT_CHANNEL = "scheduled_results"
    private const val RESULT_ID_BASE = 200_000
    private const val APPROVAL_ID_BASE = 300_000
    private const val PREVIEW_CHARS = 200

    fun createChannels(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(PROGRESS_CHANNEL, "Scheduled task progress", NotificationManager.IMPORTANCE_LOW),
        )
        manager.createNotificationChannel(
            NotificationChannel(RESULT_CHANNEL, "Scheduled task results", NotificationManager.IMPORTANCE_DEFAULT),
        )
    }

    fun progressNotification(context: Context): Notification =
        NotificationCompat.Builder(context, PROGRESS_CHANNEL)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentTitle("Running a scheduled task")
            .setOngoing(true)
            .setContentIntent(openApp(context))
            .build()

    @SuppressLint("MissingPermission")
    fun showResult(context: Context, taskId: Long, title: String, success: Boolean, text: String) {
        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled()) return
        val body = text.trim().replace('\n', ' ').take(PREVIEW_CHARS)
        val notification = NotificationCompat.Builder(context, RESULT_CHANNEL)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle(if (success) title else "$title failed")
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(openApp(context))
            .setAutoCancel(true)
            .build()
        manager.notify(RESULT_ID_BASE + taskId.toInt(), notification)
    }

    @SuppressLint("MissingPermission")
    fun showApprovalNeeded(context: Context, taskId: Long, title: String) {
        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled()) return
        val notification = NotificationCompat.Builder(context, RESULT_CHANNEL)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle("Approval needed")
            .setContentText("\"$title\" is waiting for your OK. Open the app within 5 minutes.")
            .setContentIntent(openApp(context))
            .setAutoCancel(true)
            .build()
        manager.notify(APPROVAL_ID_BASE + taskId.toInt(), notification)
    }

    private fun openApp(context: Context): PendingIntent = PendingIntent.getActivity(
        context,
        0,
        Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )
}
