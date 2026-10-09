package com.personalmentor.app.domain.github

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

class GitHubException(message: String) : Exception(message)

data class GitHubResponse(val code: Int, val body: JsonElement?) {
    val ok: Boolean get() = code in 200..299
}

/** Thin GitHub REST client. [segments] are URL path segments (each one gets encoded), never a raw path. */
interface GitHubApi {
    suspend fun call(
        method: String,
        segments: List<String>,
        query: Map<String, String> = emptyMap(),
        body: JsonObject? = null,
    ): GitHubResponse
}
