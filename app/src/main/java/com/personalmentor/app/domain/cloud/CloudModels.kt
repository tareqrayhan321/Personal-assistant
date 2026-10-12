package com.personalmentor.app.domain.cloud

import com.personalmentor.app.domain.computer.CloudComputerUrl

/** Mirrors the `status` values of the cloud-agent Worker. */
enum class CloudJobStatus(val wire: String) {
    PENDING("pending"), RUNNING("running"), SUCCEEDED("succeeded"), FAILED("failed"), CANCELLED("cancelled");

    val isFinished: Boolean get() = this == SUCCEEDED || this == FAILED || this == CANCELLED
    val isCancellable: Boolean get() = this == PENDING || this == RUNNING

    companion object {
        /** An unknown value (from a newer server) is treated as FAILED, so nothing unrecognised looks runnable. */
        fun fromWire(value: String?): CloudJobStatus = entries.firstOrNull { it.wire == value } ?: FAILED
    }
}

data class CloudJob(
    val id: String,
    val type: String,
    val status: CloudJobStatus,
    val attempts: Int,
    val maxAttempts: Int,
    val errorCode: String?,
    val createdAt: Long,
    val updatedAt: Long,
    /** Only present when a single job is fetched or just created. */
    val prompt: String? = null,
    val result: String? = null,
) {
    val shortId: String get() = id.take(8)
}

/**
 * User-controlled cloud agent configuration. Off by default: with [enabled] false the app never makes a cloud request,
 * so the local-only experience is exactly what it was before.
 */
data class CloudSettings(
    val enabled: Boolean = false,
    val baseUrl: String = "",
    val ownerToken: String = "",
) {
    val normalizedUrl: String? get() = CloudComputerUrl.normalize(baseUrl)
    val tokenValid: Boolean get() = ownerToken.trim().length >= MIN_TOKEN_LENGTH
    val configured: Boolean get() = normalizedUrl != null && tokenValid

    /** Why a request must not be sent right now, or null when it may be. */
    fun blocker(): CloudAgentException? = when {
        !enabled -> CloudAgentException(CloudErrorKind.DISABLED, "The cloud agent is turned off in Settings.")
        normalizedUrl == null -> CloudAgentException(CloudErrorKind.NOT_CONFIGURED, "Enter a valid https:// address for the cloud agent.")
        !tokenValid -> CloudAgentException(CloudErrorKind.NOT_CONFIGURED, "The owner token must be at least $MIN_TOKEN_LENGTH characters.")
        else -> null
    }

    companion object {
        const val MIN_TOKEN_LENGTH = 32
    }
}

enum class CloudErrorKind { DISABLED, NOT_CONFIGURED, UNAUTHORIZED, RATE_LIMITED, QUOTA, SERVER_OFF, NOT_FOUND, CONFLICT, INVALID, NETWORK, SERVER }

class CloudAgentException(val kind: CloudErrorKind, message: String) : Exception(message)

/** What the Settings/Cloud screen shows about the connection. */
sealed interface CloudConnection {
    data object Off : CloudConnection
    data object NotConfigured : CloudConnection
    data object Unchecked : CloudConnection
    data object Checking : CloudConnection
    data object Connected : CloudConnection
    data class Failed(val kind: CloudErrorKind, val message: String) : CloudConnection
}

/** Turns what the Worker answered into a readable error. Pure, so it is unit tested. */
object CloudAgentErrors {
    fun from(httpCode: Int, serverCode: String?, serverMessage: String?): CloudAgentException {
        val detail = serverMessage?.takeIf { it.isNotBlank() }?.take(200)
        return when {
            httpCode == 401 -> CloudAgentException(CloudErrorKind.UNAUTHORIZED, "The cloud agent rejected the owner token. Check it in Cloud agent settings.")
            httpCode == 429 -> CloudAgentException(CloudErrorKind.RATE_LIMITED, "Too many requests. Try again in a minute.")
            httpCode == 503 && serverCode == "quota_exceeded" ->
                CloudAgentException(CloudErrorKind.QUOTA, "The free daily quota is used up. It resets at 00:00 UTC. Nothing was charged.")
            httpCode == 503 && serverCode == "cloud_disabled" ->
                CloudAgentException(CloudErrorKind.SERVER_OFF, "The cloud agent is switched off on the server.")
            httpCode == 404 -> CloudAgentException(CloudErrorKind.NOT_FOUND, detail ?: "Not found.")
            httpCode == 409 -> CloudAgentException(CloudErrorKind.CONFLICT, detail ?: "That request conflicts with an existing job.")
            httpCode == 400 || httpCode == 413 -> CloudAgentException(CloudErrorKind.INVALID, detail ?: "The cloud agent rejected the request.")
            else -> CloudAgentException(CloudErrorKind.SERVER, "The cloud agent had a problem (HTTP $httpCode). Try again later.")
        }
    }

    fun network(reason: String?) =
        CloudAgentException(CloudErrorKind.NETWORK, "Could not reach the cloud agent: ${reason ?: "network error"}. Local features still work.")
}

/**
 * Keeps one Idempotency-Key per prompt until that prompt is accepted, so a retry after a timeout reuses the key and
 * the server returns the same job instead of creating a second one.
 */
class SubmissionKeys(private val newKey: () -> String) {
    private var lastPrompt: String? = null
    private var lastKey: String? = null

    @Synchronized
    fun keyFor(prompt: String): String {
        if (prompt != lastPrompt || lastKey == null) {
            lastPrompt = prompt
            lastKey = newKey()
        }
        return lastKey!!
    }

    @Synchronized
    fun accepted() {
        lastPrompt = null
        lastKey = null
    }
}
