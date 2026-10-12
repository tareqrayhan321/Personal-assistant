package com.personalmentor.app.data.cloud

import com.personalmentor.app.domain.cloud.CloudAgentApi
import com.personalmentor.app.domain.cloud.CloudAgentErrors
import com.personalmentor.app.domain.cloud.CloudAgentException
import com.personalmentor.app.domain.cloud.CloudErrorKind
import com.personalmentor.app.domain.cloud.CloudJob
import com.personalmentor.app.domain.cloud.CloudSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Own OkHttp client on purpose (same reason as the cloud-computer client): the app-wide client rewrites every request
 * to the LLM base URL and adds the LLM key. Redirects are off so the owner token can never be forwarded elsewhere.
 * No retries here: the caller decides, and creating a job is already safe to repeat thanks to the Idempotency-Key.
 */
@Singleton
class OkHttpCloudAgentApi @Inject constructor(
    private val json: Json,
) : CloudAgentApi {

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .followRedirects(false)
            .followSslRedirects(false)
            .build()
    }

    override suspend fun health(settings: CloudSettings) {
        call(settings, "GET", listOf("health"))
    }

    override suspend fun createJob(settings: CloudSettings, prompt: String, idempotencyKey: String): CloudJob {
        val body = json.encodeToString(CreateJobRequestDto.serializer(), CreateJobRequestDto("ai_prompt", CloudPromptPayloadDto(prompt)))
        val text = call(settings, "POST", listOf("v1", "jobs"), body, idempotencyKey)
        return parse { json.decodeFromString(JobEnvelopeDto.serializer(), text).job.toDomain() }
    }

    override suspend fun listJobs(settings: CloudSettings, limit: Int): List<CloudJob> {
        val text = call(settings, "GET", listOf("v1", "jobs"), query = mapOf("limit" to limit.coerceIn(1, 50).toString()))
        return parse { json.decodeFromString(JobListDto.serializer(), text).jobs.map { it.toDomain() } }
    }

    override suspend fun getJob(settings: CloudSettings, id: String): CloudJob {
        val text = call(settings, "GET", listOf("v1", "jobs", id))
        return parse { json.decodeFromString(JobEnvelopeDto.serializer(), text).job.toDomain() }
    }

    override suspend fun cancelJob(settings: CloudSettings, id: String): CloudJob {
        val text = call(settings, "POST", listOf("v1", "jobs", id, "cancel"), body = "{}")
        return parse { json.decodeFromString(JobEnvelopeDto.serializer(), text).job.toDomain() }
    }

    private inline fun <T> parse(block: () -> T): T =
        try {
            block()
        } catch (e: kotlinx.serialization.SerializationException) {
            throw CloudAgentException(CloudErrorKind.SERVER, "The cloud agent sent an answer this app does not understand.")
        }

    private suspend fun call(
        settings: CloudSettings,
        method: String,
        segments: List<String>,
        body: String? = null,
        idempotencyKey: String? = null,
        query: Map<String, String> = emptyMap(),
    ): String = withContext(Dispatchers.IO) {
        settings.blocker()?.let { throw it }
        val base = settings.normalizedUrl!!.toHttpUrlOrNull()
            ?: throw CloudAgentException(CloudErrorKind.NOT_CONFIGURED, "The cloud agent address is not valid.")
        val url = base.newBuilder().apply {
            segments.forEach { addPathSegment(it) }
            query.forEach { (k, v) -> addQueryParameter(k, v) }
        }.build()
        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer ${settings.ownerToken.trim()}")
            .header("User-Agent", "PersonalMentor-CloudAgent")
            .apply { idempotencyKey?.let { header("Idempotency-Key", it) } }
            .method(method, body?.toRequestBody(JSON))
            .build()
        try {
            client.newCall(request).execute().use { response ->
                val text = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    val err = runCatching { json.decodeFromString(CloudErrorBodyDto.serializer(), text).error }.getOrNull()
                    throw CloudAgentErrors.from(response.code, err?.code, err?.message)
                }
                text
            }
        } catch (e: IOException) {
            throw CloudAgentErrors.network(e.message)
        }
    }

    private companion object {
        val JSON = "application/json".toMediaType()
    }
}
