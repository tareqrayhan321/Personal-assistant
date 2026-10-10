package com.personalmentor.app.domain.schedule

/** Wakes the app when a scheduled task is due (implemented with AlarmManager + WorkManager). */
interface ScheduleTrigger {
    /** One alarm per task; arming again replaces the previous one. */
    fun arm(taskId: Long, at: Long)
    fun cancel(taskId: Long)

    /** Runs the task now, independent of its schedule. */
    fun runNow(taskId: Long)
}
