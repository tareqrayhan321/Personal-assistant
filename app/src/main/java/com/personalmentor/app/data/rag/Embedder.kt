package com.personalmentor.app.data.rag

import com.personalmentor.app.data.remote.EmbeddingRequest
import com.personalmentor.app.data.remote.EmbeddingsApi
import com.personalmentor.app.domain.rag.VectorMath
import com.personalmentor.app.domain.repository.SettingsRepository
import kotlinx.coroutines.delay
import retrofit2.HttpException
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/** Turns text into unit-length embedding vectors via the configured OpenAI-compatible API. */
@Singleton
class Embedder @Inject constructor(
    private val api: EmbeddingsApi,
    private val settings: SettingsRepository,
) {

    suspend fun embed(texts: List<String>): List<FloatArray> {
        if (texts.isEmpty()) return emptyList()
        if (settings.current().useFake) {
            throw IOException("No API key configured. Add one in Settings to use the knowledge base.")
        }
        return texts.chunked(BATCH_SIZE).flatMap { embedBatch(it) }
    }

    private suspend fun embedBatch(batch: List<String>): List<FloatArray> {
        var attempt = 0
        while (true) {
            try {
                val response = api.embed(EmbeddingRequest(settings.current().embeddingModel, batch))
                if (response.data.size != batch.size) {
                    throw IOException("Embedding service returned ${response.data.size} vectors for ${batch.size} inputs")
                }
                return response.data.sortedBy { it.index }
                    .map { VectorMath.normalize(it.embedding.toFloatArray()) }
            } catch (e: HttpException) {
                val retryable = e.code() == 429 || e.code() >= 500
                if (retryable && attempt < MAX_RETRIES) {
                    delay(1_000L shl attempt)
                    attempt++
                } else {
                    throw IOException("Embedding request failed (HTTP ${e.code()})", e)
                }
            }
        }
    }

    private companion object {
        const val BATCH_SIZE = 32
        const val MAX_RETRIES = 3
    }
}
