package com.personalmentor.app.data.local

import com.personalmentor.app.domain.model.AssistantMode
import com.personalmentor.app.domain.model.ChatMessage
import com.personalmentor.app.domain.model.Sender
import com.personalmentor.app.domain.model.Repeat
import com.personalmentor.app.domain.model.TodoTask

fun ChatMessageEntity.toDomain() = ChatMessage(
    id = id,
    text = text,
    sender = Sender.valueOf(sender),
    mode = AssistantMode.valueOf(mode),
    timestamp = timestamp,
)

fun ChatMessage.toEntity() = ChatMessageEntity(
    id = id,
    text = text,
    sender = sender.name,
    mode = mode.name,
    timestamp = timestamp,
)

fun TaskEntity.toDomain() = TodoTask(
    id = id, title = title, notes = notes, dueAt = dueAt,
    remindAt = remindAt, repeat = Repeat.fromName(repeatRule), isDone = isDone, createdAt = createdAt,
)

fun TodoTask.toEntity() = TaskEntity(
    id = id, title = title, notes = notes, dueAt = dueAt,
    remindAt = remindAt, repeatRule = repeat.name, isDone = isDone, createdAt = createdAt,
)
