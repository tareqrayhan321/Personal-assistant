package com.personalmentor.app.domain.repository

import com.personalmentor.app.domain.model.RunStatus
import com.personalmentor.app.domain.model.ScheduledTask
import com.personalmentor.app.domain.model.TaskRun
import kotlinx.coroutines.flow.Flow

interface ScheduledTaskRepository {
    fun observeSchedules(): Flow<List<ScheduledTask>>
    fun observeRuns(): Flow<List<TaskRun>>
    suspend fun get(id: Long): ScheduledTask?

    /** Inserts (id 0) or updates; recomputes the next run. Returns the id. */
    suspend fun save(task: ScheduledTask): Long
    suspend fun setEnabled(id: Long, enabled: Boolean)
    suspend fun delete(id: Long)
    suspend fun deleteRun(id: Long)

    suspend fun startRun(task: ScheduledTask): Long
    suspend fun finishRun(runId: Long, status: RunStatus, output: String, error: String?)

    /** Newest first. */
    suspend fun recentSuccessfulRuns(taskId: Long, limit: Int): List<TaskRun>

    /** Moves the task to its next occurrence and arms that alarm (a finished series is switched off). */
    suspend fun advance(taskId: Long)

    /** Re-arms every enabled task, skipping missed occurrences. Call after a reboot and on app start. */
    suspend fun rearmAll()

    /** Runs left in "Running" by a killed process become "Failed". */
    suspend fun markStaleRunsFailed()
}
