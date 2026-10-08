package com.personalmentor.app.domain.model

enum class IndexStatus { INDEXING, READY, FAILED }

data class KnowledgeDocument(
    val id: Long = 0,
    val name: String,
    val charCount: Int,
    val totalChunks: Int,
    val indexedChunks: Int,
    val status: IndexStatus,
    val error: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
)

/** A knowledge-base passage returned by similarity search. */
data class RetrievedChunk(
    val documentId: Long,
    val documentName: String,
    val ordinal: Int,
    val text: String,
    val score: Float,
)
