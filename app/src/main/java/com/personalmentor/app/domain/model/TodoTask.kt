package com.personalmentor.app.domain.model

data class TodoTask(
    val id: Long = 0,
    val title: String,
    val notes: String? = null,
    val dueAt: Long? = null,
    val remindAt: Long? = null,
    /** How [remindAt] recurs; for repeating tasks it always holds the next occurrence. */
    val repeat: Repeat = Repeat.NONE,
    val isDone: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
)
