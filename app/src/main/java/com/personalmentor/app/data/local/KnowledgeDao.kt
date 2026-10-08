package com.personalmentor.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface KnowledgeDao {
    @Query("SELECT * FROM knowledge_documents ORDER BY createdAt DESC, id DESC")
    fun observeDocuments(): Flow<List<KnowledgeDocumentEntity>>

    @Insert
    suspend fun insertDocument(document: KnowledgeDocumentEntity): Long

    @Query("UPDATE knowledge_documents SET charCount = :chars, totalChunks = :total WHERE id = :id")
    suspend fun setPlan(id: Long, chars: Int, total: Int)

    @Query("UPDATE knowledge_documents SET indexedChunks = :done WHERE id = :id")
    suspend fun setProgress(id: Long, done: Int)

    @Query("UPDATE knowledge_documents SET status = :status, error = :error WHERE id = :id")
    suspend fun setStatus(id: Long, status: String, error: String?)

    @Query("SELECT id FROM knowledge_documents WHERE status = 'INDEXING'")
    suspend fun indexingIds(): List<Long>

    @Insert
    suspend fun insertChunks(chunks: List<KnowledgeChunkEntity>)

    @Query("DELETE FROM knowledge_chunks WHERE documentId = :documentId")
    suspend fun deleteChunks(documentId: Long)

    /** Chunks are removed by the foreign-key cascade. */
    @Query("DELETE FROM knowledge_documents WHERE id = :id")
    suspend fun deleteDocument(id: Long)

    @Query(
        "SELECT COUNT(*) FROM knowledge_chunks c JOIN knowledge_documents d ON d.id = c.documentId " +
            "WHERE d.status = 'READY'"
    )
    suspend fun readyChunkCount(): Int

    /** Pages through the vectors of READY documents so a search never holds them all in memory. */
    @Query(
        "SELECT c.id AS id, c.embedding AS embedding FROM knowledge_chunks c " +
            "JOIN knowledge_documents d ON d.id = c.documentId " +
            "WHERE d.status = 'READY' AND c.id > :afterId ORDER BY c.id LIMIT :limit"
    )
    suspend fun embeddingPage(afterId: Long, limit: Int): List<ChunkEmbeddingRow>

    @Query(
        "SELECT c.id AS chunkId, c.documentId AS documentId, d.name AS documentName, " +
            "c.ordinal AS ordinal, c.text AS text FROM knowledge_chunks c " +
            "JOIN knowledge_documents d ON d.id = c.documentId WHERE c.id IN (:ids)"
    )
    suspend fun chunksByIds(ids: List<Long>): List<ChunkTextRow>
}
