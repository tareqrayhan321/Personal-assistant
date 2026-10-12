package com.personalmentor.app.data.cloud

import com.personalmentor.app.domain.cloud.CloudJob
import com.personalmentor.app.domain.cloud.CloudJobStatus
import kotlinx.serialization.Serializable

@Serializable
data class CloudPromptPayloadDto(val prompt: String)

@Serializable
data class CreateJobRequestDto(val type: String, val payload: CloudPromptPayloadDto)

/** `payload` is `{"prompt": ...}` or `{"text": ...}` depending on the job type; only the prompt is read. */
@Serializable
data class CloudJobDto(
    val id: String,
    val type: String,
    val status: String,
    val attempts: Int = 0,
    val max_attempts: Int = 3,
    val error_code: String? = null,
    val created_at: Long = 0,
    val updated_at: Long = 0,
    val payload: kotlinx.serialization.json.JsonObject? = null,
    val result: String? = null,
) {
    fun toDomain() = CloudJob(
        id = id,
        type = type,
        status = CloudJobStatus.fromWire(status),
        attempts = attempts,
        maxAttempts = max_attempts,
        errorCode = error_code,
        createdAt = created_at,
        updatedAt = updated_at,
        prompt = (payload?.get("prompt") as? kotlinx.serialization.json.JsonPrimitive)?.content,
        result = result,
    )
}

@Serializable
data class JobEnvelopeDto(val job: CloudJobDto, val duplicate: Boolean = false)

@Serializable
data class JobListDto(val jobs: List<CloudJobDto> = emptyList())

@Serializable
data class CloudErrorBodyDto(val error: CloudErrorDto? = null)

@Serializable
data class CloudErrorDto(val code: String? = null, val message: String? = null)
