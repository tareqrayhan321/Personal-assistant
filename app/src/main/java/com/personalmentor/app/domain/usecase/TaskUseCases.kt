package com.personalmentor.app.domain.usecase

import com.personalmentor.app.domain.model.Repeat
import com.personalmentor.app.domain.model.TodoTask
import com.personalmentor.app.domain.reminder.RepeatSchedule
import com.personalmentor.app.domain.reminder.ReminderScheduler
import com.personalmentor.app.domain.repository.TaskRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class ObserveTasksUseCase @Inject constructor(private val repository: TaskRepository) {
    operator fun invoke(): Flow<List<TodoTask>> = repository.observeTasks()
}

class GetTasksUseCase @Inject constructor(private val repository: TaskRepository) {
    suspend operator fun invoke(includeDone: Boolean): List<TodoTask> = repository.getTasks(includeDone)
}

class CreateTaskUseCase @Inject constructor(
    private val repository: TaskRepository,
    private val scheduler: ReminderScheduler,
) {
    suspend operator fun invoke(task: TodoTask): TodoTask {
        val saved = task.copy(id = repository.addTask(task))
        scheduler.schedule(saved)
        return saved
    }
}

class SetTaskDoneUseCase @Inject constructor(
    private val repository: TaskRepository,
    private val scheduler: ReminderScheduler,
) {
    /** Returns false if the task does not exist. */
    suspend operator fun invoke(id: Long, done: Boolean): Boolean {
        val task = repository.getTask(id) ?: return false
        repository.setDone(id, done)
        if (done) {
            scheduler.cancel(id)
        } else {
            scheduler.schedule(rolledForward(task))
        }
        return true
    }

    /** A repeating task whose next occurrence passed while it was completed resumes at the next future one. */
    private suspend fun rolledForward(task: TodoTask): TodoTask {
        val at = task.remindAt
        val now = System.currentTimeMillis()
        if (task.repeat == Repeat.NONE || at == null || at > now) return task
        val next = RepeatSchedule.nextAfter(at, task.repeat, now)
        repository.setReminder(task.id, next, task.repeat)
        return task.copy(remindAt = next)
    }
}

class SetReminderUseCase @Inject constructor(
    private val repository: TaskRepository,
    private val scheduler: ReminderScheduler,
) {
    /** Returns false if the task does not exist. A null [remindAt] clears the reminder (and any repeat). */
    suspend operator fun invoke(id: Long, remindAt: Long?, repeat: Repeat = Repeat.NONE): Boolean {
        val task = repository.getTask(id) ?: return false
        val effectiveRepeat = if (remindAt == null) Repeat.NONE else repeat
        repository.setReminder(id, remindAt, effectiveRepeat)
        scheduler.cancel(id)
        if (remindAt != null && !task.isDone) scheduler.schedule(task.copy(remindAt = remindAt, repeat = effectiveRepeat))
        return true
    }
}

class DeleteTaskUseCase @Inject constructor(
    private val repository: TaskRepository,
    private val scheduler: ReminderScheduler,
) {
    /** Returns false if the task did not exist. */
    suspend operator fun invoke(id: Long): Boolean {
        val existed = repository.getTask(id) != null
        scheduler.cancel(id)
        repository.deleteTask(id)
        return existed
    }
}

/** Re-arms every pending alarm, e.g. after a device reboot. */
class RescheduleRemindersUseCase @Inject constructor(
    private val repository: TaskRepository,
    private val scheduler: ReminderScheduler,
) {
    suspend operator fun invoke() {
        val now = System.currentTimeMillis()
        // Repeating reminders that came due while the device was off skip ahead to their next occurrence.
        repository.getRepeatingActive().forEach { task ->
            val at = task.remindAt ?: return@forEach
            if (at <= now) repository.setReminder(task.id, RepeatSchedule.nextAfter(at, task.repeat, now), task.repeat)
        }
        repository.getPendingReminders(now).forEach(scheduler::schedule)
    }
}

class CanScheduleExactAlarmsUseCase @Inject constructor(private val scheduler: ReminderScheduler) {
    operator fun invoke(): Boolean = scheduler.canScheduleExact()
}

/** Pushes a task's reminder [minutes] into the future from now. Returns false if the task is missing or done. */
class SnoozeReminderUseCase @Inject constructor(
    private val repository: TaskRepository,
    private val setReminder: SetReminderUseCase,
    private val scheduler: ReminderScheduler,
) {
    suspend operator fun invoke(id: Long, minutes: Int): Boolean {
        val task = repository.getTask(id) ?: return false
        if (task.isDone) return false
        val at = System.currentTimeMillis() + minutes * 60_000L
        if (task.repeat != Repeat.NONE) {
            // A separate one-off alarm: the repeating series keeps its own next occurrence.
            scheduler.scheduleSnooze(task, at)
            return true
        }
        return setReminder(id, at)
    }
}

/**
 * Called when a reminder alarm fires. Returns the task to notify about, or null if it was deleted or
 * completed meanwhile. For a repeating task (and a non-snooze alarm) the next occurrence is armed first.
 */
class HandleReminderFiredUseCase @Inject constructor(
    private val repository: TaskRepository,
    private val scheduler: ReminderScheduler,
) {
    suspend operator fun invoke(id: Long, isSnooze: Boolean): TodoTask? {
        val task = repository.getTask(id) ?: return null
        if (task.isDone) return null
        if (!isSnooze && task.repeat != Repeat.NONE) {
            val now = System.currentTimeMillis()
            val next = RepeatSchedule.nextAfter(task.remindAt ?: now, task.repeat, now)
            repository.setReminder(id, next, task.repeat)
            scheduler.schedule(task.copy(remindAt = next))
        }
        return task
    }
}

/**
 * The notification's "Done" button. For a repeating task it only dismisses this occurrence (the next
 * one is already armed); completing the task itself, which ends the series, is done in the Tasks screen.
 */
class MarkReminderDoneUseCase @Inject constructor(
    private val repository: TaskRepository,
    private val setDone: SetTaskDoneUseCase,
) {
    suspend operator fun invoke(id: Long): Boolean {
        val task = repository.getTask(id) ?: return false
        if (task.repeat != Repeat.NONE) return true
        return setDone(id, true)
    }
}
