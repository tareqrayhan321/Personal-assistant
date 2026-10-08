package com.personalmentor.app.domain.agent

import com.personalmentor.app.domain.model.Repeat
import com.personalmentor.app.domain.model.TodoTask
import com.personalmentor.app.domain.usecase.CanScheduleExactAlarmsUseCase
import com.personalmentor.app.domain.usecase.CreateTaskUseCase
import com.personalmentor.app.domain.usecase.DeleteTaskUseCase
import com.personalmentor.app.domain.usecase.GetTasksUseCase
import com.personalmentor.app.domain.usecase.SetReminderUseCase
import com.personalmentor.app.domain.usecase.SetTaskDoneUseCase
import com.personalmentor.app.domain.usecase.SnoozeReminderUseCase
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import java.time.Instant
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import javax.inject.Inject

private const val REPEAT_ERROR = "repeat must be one of: none, daily, weekly"

/** Tool names exposed to the LLM in Task Mode. */
object TaskTools {
    const val CREATE_TASK = "create_task"
    const val LIST_TASKS = "list_tasks"
    const val COMPLETE_TASK = "complete_task"
    const val DELETE_TASK = "delete_task"
    const val SET_REMINDER = "set_reminder"
    const val SNOOZE_TASK = "snooze_task"
}

/**
 * Executes tool calls requested by the LLM. Always returns a JSON string
 * (`{"ok":true,...}` or `{"ok":false,"error":"..."}`) that is fed back to the model.
 */
class TaskToolExecutor @Inject constructor(
    private val createTask: CreateTaskUseCase,
    private val getTasks: GetTasksUseCase,
    private val setDone: SetTaskDoneUseCase,
    private val deleteTask: DeleteTaskUseCase,
    private val setReminder: SetReminderUseCase,
    private val snoozeReminder: SnoozeReminderUseCase,
    private val canScheduleExactAlarms: CanScheduleExactAlarmsUseCase,
    private val json: Json,
) {
    suspend fun execute(name: String, argumentsJson: String): String {
        return try {
            val args = if (argumentsJson.isBlank()) JsonObject(emptyMap())
            else json.parseToJsonElement(argumentsJson).jsonObject
            when (name) {
                TaskTools.CREATE_TASK -> create(args)
                TaskTools.LIST_TASKS -> list(args)
                TaskTools.COMPLETE_TASK -> complete(args)
                TaskTools.DELETE_TASK -> delete(args)
                TaskTools.SET_REMINDER -> remind(args)
                TaskTools.SNOOZE_TASK -> snooze(args)
                else -> fail("Unknown tool: $name")
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            fail(e.message ?: "Tool call failed")
        }
    }

    private suspend fun create(args: JsonObject): String {
        val title = args.string("title")?.trim().orEmpty()
        if (title.isEmpty()) return fail("title is required")
        val remindAt = parseMillis(args.string("remind_at"))
        if (remindAt != null && remindAt <= System.currentTimeMillis()) return fail("remind_at is in the past")
        val repeat = parseRepeat(args) ?: return fail(REPEAT_ERROR)
        if (repeat != Repeat.NONE && remindAt == null) return fail("repeat requires remind_at (the first occurrence)")
        val task = createTask(
            TodoTask(
                title = title,
                notes = args.string("notes"),
                dueAt = parseMillis(args.string("due_at")),
                remindAt = remindAt,
                repeat = repeat,
            )
        )
        return ok {
            put("task", task.toJson())
            if (task.remindAt != null) putExactAlarmWarning()
        }
    }

    private suspend fun list(args: JsonObject): String {
        val tasks = getTasks(includeDone = args.bool("include_completed") ?: false)
        return ok { put("tasks", buildJsonArray { tasks.forEach { add(it.toJson()) } }) }
    }

    private suspend fun complete(args: JsonObject): String {
        val id = args.long("task_id") ?: return fail("task_id is required")
        return if (setDone(id, true)) ok { put("task_id", id) } else fail("No task with id $id")
    }

    private suspend fun delete(args: JsonObject): String {
        val id = args.long("task_id") ?: return fail("task_id is required")
        return if (deleteTask(id)) ok { put("task_id", id) } else fail("No task with id $id")
    }

    private suspend fun remind(args: JsonObject): String {
        val id = args.long("task_id") ?: return fail("task_id is required")
        val remindAt = parseMillis(args.string("remind_at")) ?: return fail("remind_at is required (yyyy-MM-ddTHH:mm)")
        if (remindAt <= System.currentTimeMillis()) return fail("remind_at is in the past")
        val repeat = parseRepeat(args) ?: return fail(REPEAT_ERROR)
        return if (setReminder(id, remindAt, repeat)) {
            ok {
                put("task_id", id)
                put("remind_at", format(remindAt))
                if (repeat != Repeat.NONE) put("repeat", repeat.name.lowercase())
                putExactAlarmWarning()
            }
        } else {
            fail("No task with id $id")
        }
    }

    private suspend fun snooze(args: JsonObject): String {
        val id = args.long("task_id") ?: return fail("task_id is required")
        val minutes = args.long("minutes") ?: return fail("minutes is required")
        if (minutes !in 1L..10_080L) return fail("minutes must be between 1 and 10080")
        return if (snoozeReminder(id, minutes.toInt())) {
            ok {
                put("task_id", id)
                put("minutes", minutes)
                putExactAlarmWarning()
            }
        } else {
            fail("No active (not completed) task with id $id")
        }
    }

    // ---- helpers ----

    /** Missing/blank means [Repeat.NONE]; null means an unknown value was given. */
    private fun parseRepeat(args: JsonObject): Repeat? {
        val raw = args.string("repeat")?.trim()
        return if (raw.isNullOrEmpty()) Repeat.NONE else Repeat.parseOrNull(raw)
    }

    private fun JsonObjectBuilder.putExactAlarmWarning() {
        if (!canScheduleExactAlarms()) {
            put(
                "warning",
                "Exact alarms are not allowed, so the reminder may arrive a few minutes late. " +
                    "Tell the user to tap Allow on the banner in the Tasks screen to fix this.",
            )
        }
    }

    private fun JsonObject.string(key: String): String? = this[key]?.jsonPrimitive?.contentOrNull
    private fun JsonObject.long(key: String): Long? = this[key]?.jsonPrimitive?.longOrNull
    private fun JsonObject.bool(key: String): Boolean? = this[key]?.jsonPrimitive?.booleanOrNull

    private fun ok(block: JsonObjectBuilder.() -> Unit): String =
        buildJsonObject { put("ok", true); block() }.toString()

    private fun fail(message: String): String =
        buildJsonObject { put("ok", false); put("error", message) }.toString()

    private fun TodoTask.toJson(): JsonObject = buildJsonObject {
        put("id", id)
        put("title", title)
        notes?.let { put("notes", it) }
        dueAt?.let { put("due_at", format(it)) }
        remindAt?.let { put("remind_at", format(it)) }
        if (repeat != Repeat.NONE) put("repeat", repeat.name.lowercase())
        put("done", isDone)
    }

    /** Accepts local "yyyy-MM-ddTHH:mm[:ss]" (preferred) or an ISO string with an offset. */
    private fun parseMillis(raw: String?): Long? {
        val value = raw?.trim().orEmpty()
        if (value.isEmpty()) return null
        return runCatching {
            LocalDateTime.parse(value).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        }.getOrElse {
            OffsetDateTime.parse(value).toInstant().toEpochMilli()
        }
    }

    private fun format(millis: Long): String =
        Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDateTime().withNano(0).toString()
}
