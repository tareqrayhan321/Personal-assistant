package com.personalmentor.app.domain.model

/** Runtime LLM / embeddings configuration (any OpenAI-compatible provider). */
data class LlmSettings(
    val baseUrl: String,
    val apiKey: String,
    val model: String,
    val embeddingModel: String,
) {
    /** Offline demo mode: no key and still pointing at the default OpenAI endpoint. */
    val useFake: Boolean
        get() = apiKey.isBlank() && baseUrl.trim().trimEnd('/') == DEFAULT_BASE_URL

    companion object {
        const val DEFAULT_BASE_URL = "https://api.openai.com"
    }
}
