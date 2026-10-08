package com.personalmentor.app.data.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.personalmentor.app.domain.usecase.HandleReminderFiredUseCase
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Fired by AlarmManager when a task reminder is due. */
@AndroidEntryPoint
class ReminderReceiver : BroadcastReceiver() {

    @Inject lateinit var handleReminderFired: HandleReminderFiredUseCase

    override fun onReceive(context: Context, intent: Intent) {
        val taskId = intent.getLongExtra(EXTRA_TASK_ID, -1L)
        if (taskId < 0) return
        val isSnooze = intent.getBooleanExtra(EXTRA_SNOOZE, false)
        val fallbackTitle = intent.getStringExtra(EXTRA_TITLE).orEmpty()

        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                // Null: the task was deleted or completed since the alarm was set.
                handleReminderFired(taskId, isSnooze)?.let { ReminderNotifier.show(context, it.id, it.title) }
            } catch (e: Exception) {
                // Database trouble must not swallow the reminder.
                if (fallbackTitle.isNotEmpty()) ReminderNotifier.show(context, taskId, fallbackTitle)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val EXTRA_TASK_ID = "task_id"
        const val EXTRA_TITLE = "task_title"
        const val EXTRA_SNOOZE = "is_snooze"
    }
}
