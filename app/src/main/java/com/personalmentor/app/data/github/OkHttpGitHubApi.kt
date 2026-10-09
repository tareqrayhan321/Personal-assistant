package com.personalmentor.app.data.github

import com.personalmentor.app.domain.github.GitHubApi
import com.personalmentor.app.domain.github.GitHubException
import com.personalmentor.app.domain.github.GitHubResponse
import com.personalmentor.app.domain.repository.AgentSettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * GitHub REST client with its own OkHttp client: the app-wide client rewrites every request to the LLM base URL
 * and adds the LLM key, so it must never be used here (it would send the GitHub token to the LLM host and
 * the LLM key to GitHub).
 */
@Singleton
class OkHttpGitHubApi @Inject constructor(
    private val settings: AgentSettingsRepository,
    private val json: Json,
) : GitHubApi {

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    override suspend fun call(
        method: String,
        segments: List<String>,
        query: Map<String, String>,
        body: JsonObject?,
    ): GitHubResponse = withContext(Dispatchers.IO) {
        val token = settings.current().githubToken
        if (token.isBlank()) {
            throw GitHubException("No GitHub token yet. Add one in the Agent screen (GitHub & Safety tab).")
        }
        if (segments.any { it.isEmpty() || it == "." || it == ".." }) throw GitHubException("Invalid request path.")

        val url = API_BASE.toHttpUrl().newBuilder().apply {
            segments.forEach { addPathSegment(it) }
            query.forEach { (key, value) -> addQueryParameter(key, value) }
        }.build()

        val jsonType = "application/json".toMediaType()
        val requestBody = when {
            body != null -> json.encodeToString(JsonObject.serializer(), body).toRequestBody(jsonType)
            method == "POST" || method == "PUT" || method == "PATCH" -> "{}".toRequestBody(jsonType)
            else -> null
        }
        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $token")
            .header("Accept", "application/vnd.github+json")
            .header("X-GitHub-Api-Version", API_VERSION)
            .header("User-Agent", "PersonalMentor-Agent")
            .method(method, requestBody)
            .build()

        try {
            client.newCall(request).execute().use { response ->
                val text = response.body?.string().orEmpty()
                val parsed = if (text.isBlank()) null else runCatching { json.parseToJsonElement(text) }.getOrNull()
                GitHubResponse(response.code, parsed)
            }
        } catch (e: IOException) {
            throw GitHubException("Could not reach GitHub: ${e.message ?: "network error"}")
        }
    }

    private companion object {
        const val API_BASE = "https://api.github.com/"
        const val API_VERSION = "2022-11-28"
    }
}
