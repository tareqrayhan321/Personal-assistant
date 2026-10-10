package com.personalmentor.app.domain.repository

import com.personalmentor.app.domain.model.CloudComputer
import kotlinx.coroutines.flow.Flow

interface CloudComputerRepository {
    fun observeAll(): Flow<List<CloudComputer>>
    suspend fun get(id: Long): CloudComputer?

    /** Inserts (id 0) or updates; returns the id. The URL is normalized. */
    suspend fun save(computer: CloudComputer): Long

    /** Tasks that used it are left without a cloud computer. */
    suspend fun delete(id: Long)
}
