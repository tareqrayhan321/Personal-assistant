package com.personalmentor.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ScheduledTaskDao {
    @Query("SELECT * FROM scheduled_tasks ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<ScheduledTaskEntity>>

    @Query("SELECT * FROM scheduled_tasks WHERE id = :id")
    suspend fun getById(id: Long): ScheduledTaskEntity?

    @Query("SELECT * FROM scheduled_tasks WHERE enabled = 1")
    suspend fun getEnabled(): List<ScheduledTaskEntity>

    @Insert
    suspend fun insert(task: ScheduledTaskEntity): Long

    @Update
    suspend fun update(task: ScheduledTaskEntity)

    @Query("UPDATE scheduled_tasks SET enabled = :enabled, nextRunAt = :nextRunAt WHERE id = :id")
    suspend fun setEnabled(id: Long, enabled: Boolean, nextRunAt: Long?)

    @Query("UPDATE scheduled_tasks SET projectId = NULL WHERE projectId = :projectId")
    suspend fun clearProject(projectId: Long)

    @Query("DELETE FROM scheduled_tasks WHERE id = :id")
    suspend fun delete(id: Long)
}

@Dao
interface TaskRunDao {
    @Query("SELECT * FROM task_runs ORDER BY startedAt DESC, id DESC LIMIT 200")
    fun observeRecent(): Flow<List<TaskRunEntity>>

    @Query("SELECT * FROM task_runs WHERE id = :id")
    suspend fun getById(id: Long): TaskRunEntity?

    @Query("SELECT * FROM task_runs WHERE scheduledTaskId = :taskId AND status = 'SUCCESS' ORDER BY startedAt DESC, id DESC LIMIT :limit")
    suspend fun recentSuccessful(taskId: Long, limit: Int): List<TaskRunEntity>

    @Query("UPDATE task_runs SET status = 'FAILED', error = 'Interrupted', finishedAt = :now WHERE status = 'RUNNING' AND startedAt < :cutoff")
    suspend fun failStale(cutoff: Long, now: Long)

    @Insert
    suspend fun insert(run: TaskRunEntity): Long

    @Update
    suspend fun update(run: TaskRunEntity)

    @Query("DELETE FROM task_runs WHERE id = :id")
    suspend fun delete(id: Long)
}

@Dao
interface ProjectDao {
    @Query("SELECT * FROM projects ORDER BY name COLLATE NOCASE ASC")
    fun observeAll(): Flow<List<ProjectEntity>>

    @Query("SELECT * FROM projects WHERE id = :id")
    suspend fun getById(id: Long): ProjectEntity?

    @Insert
    suspend fun insert(project: ProjectEntity): Long

    @Update
    suspend fun update(project: ProjectEntity)

    @Query("DELETE FROM projects WHERE id = :id")
    suspend fun delete(id: Long)
}
