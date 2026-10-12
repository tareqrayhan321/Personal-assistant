package com.personalmentor.app.domain.cloud

import com.personalmentor.app.domain.repository.CloudJobRepository

/**
 * The rules around cloud calls, free of Android and DI so they can be unit tested:
 * nothing is sent unless the cloud agent is on and configured, prompts are bounded, and a retried prompt reuses its key.
 */
class CloudJobService(
    private val api: CloudAgentApi,
    private val settings: () -> CloudSettings,
    private val keys: SubmissionKeys,
) : CloudJobRepository {

    private fun usable(): CloudSettings = settings().also { s -> s.blocker()?.let { throw it } }

    override suspend fun checkConnection() = api.health(usable())

    override suspend fun submit(prompt: String): CloudJob {
        val text = prompt.trim()
        if (text.isEmpty() || text.length > MAX_PROMPT) {
            throw CloudAgentException(CloudErrorKind.INVALID, "Write between 1 and $MAX_PROMPT characters.")
        }
        val job = api.createJob(usable(), text, keys.keyFor(text))
        keys.accepted()
        return job
    }

    override suspend fun recent(limit: Int) = api.listJobs(usable(), limit)
    override suspend fun get(id: String) = api.getJob(usable(), id)
    override suspend fun cancel(id: String) = api.cancelJob(usable(), id)

    companion object {
        const val MAX_PROMPT = 2000
    }
}
