package com.personalmentor.app.domain.model

import java.time.LocalDate

enum class ScheduleRepeat(val label: String) {
    ONCE("Once"), DAILY("Daily"), WEEKDAYS("Weekdays"), WEEKLY("Weekly"), MONTHLY("Monthly");

    companion object {
        fun fromName(name: String?): ScheduleRepeat = entries.firstOrNull { it.name == name } ?: DAILY
    }
}

/** "Same task" keeps one conversation across runs; "New task" starts fresh every run. */
enum class RunMode(val label: String) {
    SAME_TASK("Same task"), NEW_TASK("New task");

    companion object {
        fun fromName(name: String?): RunMode = entries.firstOrNull { it.name == name } ?: SAME_TASK
    }
}

/** Tool groups a scheduled run may use (task/reminder tools are always available). */
enum class Connector(val label: String) {
    GITHUB("GitHub"), BROWSER("Browser");

    companion object {
        fun parseSet(raw: String): Set<Connector> =
            raw.split(',').mapNotNull { part -> entries.firstOrNull { it.name == part.trim() } }.toSet()

        fun encode(set: Set<Connector>): String = entries.filter { it in set }.joinToString(",") { it.name }
    }
}

enum class RunStatus(val label: String) {
    RUNNING("Running"), SUCCESS("Done"), FAILED("Failed"), SKIPPED("Skipped");

    companion object {
        fun fromName(name: String?): RunStatus = entries.firstOrNull { it.name == name } ?: FAILED
    }
}

data class ScheduledTask(
    val id: Long = 0,
    val title: String,
    val prompt: String,
    val repeat: ScheduleRepeat = ScheduleRepeat.DAILY,
    val hour: Int = 8,
    val minute: Int = 0,
    /** Anchor date: the date of a one-off run, the weekday of a weekly run, the day of month of a monthly run. */
    val startEpochDay: Long = LocalDate.now().toEpochDay(),
    /** Last day a run may happen; null = never ends. */
    val endEpochDay: Long? = null,
    val skipConfirmations: Boolean = false,
    val runMode: RunMode = RunMode.SAME_TASK,
    val connectors: Set<Connector> = Connector.entries.toSet(),
    /** Model for this task; null = the model from Settings. */
    val agentModel: String? = null,
    val projectId: Long? = null,
    val cloudComputerId: Long? = null,
    val enabled: Boolean = true,
    val nextRunAt: Long? = null,
    val createdAt: Long = System.currentTimeMillis(),
)

data class TaskRun(
    val id: Long = 0,
    val scheduledTaskId: Long,
    val taskTitle: String,
    val startedAt: Long,
    val finishedAt: Long? = null,
    val status: RunStatus = RunStatus.RUNNING,
    val output: String = "",
    val error: String? = null,
)
