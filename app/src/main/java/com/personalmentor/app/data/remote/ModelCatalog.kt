package com.personalmentor.app.data.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/** Which known provider a base URL points at (anything else is a custom / local endpoint). */
enum class ProviderKind {
    OPENAI, OPENROUTER, GEMINI, ANTHROPIC, OTHER;

    companion object {
        fun of(baseUrl: String): ProviderKind {
            val host = baseUrl.trim().toHttpUrlOrNull()?.host ?: return OTHER
            return when {
                host == "api.openai.com" -> OPENAI
                host == "openrouter.ai" -> OPENROUTER
                host == "generativelanguage.googleapis.com" -> GEMINI
                host == "api.anthropic.com" -> ANTHROPIC
                else -> OTHER
            }
        }
    }
}

/** The models the user can pick for a provider, plus a short note to show under the picker. */
data class ModelList(val models: List<String>, val note: String? = null)

/**
 * Loads the model list straight from the provider, so the user never has to type a model name.
 * OpenRouter: only models whose prompt and completion price are both 0 (the current free ones).
 * Gemini: the Flash / Flash-Lite chat models (the ones with a free tier).
 * OpenAI and Anthropic have no free API models, so those show every chat model the key can use.
 * Uses its own client because the app's main client rewrites every request to the saved base URL.
 */
@Singleton
class ModelCatalog @Inject constructor(private val json: Json) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    /** Works offline / without a key: a short built-in list so the picker is never empty (not for OpenRouter). */
    fun fallback(kind: ProviderKind): List<String> = when (kind) {
        ProviderKind.GEMINI -> listOf("gemini-2.5-flash", "gemini-2.5-flash-lite")
        ProviderKind.OPENAI -> listOf("gpt-4o-mini", "gpt-4o", "gpt-4.1-mini", "gpt-4.1")
        ProviderKind.ANTHROPIC -> listOf("claude-haiku-5-5", "claude-sonnet-5-5", "claude-opus-5-5")
        else -> emptyList()
    }

    suspend fun load(kind: ProviderKind, apiKey: String): Result<ModelList> = withContext(Dispatchers.IO) {
        runCatching {
            val key = apiKey.trim()
            when (kind) {
                ProviderKind.OPENROUTER -> ModelList(openRouterFree(), "Free models on OpenRouter right now. Tap refresh to update.")
                ProviderKind.GEMINI -> {
                    requireKey(key)
                    ModelList(geminiFree(key), "Gemini models with a free tier (rate limited).")
                }
                ProviderKind.OPENAI -> {
                    requireKey(key)
                    ModelList(openAiChat(key), "OpenAI has no free API models. These are the chat models your key can use.")
                }
                ProviderKind.ANTHROPIC -> {
                    requireKey(key)
                    ModelList(anthropicChat(key), "Anthropic has no free API models. These are the Claude models your key can use.")
                }
                ProviderKind.OTHER -> ModelList(emptyList())
            }
        }
    }

    private fun requireKey(key: String) {
        if (key.isBlank()) throw IOException("Add your API key and tap refresh to load the full list.")
    }

    // ---- providers ----

    private fun openRouterFree(): List<String> {
        val data = getJson("https://openrouter.ai/api/v1/models", emptyMap())["data"]?.jsonArray ?: return emptyList()
        val free = data.mapNotNull { el ->
            val m = el.jsonObject
            val id = m["id"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            val pricing = m["pricing"] as? JsonObject
            val prompt = pricing?.get("prompt")?.jsonPrimitive?.contentOrNull?.toDoubleOrNull()
            val completion = pricing?.get("completion")?.jsonPrimitive?.contentOrNull?.toDoubleOrNull()
            val isFree = (prompt == 0.0 && completion == 0.0) || id.endsWith(":free")
            val outputs = (m["architecture"] as? JsonObject)?.get("output_modalities")?.jsonArray
                ?.map { it.jsonPrimitive.content }
            val textOut = outputs == null || "text" in outputs
            val tools = m["supported_parameters"]?.jsonArray?.any { it.jsonPrimitive.content == "tools" } == true
            if (isFree && textOut) id to tools else null
        }
        // Models that support tool calls first: Agent Mode needs them.
        return free.sortedWith(compareByDescending<Pair<String, Boolean>> { it.second }.thenBy { it.first }).map { it.first }
    }

    private fun geminiFree(key: String): List<String> {
        val skip = listOf("image", "tts", "live", "audio", "embedding", "robotics", "computer", "preview-native")
        return ids("https://generativelanguage.googleapis.com/v1beta/openai/models", mapOf("Authorization" to "Bearer $key"))
            .map { it.removePrefix("models/") }
            .filter { it.startsWith("gemini") && "flash" in it && skip.none { s -> s in it } }
            .distinct()
            .sortedDescending()
    }

    private fun openAiChat(key: String): List<String> {
        val skip = listOf("audio", "realtime", "transcribe", "tts", "image", "search", "instruct", "embedding", "moderation", "whisper", "dall")
        return ids("https://api.openai.com/v1/models", mapOf("Authorization" to "Bearer $key"))
            .filter { (it.startsWith("gpt-") || Regex("^o\\d").containsMatchIn(it)) && skip.none { s -> s in it } }
            .distinct()
            .sorted()
    }

    private fun anthropicChat(key: String): List<String> =
        ids(
            "https://api.anthropic.com/v1/models?limit=100",
            mapOf("x-api-key" to key, "anthropic-version" to "2023-06-01"),
        ).filter { it.startsWith("claude") }.distinct()

    // ---- http ----

    private fun ids(url: String, headers: Map<String, String>): List<String> =
        getJson(url, headers)["data"]?.jsonArray
            ?.mapNotNull { it.jsonObject["id"]?.jsonPrimitive?.contentOrNull }
            .orEmpty()

    private fun getJson(url: String, headers: Map<String, String>): JsonObject {
        val request = Request.Builder().url(url).apply { headers.forEach { (k, v) -> header(k, v) } }.build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                val hint = if (response.code == 401 || response.code == 403) " - check your API key" else ""
                throw IOException("Could not load models (HTTP ${response.code})$hint")
            }
            return json.parseToJsonElement(response.body?.string().orEmpty()).jsonObject
        }
    }
}
