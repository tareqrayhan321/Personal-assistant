package com.personalmentor.app.data.remote

import kotlinx.serialization.Serializable
import retrofit2.http.Body
import retrofit2.http.POST

/** OpenAI-compatible embeddings endpoint. */
interface EmbeddingsApi {
    @POST("v1/embeddings")
    suspend fun embed(@Body request: EmbeddingRequest): EmbeddingResponse
}

@Serializable
data class EmbeddingRequest(val model: String, val input: List<String>)

@Serializable
data class EmbeddingResponse(val data: List<EmbeddingItem> = emptyList())

@Serializable
data class EmbeddingItem(val index: Int = 0, val embedding: List<Float> = emptyList())
