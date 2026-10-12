package com.personalmentor.app.domain.cloud

/** Client for the `cloud-agent/` Worker. Every call throws [CloudAgentException] with a readable message. */
interface CloudAgentApi {
    suspend fun health(settings: CloudSettings)
    suspend fun createJob(settings: CloudSettings, prompt: String, idempotencyKey: String): CloudJob
    suspend fun listJobs(settings: CloudSettings, limit: Int): List<CloudJob>
    suspend fun getJob(settings: CloudSettings, id: String): CloudJob
    suspend fun cancelJob(settings: CloudSettings, id: String): CloudJob
}
