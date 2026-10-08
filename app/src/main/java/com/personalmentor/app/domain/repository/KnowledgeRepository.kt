package com.personalmentor.app.domain.repository

import com.personalmentor.app.domain.model.KnowledgeDocument
import com.personalmentor.app.domain.model.RetrievedChunk
import kotlinx.coroutines.flow.Flow

interface KnowledgeRepository {
    fun observeDocuments(): Flow<List<KnowledgeDocument>>

    /** Registers the files and indexes them in the background (progress is visible via [observeDocuments]). */
    fun enqueue(uris: List<String>)

    suspend fun delete(id: Long)

    /** Top-[topK] passages most similar to [query], best first. Empty when the knowledge base is empty. */
    suspend fun search(query: String, topK: Int): List<RetrievedChunk>

    /** Marks documents left in INDEXING by a killed process as FAILED. */
    suspend fun recoverInterrupted()
}
