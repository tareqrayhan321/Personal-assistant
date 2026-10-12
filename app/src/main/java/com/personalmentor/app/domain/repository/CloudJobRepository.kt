package com.personalmentor.app.domain.repository

import com.personalmentor.app.domain.cloud.CloudAgentException
import com.personalmentor.app.domain.cloud.CloudJob

/**
 * Cloud jobs live on the Worker (D1), not in Room. Every call checks the settings first and throws
 * [CloudAgentException] (never makes a request) when the cloud agent is off or not configured.
 */
interface CloudJobRepository {
    /** Pings /health. Throws [CloudAgentException]. */
    suspend fun checkConnection()
    suspend fun submit(prompt: String): CloudJob
    suspend fun recent(limit: Int = 20): List<CloudJob>
    suspend fun get(id: String): CloudJob
    suspend fun cancel(id: String): CloudJob
}
