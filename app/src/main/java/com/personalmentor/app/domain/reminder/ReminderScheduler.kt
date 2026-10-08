package com.personalmentor.app.domain.reminder

import com.personalmentor.app.domain.model.TodoTask

interface ReminderScheduler {
    /** Schedules an alarm for [TodoTask.remindAt]. No-op if it is null or already in the past. */
    fun schedule(task: TodoTask)

    /** One-off alarm at [at] that does not replace the task's regular alarm (used to snooze repeating tasks). */
    fun scheduleSnooze(task: TodoTask, at: Long)

    /** Cancels both the regular and the snooze alarm. */
    fun cancel(taskId: Long)

    /** False on Android 12+ when the user has not allowed exact alarms (reminders may then arrive late). */
    fun canScheduleExact(): Boolean
}
