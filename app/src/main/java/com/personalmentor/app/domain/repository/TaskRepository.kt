package com.personalmentor.app.domain.repository

import com.personalmentor.app.domain.model.Repeat
import com.personalmentor.app.domain.model.TodoTask
import kotlinx.coroutines.flow.Flow

interface TaskRepository {
    fun observeTasks(): Flow<List<TodoTask>>
    suspend fun getTasks(includeDone: Boolean): List<TodoTask>
    suspend fun getTask(id: Long): TodoTask?
    suspend fun getPendingReminders(now: Long): List<TodoTask>
    suspend fun getRepeatingActive(): List<TodoTask>
    suspend fun addTask(task: TodoTask): Long
    suspend fun setDone(id: Long, done: Boolean)
    suspend fun setReminder(id: Long, remindAt: Long?, repeat: Repeat = Repeat.NONE)
    suspend fun deleteTask(id: Long)
}
