package com.personalmentor.app.data.schedule

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.personalmentor.app.domain.repository.ScheduledTaskRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Fired by AlarmManager when a scheduled task is due. */
@AndroidEntryPoint
class ScheduleAlarmReceiver : BroadcastReceiver() {

    @Inject lateinit var repository: ScheduledTaskRepository

    override fun onReceive(context: Context, intent: Intent) {
        val taskId = intent.getLongExtra(EXTRA_TASK_ID, -1L)
        if (taskId < 0) return
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val task = repository.get(taskId)
                if (task != null && task.enabled) {
                    // Arm the next occurrence first, so the series survives even if this run fails.
                    repository.advance(taskId)
                    ScheduleWork.enqueue(context, taskId)
                }
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val EXTRA_TASK_ID = "scheduled_task_id"
    }
}
