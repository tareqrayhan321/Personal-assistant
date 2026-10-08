package com.personalmentor.app.domain.usecase

import com.personalmentor.app.domain.model.Repeat
import com.personalmentor.app.domain.model.TodoTask
import com.personalmentor.app.domain.reminder.ReminderScheduler
import com.personalmentor.app.domain.repository.TaskRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private const val HOUR = 3_600_000L
private const val DAY = 24 * HOUR

class FakeTaskRepository : TaskRepository {
    val tasks = MutableStateFlow<Map<Long, TodoTask>>(emptyMap())
    override fun observeTasks(): Flow<List<TodoTask>> = tasks.map { it.values.toList() }
    override suspend fun getTasks(includeDone: Boolean) = tasks.value.values.filter { includeDone || !it.isDone }
    override suspend fun getTask(id: Long) = tasks.value[id]
    override suspend fun getPendingReminders(now: Long) =
        tasks.value.values.filter { !it.isDone && (it.remindAt ?: 0L) > now }
    override suspend fun getRepeatingActive() =
        tasks.value.values.filter { !it.isDone && it.remindAt != null && it.repeat != Repeat.NONE }
    override suspend fun addTask(task: TodoTask): Long {
        val id = (tasks.value.keys.maxOrNull() ?: 0L) + 1
        tasks.value = tasks.value + (id to task.copy(id = id))
        return id
    }
    override suspend fun setDone(id: Long, done: Boolean) = update(id) { it.copy(isDone = done) }
    override suspend fun setReminder(id: Long, remindAt: Long?, repeat: Repeat) =
        update(id) { it.copy(remindAt = remindAt, repeat = repeat) }
    override suspend fun deleteTask(id: Long) { tasks.value = tasks.value - id }
    private fun update(id: Long, f: (TodoTask) -> TodoTask) {
        tasks.value[id]?.let { tasks.value = tasks.value + (id to f(it)) }
    }
}

class FakeScheduler : ReminderScheduler {
    val scheduled = mutableMapOf<Long, Long>()
    val snoozed = mutableMapOf<Long, Long>()
    val cancelled = mutableListOf<Long>()
    override fun schedule(task: TodoTask) { task.remindAt?.let { scheduled[task.id] = it } }
    override fun scheduleSnooze(task: TodoTask, at: Long) { snoozed[task.id] = at }
    override fun cancel(taskId: Long) { cancelled += taskId; scheduled.remove(taskId); snoozed.remove(taskId) }
    var exactAllowed = true
    override fun canScheduleExact() = exactAllowed
}

class RepeatingReminderTest {
    private val repo = FakeTaskRepository()
    private val scheduler = FakeScheduler()
    private val setReminder = SetReminderUseCase(repo, scheduler)
    private val setDone = SetTaskDoneUseCase(repo, scheduler)
    private val now get() = System.currentTimeMillis()

    private fun add(remindAt: Long?, repeat: Repeat, done: Boolean = false): Long = runBlocking {
        repo.addTask(TodoTask(title = "Vitamins", remindAt = remindAt, repeat = repeat, isDone = done))
    }

    @Test fun firingARepeatingTaskArmsTheNextOccurrence() = runBlocking<Unit> {
        val id = add(now - 1_000, Repeat.DAILY)
        val fired = HandleReminderFiredUseCase(repo, scheduler)(id, isSnooze = false)
        assertEquals("Vitamins", fired?.title)
        val next = repo.getTask(id)!!.remindAt!!
        assertTrue(next > now && next <= now + DAY + HOUR)
        assertEquals(next, scheduler.scheduled[id])
        assertEquals(Repeat.DAILY, repo.getTask(id)!!.repeat)
    }

    @Test fun firingASnoozeAlarmDoesNotMoveTheSeries() = runBlocking<Unit> {
        val at = now + 5 * HOUR
        val id = add(at, Repeat.DAILY)
        HandleReminderFiredUseCase(repo, scheduler)(id, isSnooze = true)
        assertEquals(at, repo.getTask(id)!!.remindAt)
        assertTrue(scheduler.scheduled.isEmpty())
    }

    @Test fun firingAOneOffTaskLeavesItAlone() = runBlocking<Unit> {
        val at = now - 1_000
        val id = add(at, Repeat.NONE)
        assertEquals(id, HandleReminderFiredUseCase(repo, scheduler)(id, false)?.id)
        assertEquals(at, repo.getTask(id)!!.remindAt)
        assertTrue(scheduler.scheduled.isEmpty())
    }

    @Test fun completedOrDeletedTasksDoNotNotify() = runBlocking<Unit> {
        val done = add(now - 1_000, Repeat.DAILY, done = true)
        assertNull(HandleReminderFiredUseCase(repo, scheduler)(done, false))
        assertNull(HandleReminderFiredUseCase(repo, scheduler)(999, false))
    }

    @Test fun doneOnTheNotificationOnlyDismissesARepeatingTask() = runBlocking<Unit> {
        val id = add(now + HOUR, Repeat.WEEKLY)
        assertTrue(MarkReminderDoneUseCase(repo, setDone)(id))
        assertEquals(false, repo.getTask(id)!!.isDone)
    }

    @Test fun doneOnTheNotificationCompletesAOneOffTask() = runBlocking<Unit> {
        val id = add(now + HOUR, Repeat.NONE)
        assertTrue(MarkReminderDoneUseCase(repo, setDone)(id))
        assertEquals(true, repo.getTask(id)!!.isDone)
        assertTrue(id in scheduler.cancelled)
    }

    @Test fun snoozingARepeatingTaskKeepsItsNextOccurrence() = runBlocking<Unit> {
        val at = now + 5 * HOUR
        val id = add(at, Repeat.DAILY)
        assertTrue(SnoozeReminderUseCase(repo, setReminder, scheduler)(id, 15))
        assertEquals(at, repo.getTask(id)!!.remindAt)
        assertTrue(scheduler.snoozed.getValue(id) in (now + 14 * 60_000L)..(now + 16 * 60_000L))
    }

    @Test fun snoozingAOneOffTaskMovesItsReminder() = runBlocking<Unit> {
        val id = add(now + 5 * HOUR, Repeat.NONE)
        assertTrue(SnoozeReminderUseCase(repo, setReminder, scheduler)(id, 15))
        assertTrue(repo.getTask(id)!!.remindAt!! in (now + 14 * 60_000L)..(now + 16 * 60_000L))
        assertTrue(scheduler.snoozed.isEmpty())
    }

    @Test fun uncompletingARepeatingTaskResumesAtTheNextFutureOccurrence() = runBlocking<Unit> {
        val id = add(now - 3 * DAY, Repeat.DAILY, done = true)
        assertTrue(setDone(id, false))
        val next = repo.getTask(id)!!.remindAt!!
        assertTrue(next > now && next <= now + DAY + HOUR)
        assertEquals(next, scheduler.scheduled[id])
    }

    @Test fun settingAReminderWithoutRepeatClearsOldRepeat() = runBlocking<Unit> {
        val id = add(now + HOUR, Repeat.DAILY)
        setReminder(id, now + 2 * HOUR)
        assertEquals(Repeat.NONE, repo.getTask(id)!!.repeat)
        setReminder(id, now + 2 * HOUR, Repeat.WEEKLY)
        assertEquals(Repeat.WEEKLY, repo.getTask(id)!!.repeat)
        setReminder(id, null, Repeat.WEEKLY)
        assertEquals(Repeat.NONE, repo.getTask(id)!!.repeat)
    }

    @Test fun rescheduleSkipsOverdueRepeatingRemindersAheadAndArmsAll() = runBlocking<Unit> {
        val overdue = add(now - 5 * DAY, Repeat.DAILY)
        val future = add(now + 3 * HOUR, Repeat.WEEKLY)
        val oneOff = add(now + HOUR, Repeat.NONE)
        RescheduleRemindersUseCase(repo, scheduler)()
        assertTrue(repo.getTask(overdue)!!.remindAt!! > now)
        assertTrue(overdue in scheduler.scheduled && future in scheduler.scheduled && oneOff in scheduler.scheduled)
    }
}
