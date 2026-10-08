package com.personalmentor.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        ChatMessageEntity::class,
        TaskEntity::class,
        KnowledgeDocumentEntity::class,
        KnowledgeChunkEntity::class,
    ],
    version = 4,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun chatMessageDao(): ChatMessageDao
    abstract fun taskDao(): TaskDao
    abstract fun knowledgeDao(): KnowledgeDao

    companion object {
        const val NAME = "personal_mentor.db"

        /** v2: tasks.remindAt (reminder time, epoch millis). */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE tasks ADD COLUMN remindAt INTEGER")
            }
        }

        /** v4: repeating reminders. */
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE tasks ADD COLUMN repeatRule TEXT NOT NULL DEFAULT 'NONE'")
            }
        }

        /** v3: knowledge base for Mentor Mode RAG (documents + embedded chunks). */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `knowledge_documents` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, " +
                        "`charCount` INTEGER NOT NULL, `totalChunks` INTEGER NOT NULL, " +
                        "`indexedChunks` INTEGER NOT NULL, `status` TEXT NOT NULL, " +
                        "`error` TEXT, `createdAt` INTEGER NOT NULL)"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `knowledge_chunks` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `documentId` INTEGER NOT NULL, " +
                        "`ordinal` INTEGER NOT NULL, `text` TEXT NOT NULL, `embedding` BLOB NOT NULL, " +
                        "FOREIGN KEY(`documentId`) REFERENCES `knowledge_documents`(`id`) " +
                        "ON UPDATE NO ACTION ON DELETE CASCADE)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_knowledge_chunks_documentId` " +
                        "ON `knowledge_chunks` (`documentId`)"
                )
            }
        }
    }
}
