package com.personalmentor.app.data.repository

import com.personalmentor.app.data.local.CloudComputerDao
import com.personalmentor.app.data.local.ScheduledTaskDao
import com.personalmentor.app.data.local.toDomain
import com.personalmentor.app.data.local.toEntity
import com.personalmentor.app.domain.computer.CloudComputerUrl
import com.personalmentor.app.domain.model.CloudComputer
import com.personalmentor.app.domain.repository.CloudComputerRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class CloudComputerRepositoryImpl @Inject constructor(
    private val computers: CloudComputerDao,
    private val tasks: ScheduledTaskDao,
) : CloudComputerRepository {

    override fun observeAll(): Flow<List<CloudComputer>> = computers.observeAll().map { list -> list.map { it.toDomain() } }

    override suspend fun get(id: Long): CloudComputer? = computers.getById(id)?.toDomain()

    override suspend fun save(computer: CloudComputer): Long {
        val url = CloudComputerUrl.normalize(computer.url) ?: throw IllegalArgumentException("Use an https:// address.")
        val entity = computer.copy(name = computer.name.trim(), url = url, token = computer.token.trim()).toEntity()
        return if (computer.id == 0L) {
            computers.insert(entity)
        } else {
            computers.update(entity)
            computer.id
        }
    }

    override suspend fun delete(id: Long) {
        tasks.clearCloudComputer(id)
        computers.delete(id)
    }
}
