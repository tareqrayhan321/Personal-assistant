package com.personalmentor.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ChatMessageDao {
    @Query("SELECT * FROM chat_messages WHERE mode = :mode ORDER BY timestamp ASC, id ASC")
    fun observeByMode(mode: String): Flow<List<ChatMessageEntity>>

    @Query("SELECT * FROM chat_messages WHERE mode = :mode ORDER BY timestamp DESC, id DESC LIMIT :limit")
    suspend fun getRecent(mode: String, limit: Int): List<ChatMessageEntity>

    @Insert
    suspend fun insert(message: ChatMessageEntity): Long

    @Query("UPDATE chat_messages SET text = :text WHERE id = :id")
    suspend fun updateText(id: Long, text: String)

    @Query("DELETE FROM chat_messages WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM chat_messages WHERE mode = :mode")
    suspend fun clear(mode: String)
}

@Dao
interface TaskDao {
    @Query("SELECT * FROM tasks ORDER BY isDone ASC, createdAt DESC")
    fun observeAll(): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE (:includeDone = 1 OR isDone = 0) ORDER BY createdAt DESC")
    suspend fun getAll(includeDone: Boolean): List<TaskEntity>

    @Query("SELECT * FROM tasks WHERE id = :id")
    suspend fun getById(id: Long): TaskEntity?

    @Query("SELECT * FROM tasks WHERE isDone = 0 AND remindAt IS NOT NULL AND remindAt > :now")
    suspend fun getPendingReminders(now: Long): List<TaskEntity>

    @Insert
    suspend fun insert(task: TaskEntity): Long

    @Query("UPDATE tasks SET isDone = :done WHERE id = :id")
    suspend fun setDone(id: Long, done: Boolean)

    @Query("SELECT * FROM tasks WHERE isDone = 0 AND remindAt IS NOT NULL AND repeatRule != 'NONE'")
    suspend fun getRepeatingActive(): List<TaskEntity>

    @Query("UPDATE tasks SET remindAt = :remindAt, repeatRule = :repeatRule WHERE id = :id")
    suspend fun setReminder(id: Long, remindAt: Long?, repeatRule: String)

    @Query("DELETE FROM tasks WHERE id = :id")
    suspend fun delete(id: Long)
}
