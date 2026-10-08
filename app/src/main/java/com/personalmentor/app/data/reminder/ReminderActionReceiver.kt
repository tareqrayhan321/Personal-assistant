package com.personalmentor.app.data.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import com.personalmentor.app.domain.usecase.MarkReminderDoneUseCase
import com.personalmentor.app.domain.usecase.SnoozeReminderUseCase
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Handles the "Snooze" and "Done" buttons on a reminder notification. */
@AndroidEntryPoint
class ReminderActionReceiver : BroadcastReceiver() {

    @Inject lateinit var snoozeReminder: SnoozeReminderUseCase
    @Inject lateinit var markReminderDone: MarkReminderDoneUseCase

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent) // required by Hilt for injection
        val taskId = intent.getLongExtra(ReminderReceiver.EXTRA_TASK_ID, -1L)
        if (taskId < 0) return

        NotificationManagerCompat.from(context).cancel(taskId.toInt())

        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                when (intent.action) {
                    ACTION_SNOOZE -> snoozeReminder(taskId, intent.getIntExtra(EXTRA_MINUTES, DEFAULT_SNOOZE_MINUTES))
                    ACTION_DONE -> markReminderDone(taskId)
                }
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_SNOOZE = "com.personalmentor.app.action.SNOOZE"
        const val ACTION_DONE = "com.personalmentor.app.action.DONE"
        const val EXTRA_MINUTES = "snooze_minutes"
        const val DEFAULT_SNOOZE_MINUTES = 10
    }
}
