package com.personalmentor.app.data.repository

import com.personalmentor.app.data.local.ScheduledTaskDao
import com.personalmentor.app.data.local.TaskRunDao
import com.personalmentor.app.data.local.toDomain
import com.personalmentor.app.data.local.toEntity
import com.personalmentor.app.domain.model.RunStatus
import com.personalmentor.app.domain.model.ScheduledTask
import com.personalmentor.app.domain.model.TaskRun
import com.personalmentor.app.domain.repository.ScheduledTaskRepository
import com.personalmentor.app.domain.schedule.ScheduleCalculator
import com.personalmentor.app.domain.schedule.ScheduleTrigger
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class ScheduledTaskRepositoryImpl @Inject constructor(
    private val tasks: ScheduledTaskDao,
    private val runs: TaskRunDao,
    private val trigger: ScheduleTrigger,
) : ScheduledTaskRepository {

    override fun observeSchedules(): Flow<List<ScheduledTask>> =
        tasks.observeAll().map { list -> list.map { it.toDomain() } }

    override fun observeRuns(): Flow<List<TaskRun>> =
        runs.observeRecent().map { list -> list.map { it.toDomain() } }

    override suspend fun get(id: Long): ScheduledTask? = tasks.getById(id)?.toDomain()

    override suspend fun save(task: ScheduledTask): Long {
        val prompt = task.prompt.trim()
        val title = task.title.trim().ifBlank { prompt.lineSequence().first().take(TITLE_FALLBACK_CHARS) }
        val next = if (task.enabled) ScheduleCalculator.nextRun(task, System.currentTimeMillis()) else null
        val entity = task.copy(title = title, prompt = prompt, nextRunAt = next).toEntity()
        val id = if (task.id == 0L) {
            tasks.insert(entity)
        } else {
            tasks.update(entity)
            task.id
        }
        if (task.enabled && next != null) trigger.arm(id, next) else trigger.cancel(id)
        return id
    }

    override suspend fun setEnabled(id: Long, enabled: Boolean) {
        val task = tasks.getById(id)?.toDomain() ?: return
        val next = if (enabled) ScheduleCalculator.nextRun(task, System.currentTimeMillis()) else null
        tasks.setEnabled(id, enabled, next)
        if (enabled && next != null) trigger.arm(id, next) else trigger.cancel(id)
    }

    override suspend fun delete(id: Long) {
        trigger.cancel(id)
        tasks.delete(id)
    }

    override suspend fun deleteRun(id: Long) = runs.delete(id)

    override suspend fun startRun(task: ScheduledTask): Long =
        runs.insert(TaskRun(scheduledTaskId = task.id, taskTitle = task.title, startedAt = System.currentTimeMillis()).toEntity())

    override suspend fun finishRun(runId: Long, status: RunStatus, output: String, error: String?) {
        val run = runs.getById(runId) ?: return
        runs.update(run.copy(status = status.name, output = output, error = error, finishedAt = System.currentTimeMillis()))
    }

    override suspend fun recentSuccessfulRuns(taskId: Long, limit: Int): List<TaskRun> =
        runs.recentSuccessful(taskId, limit).map { it.toDomain() }

    override suspend fun advance(taskId: Long) {
        val task = tasks.getById(taskId)?.toDomain() ?: return
        val now = System.currentTimeMillis()
        // Never step back to the occurrence that just fired, even if the alarm arrived a moment early.
        val next = ScheduleCalculator.nextRun(task, maxOf(now, task.nextRunAt ?: now))
        tasks.setEnabled(taskId, next != null, next)
        if (next != null) trigger.arm(taskId, next) else trigger.cancel(taskId)
    }

    override suspend fun rearmAll() {
        val now = System.currentTimeMillis()
        tasks.getEnabled().map { it.toDomain() }.forEach { task ->
            val next = task.nextRunAt?.takeIf { it > now } ?: ScheduleCalculator.nextRun(task, now)
            if (next != task.nextRunAt) tasks.setEnabled(task.id, next != null, next)
            if (next != null) trigger.arm(task.id, next) else trigger.cancel(task.id)
        }
    }

    override suspend fun markStaleRunsFailed() {
        val now = System.currentTimeMillis()
        runs.failStale(now - STALE_RUN_MS, now)
    }

    private companion object {
        const val TITLE_FALLBACK_CHARS = 40
        const val STALE_RUN_MS = 15 * 60 * 1000L
    }
}
