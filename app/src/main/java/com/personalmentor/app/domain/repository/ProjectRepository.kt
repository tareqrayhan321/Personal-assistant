package com.personalmentor.app.domain.repository

import com.personalmentor.app.domain.model.Project
import kotlinx.coroutines.flow.Flow

interface ProjectRepository {
    fun observeAll(): Flow<List<Project>>
    suspend fun get(id: Long): Project?

    /** Inserts (id 0) or updates; returns the id. */
    suspend fun save(project: Project): Long

    /** Tasks that used the project are left without one. */
    suspend fun delete(id: Long)
}
