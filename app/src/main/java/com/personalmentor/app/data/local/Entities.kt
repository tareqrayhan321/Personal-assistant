package com.personalmentor.app.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "chat_messages", indices = [Index("mode")])
data class ChatMessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val text: String,
    val sender: String,
    val mode: String,
    val timestamp: Long,
)

@Entity(tableName = "tasks")
data class TaskEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val notes: String?,
    val dueAt: Long?,
    val remindAt: Long?,
    val repeatRule: String,
    val isDone: Boolean,
    val createdAt: Long,
)
