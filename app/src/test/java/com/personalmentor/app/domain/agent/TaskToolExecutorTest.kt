package com.personalmentor.app.domain.agent

import com.personalmentor.app.domain.model.Repeat
import com.personalmentor.app.domain.model.TodoTask
import com.personalmentor.app.domain.usecase.CanScheduleExactAlarmsUseCase
import com.personalmentor.app.domain.usecase.CreateTaskUseCase
import com.personalmentor.app.domain.usecase.DeleteTaskUseCase
import com.personalmentor.app.domain.usecase.FakeScheduler
import com.personalmentor.app.domain.usecase.FakeTaskRepository
import com.personalmentor.app.domain.usecase.GetTasksUseCase
import com.personalmentor.app.domain.usecase.SetReminderUseCase
import com.personalmentor.app.domain.usecase.SetTaskDoneUseCase
import com.personalmentor.app.domain.usecase.SnoozeReminderUseCase
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

class TaskToolExecutorTest {
    private val repo = FakeTaskRepository()
    private val scheduler = FakeScheduler()
    private val json = Json { ignoreUnknownKeys = true }
    private val setReminder = SetReminderUseCase(repo, scheduler)
    private val executor = TaskToolExecutor(
        createTask = CreateTaskUseCase(repo, scheduler),
        getTasks = GetTasksUseCase(repo),
        setDone = SetTaskDoneUseCase(repo, scheduler),
        deleteTask = DeleteTaskUseCase(repo, scheduler),
        setReminder = setReminder,
        snoozeReminder = SnoozeReminderUseCase(repo, setReminder, scheduler),
        canScheduleExactAlarms = CanScheduleExactAlarmsUseCase(scheduler),
        json = json,
    )

    private fun call(tool: String, args: String): JsonObject =
        runBlocking { json.parseToJsonElement(executor.execute(tool, args)).jsonObject }

    private fun JsonObject.ok() = getValue("ok").jsonPrimitive.boolean
    private fun JsonObject.error() = getValue("error").jsonPrimitive.content
    private fun inFuture(days: Long = 1) = LocalDateTime.now().plusDays(days).withNano(0).toString()
    private fun seed(title: String, done: Boolean = false, remindAt: Long? = null) =
        runBlocking { repo.addTask(TodoTask(title = title, isDone = done, remindAt = remindAt)) }

    // ---- create_task ----

    @Test fun createTaskStoresItAndReturnsIt() {
        val r = call("create_task", """{"title":"  Buy milk  ","notes":"2 litres"}""")
        assertTrue(r.ok())
        val task = r.getValue("task").jsonObject
        assertEquals("Buy milk", task.getValue("title").jsonPrimitive.content)
        assertEquals("Buy milk", runBlocking { repo.getTasks(true) }.single().title)
    }

    @Test fun createTaskRequiresATitle() {
        assertEquals("title is required", call("create_task", """{"title":"  "}""").error())
        assertEquals("title is required", call("create_task", "{}").error())
    }

    @Test fun createTaskWithReminderSchedulesAnAlarm() {
        val r = call("create_task", """{"title":"Call","remind_at":"${inFuture()}"}""")
        assertTrue(r.ok())
        assertEquals(1, scheduler.scheduled.size)
    }

    @Test fun pastReminderIsRejected() {
        val r = call("create_task", """{"title":"Call","remind_at":"2020-01-01T08:00"}""")
        assertFalse(r.ok())
        assertEquals("remind_at is in the past", r.error())
        assertTrue(runBlocking { repo.getTasks(true) }.isEmpty())
    }

    @Test fun createTaskWithRepeatKeepsIt() {
        val r = call("create_task", """{"title":"Vitamins","remind_at":"${inFuture()}","repeat":"Daily"}""")
        assertTrue(r.ok())
        assertEquals("daily", r.getValue("task").jsonObject.getValue("repeat").jsonPrimitive.content)
        assertEquals(Repeat.DAILY, runBlocking { repo.getTasks(true) }.single().repeat)
    }

    @Test fun repeatNeedsAFirstReminderTime() {
        val r = call("create_task", """{"title":"Vitamins","repeat":"daily"}""")
        assertFalse(r.ok())
        assertTrue(r.error().contains("remind_at"))
    }

    @Test fun unknownRepeatValueIsRejected() {
        val r = call("create_task", """{"title":"x","remind_at":"${inFuture()}","repeat":"hourly"}""")
        assertEquals("repeat must be one of: none, daily, weekly", r.error())
    }

    @Test fun exactAlarmWarningOnlyWhenNotAllowed() {
        val allowed = call("create_task", """{"title":"a","remind_at":"${inFuture()}"}""")
        assertNull(allowed["warning"])
        scheduler.exactAllowed = false
        val denied = call("create_task", """{"title":"b","remind_at":"${inFuture()}"}""")
        assertNotNull(denied["warning"])
    }

    // ---- list / complete / delete ----

    @Test fun listHidesCompletedByDefault() {
        seed("open"); seed("finished", done = true)
        val titles = call("list_tasks", "{}").getValue("tasks").jsonArray.map {
            it.jsonObject.getValue("title").jsonPrimitive.content
        }
        assertEquals(listOf("open"), titles)
        val all = call("list_tasks", """{"include_completed":true}""").getValue("tasks").jsonArray
        assertEquals(2, all.size)
    }

    @Test fun emptyArgumentStringIsAcceptedForListing() {
        assertTrue(call("list_tasks", "").ok())
    }

    @Test fun completeMarksDoneAndCancelsTheAlarm() {
        val id = seed("x", remindAt = System.currentTimeMillis() + 3_600_000)
        assertTrue(call("complete_task", """{"task_id":$id}""").ok())
        assertTrue(runBlocking { repo.getTask(id) }!!.isDone)
        assertTrue(id in scheduler.cancelled)
    }

    @Test fun completeUnknownTaskFails() {
        assertEquals("No task with id 99", call("complete_task", """{"task_id":99}""").error())
        assertEquals("task_id is required", call("complete_task", "{}").error())
    }

    @Test fun deleteRemovesTheTask() {
        val id = seed("x")
        assertTrue(call("delete_task", """{"task_id":$id}""").ok())
        assertNull(runBlocking { repo.getTask(id) })
        assertEquals("No task with id $id", call("delete_task", """{"task_id":$id}""").error())
    }

    // ---- set_reminder / snooze_task ----

    @Test fun setReminderUpdatesTaskAndRepeat() {
        val id = seed("x")
        val at = inFuture()
        val r = call("set_reminder", """{"task_id":$id,"remind_at":"$at","repeat":"weekly"}""")
        assertTrue(r.ok())
        assertEquals("weekly", r.getValue("repeat").jsonPrimitive.content)
        val task = runBlocking { repo.getTask(id) }!!
        assertEquals(Repeat.WEEKLY, task.repeat)
        assertNotNull(task.remindAt)
    }

    @Test fun setReminderValidatesInput() {
        val id = seed("x")
        assertTrue(call("set_reminder", """{"task_id":$id}""").error().contains("remind_at"))
        assertEquals("remind_at is in the past", call("set_reminder", """{"task_id":$id,"remind_at":"2020-01-01T08:00"}""").error())
        assertEquals("No task with id 77", call("set_reminder", """{"task_id":77,"remind_at":"${inFuture()}"}""").error())
    }

    @Test fun snoozeValidatesMinutes() {
        val id = seed("x", remindAt = System.currentTimeMillis() + 3_600_000)
        assertEquals("minutes is required", call("snooze_task", """{"task_id":$id}""").error())
        assertEquals("minutes must be between 1 and 10080", call("snooze_task", """{"task_id":$id,"minutes":0}""").error())
        assertEquals("minutes must be between 1 and 10080", call("snooze_task", """{"task_id":$id,"minutes":10081}""").error())
    }

    @Test fun snoozeMovesTheReminder() {
        val id = seed("x", remindAt = System.currentTimeMillis() + 3_600_000)
        val before = System.currentTimeMillis()
        assertTrue(call("snooze_task", """{"task_id":$id,"minutes":15}""").ok())
        val at = runBlocking { repo.getTask(id) }!!.remindAt!!
        assertTrue(at in (before + 14 * 60_000L)..(System.currentTimeMillis() + 16 * 60_000L))
    }

    @Test fun snoozingACompletedTaskFails() {
        val id = seed("x", done = true)
        assertFalse(call("snooze_task", """{"task_id":$id,"minutes":5}""").ok())
    }

    // ---- robustness ----

    @Test fun unknownToolAndBrokenJsonReturnErrorsInsteadOfThrowing() {
        assertEquals("Unknown tool: fly", call("fly", "{}").error())
        assertFalse(call("create_task", "{not json").ok())
        assertFalse(call("create_task", "[1,2]").ok())
    }
}
