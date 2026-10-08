package com.personalmentor.app.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "knowledge_documents")
data class KnowledgeDocumentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val charCount: Int,
    val totalChunks: Int,
    val indexedChunks: Int,
    val status: String,
    val error: String?,
    val createdAt: Long,
)

@Suppress("ArrayInDataClass")
@Entity(
    tableName = "knowledge_chunks",
    foreignKeys = [
        ForeignKey(
            entity = KnowledgeDocumentEntity::class,
            parentColumns = ["id"],
            childColumns = ["documentId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("documentId")],
)
data class KnowledgeChunkEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val documentId: Long,
    val ordinal: Int,
    val text: String,
    /** Unit-length float32 vector, little-endian (see VectorMath.toBlob). */
    val embedding: ByteArray,
)

@Suppress("ArrayInDataClass")
data class ChunkEmbeddingRow(val id: Long, val embedding: ByteArray)

data class ChunkTextRow(
    val chunkId: Long,
    val documentId: Long,
    val documentName: String,
    val ordinal: Int,
    val text: String,
)
