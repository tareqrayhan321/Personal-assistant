package com.personalmentor.app.data.remote

import com.personalmentor.app.domain.agent.TaskTools
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** JSON-schema tool definitions sent to the LLM in Task Mode. */
object TaskToolSpecs {

    private const val REPEAT_HELP =
        "Optional recurrence: none (default), daily or weekly. remind_at is the first occurrence; later ones keep the same time of day."

    private const val TIME_FORMAT = "Local time in the format yyyy-MM-ddTHH:mm (e.g. 2026-10-08T18:00)"

    val all: List<ToolSpec> = listOf(
        spec(
            TaskTools.CREATE_TASK,
            "Create a to-do task, optionally with a due time and a reminder.",
            required = listOf("title"),
            "title" to string("Short task title"),
            "notes" to string("Optional extra details"),
            "due_at" to string("Optional deadline. $TIME_FORMAT"),
            "remind_at" to string("Optional time to notify the user. $TIME_FORMAT"),
            "repeat" to string(REPEAT_HELP),
        ),
        spec(
            TaskTools.LIST_TASKS,
            "List the user's tasks with their ids. Use this to find a task id before completing, deleting or changing it.",
            required = emptyList(),
            "include_completed" to type("boolean", "Include completed tasks (default false)"),
        ),
        spec(
            TaskTools.COMPLETE_TASK,
            "Mark a task as completed.",
            required = listOf("task_id"),
            "task_id" to type("integer", "Id of the task"),
        ),
        spec(
            TaskTools.DELETE_TASK,
            "Permanently delete a task and its reminder.",
            required = listOf("task_id"),
            "task_id" to type("integer", "Id of the task"),
        ),
        spec(
            TaskTools.SET_REMINDER,
            "Set or change the reminder time of an existing task.",
            required = listOf("task_id", "remind_at"),
            "task_id" to type("integer", "Id of the task"),
            "remind_at" to string(TIME_FORMAT),
            "repeat" to string(REPEAT_HELP),
        ),
        spec(
            TaskTools.SNOOZE_TASK,
            "Postpone a task's reminder by a number of minutes from now (e.g. 'snooze 15 minutes').",
            required = listOf("task_id", "minutes"),
            "task_id" to type("integer", "Id of the task"),
            "minutes" to type("integer", "Minutes from now, 1 to 10080"),
        ),
    )

    private fun string(description: String) = type("string", description)

    private fun type(type: String, description: String): JsonObject = buildJsonObject {
        put("type", type)
        put("description", description)
    }

    private fun spec(
        name: String,
        description: String,
        required: List<String>,
        vararg properties: Pair<String, JsonObject>,
    ) = ToolSpec(
        function = FunctionSpec(
            name = name,
            description = description,
            parameters = buildJsonObject {
                put("type", "object")
                put("properties", buildJsonObject { properties.forEach { (key, value) -> put(key, value) } })
                put("required", JsonArray(required.map(::JsonPrimitive)))
            },
        ),
    )
}
