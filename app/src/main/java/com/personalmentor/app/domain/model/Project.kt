package com.personalmentor.app.domain.model

/** A named set of standing instructions that scheduled tasks can share. */
data class Project(
    val id: Long = 0,
    val name: String,
    val instructions: String,
    val createdAt: Long = System.currentTimeMillis(),
)
