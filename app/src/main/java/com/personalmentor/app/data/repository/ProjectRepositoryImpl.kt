package com.personalmentor.app.data.repository

import com.personalmentor.app.data.local.ProjectDao
import com.personalmentor.app.data.local.ScheduledTaskDao
import com.personalmentor.app.data.local.toDomain
import com.personalmentor.app.data.local.toEntity
import com.personalmentor.app.domain.model.Project
import com.personalmentor.app.domain.repository.ProjectRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class ProjectRepositoryImpl @Inject constructor(
    private val projects: ProjectDao,
    private val tasks: ScheduledTaskDao,
) : ProjectRepository {

    override fun observeAll(): Flow<List<Project>> = projects.observeAll().map { list -> list.map { it.toDomain() } }

    override suspend fun get(id: Long): Project? = projects.getById(id)?.toDomain()

    override suspend fun save(project: Project): Long {
        val entity = project.copy(name = project.name.trim(), instructions = project.instructions.trim()).toEntity()
        return if (project.id == 0L) {
            projects.insert(entity)
        } else {
            projects.update(entity)
            project.id
        }
    }

    override suspend fun delete(id: Long) {
        tasks.clearProject(id)
        projects.delete(id)
    }
}
