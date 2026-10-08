package com.personalmentor.app.data.repository

import com.personalmentor.app.data.local.TaskDao
import com.personalmentor.app.data.local.toDomain
import com.personalmentor.app.data.local.toEntity
import com.personalmentor.app.domain.model.Repeat
import com.personalmentor.app.domain.model.TodoTask
import com.personalmentor.app.domain.repository.TaskRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class TaskRepositoryImpl @Inject constructor(
    private val dao: TaskDao,
) : TaskRepository {

    override fun observeTasks(): Flow<List<TodoTask>> =
        dao.observeAll().map { list -> list.map { it.toDomain() } }

    override suspend fun getTasks(includeDone: Boolean): List<TodoTask> =
        dao.getAll(includeDone).map { it.toDomain() }

    override suspend fun getTask(id: Long): TodoTask? = dao.getById(id)?.toDomain()

    override suspend fun getPendingReminders(now: Long): List<TodoTask> =
        dao.getPendingReminders(now).map { it.toDomain() }

    override suspend fun getRepeatingActive(): List<TodoTask> = dao.getRepeatingActive().map { it.toDomain() }

    override suspend fun addTask(task: TodoTask): Long = dao.insert(task.toEntity())

    override suspend fun setDone(id: Long, done: Boolean) = dao.setDone(id, done)

    override suspend fun setReminder(id: Long, remindAt: Long?, repeat: Repeat) =
        dao.setReminder(id, remindAt, repeat.name)

    override suspend fun deleteTask(id: Long) = dao.delete(id)
}
