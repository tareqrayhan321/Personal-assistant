package com.personalmentor.app.data.repository

import com.personalmentor.app.data.local.KnowledgeChunkEntity
import com.personalmentor.app.data.local.KnowledgeDao
import com.personalmentor.app.data.local.KnowledgeDocumentEntity
import com.personalmentor.app.data.rag.DocumentReader
import com.personalmentor.app.data.rag.Embedder
import com.personalmentor.app.di.ApplicationScope
import com.personalmentor.app.domain.model.IndexStatus
import com.personalmentor.app.domain.model.KnowledgeDocument
import com.personalmentor.app.domain.model.RetrievedChunk
import com.personalmentor.app.domain.rag.TextChunker
import com.personalmentor.app.domain.rag.TopK
import com.personalmentor.app.domain.rag.VectorMath
import com.personalmentor.app.domain.repository.KnowledgeRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * On-device RAG store: chunks and their embeddings live in Room; search is an exact
 * cosine-similarity scan (vectors are stored unit-length, so cosine = dot product).
 * Indexing runs in the application scope so it survives leaving the Knowledge screen.
 */
@Singleton
class KnowledgeRepositoryImpl @Inject constructor(
    private val dao: KnowledgeDao,
    private val reader: DocumentReader,
    private val embedder: Embedder,
    @ApplicationScope private val scope: CoroutineScope,
) : KnowledgeRepository {

    private val jobs = ConcurrentHashMap<Long, Job>()
    private val indexingSlots = Semaphore(2)

    override fun observeDocuments(): Flow<List<KnowledgeDocument>> =
        dao.observeDocuments().map { list -> list.map { it.toDomain() } }

    override fun enqueue(uris: List<String>) {
        uris.forEach { uri -> scope.launch { addAndIndex(uri) } }
    }

    private suspend fun addAndIndex(uri: String) {
        val id = dao.insertDocument(
            KnowledgeDocumentEntity(
                name = reader.displayName(uri),
                charCount = 0,
                totalChunks = 0,
                indexedChunks = 0,
                status = IndexStatus.INDEXING.name,
                error = null,
                createdAt = System.currentTimeMillis(),
            )
        )
        jobs[id] = currentCoroutineContext()[Job]!!
        try {
            indexingSlots.withPermit { index(id, reader.readText(uri)) }
        } catch (e: CancellationException) {
            throw e // delete() removes the row itself
        } catch (e: Exception) {
            dao.deleteChunks(id)
            dao.setStatus(id, IndexStatus.FAILED.name, e.message ?: "Indexing failed")
        } finally {
            jobs.remove(id)
        }
    }

    private suspend fun index(id: Long, text: String) {
        val chunks = TextChunker.chunk(text)
        if (chunks.isEmpty()) throw IllegalStateException("The file contains no text")
        if (chunks.size > MAX_CHUNKS) throw IllegalStateException("File too large (${chunks.size} chunks, max $MAX_CHUNKS)")
        dao.setPlan(id, text.length, chunks.size)

        var done = 0
        for (batch in chunks.chunked(BATCH)) {
            val vectors = embedder.embed(batch)
            dao.insertChunks(
                batch.mapIndexed { i, chunkText ->
                    KnowledgeChunkEntity(
                        documentId = id,
                        ordinal = done + i,
                        text = chunkText,
                        embedding = VectorMath.toBlob(vectors[i]),
                    )
                }
            )
            done += batch.size
            dao.setProgress(id, done)
        }
        dao.setStatus(id, IndexStatus.READY.name, null)
    }

    override suspend fun delete(id: Long) {
        jobs[id]?.cancelAndJoin()
        dao.deleteDocument(id)
    }

    override suspend fun search(query: String, topK: Int): List<RetrievedChunk> = withContext(Dispatchers.Default) {
        if (query.isBlank() || dao.readyChunkCount() == 0) return@withContext emptyList()
        val q = embedder.embed(listOf(query.trim())).first()

        val top = TopK<Long>(topK)
        var afterId = 0L
        while (true) {
            val page = dao.embeddingPage(afterId, PAGE_SIZE)
            if (page.isEmpty()) break
            for (row in page) {
                val v = VectorMath.fromBlob(row.embedding)
                if (v.size == q.size) top.add(VectorMath.dot(q, v), row.id) // skips vectors from another embedding model
            }
            afterId = page.last().id
        }

        val best = top.results()
        if (best.isEmpty()) return@withContext emptyList()
        val rows = dao.chunksByIds(best.map { it.second }).associateBy { it.chunkId }
        best.mapNotNull { (score, chunkId) ->
            rows[chunkId]?.let { RetrievedChunk(it.documentId, it.documentName, it.ordinal, it.text, score) }
        }
    }

    override suspend fun recoverInterrupted() {
        dao.indexingIds().filter { it !in jobs }.forEach { id ->
            dao.deleteChunks(id)
            dao.setStatus(id, IndexStatus.FAILED.name, "Interrupted. Remove it and add the file again.")
        }
    }

    private fun KnowledgeDocumentEntity.toDomain() = KnowledgeDocument(
        id = id,
        name = name,
        charCount = charCount,
        totalChunks = totalChunks,
        indexedChunks = indexedChunks,
        status = IndexStatus.valueOf(status),
        error = error,
        createdAt = createdAt,
    )

    private companion object {
        const val BATCH = 32
        const val MAX_CHUNKS = 3_000
        const val PAGE_SIZE = 500
    }
}
