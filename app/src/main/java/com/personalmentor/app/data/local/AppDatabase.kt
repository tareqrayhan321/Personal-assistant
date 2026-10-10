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
        ScheduledTaskEntity::class,
        TaskRunEntity::class,
        ProjectEntity::class,
        CloudComputerEntity::class,
    ],
    version = 7,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun chatMessageDao(): ChatMessageDao
    abstract fun taskDao(): TaskDao
    abstract fun knowledgeDao(): KnowledgeDao
    abstract fun scheduledTaskDao(): ScheduledTaskDao
    abstract fun taskRunDao(): TaskRunDao
    abstract fun projectDao(): ProjectDao
    abstract fun cloudComputerDao(): CloudComputerDao

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

        /** v6: projects (shared instructions for scheduled tasks). */
        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `projects` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, " +
                        "`instructions` TEXT NOT NULL, `createdAt` INTEGER NOT NULL)"
                )
            }
        }

        /** v7: cloud computer connections and optional scheduled-task association. */
        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `cloud_computers` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, " +
                        "`url` TEXT NOT NULL, `token` TEXT NOT NULL, `createdAt` INTEGER NOT NULL)"
                )
                db.execSQL("ALTER TABLE `scheduled_tasks` ADD COLUMN `cloudComputerId` INTEGER")
            }
        }

        /** v5: scheduled tasks and their run history. */
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `scheduled_tasks` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `title` TEXT NOT NULL, " +
                        "`prompt` TEXT NOT NULL, `repeatRule` TEXT NOT NULL, `hour` INTEGER NOT NULL, " +
                        "`minute` INTEGER NOT NULL, `startEpochDay` INTEGER NOT NULL, `endEpochDay` INTEGER, " +
                        "`skipConfirmations` INTEGER NOT NULL, `runMode` TEXT NOT NULL, " +
                        "`connectors` TEXT NOT NULL, `agentModel` TEXT, `projectId` INTEGER, " +
                        "`enabled` INTEGER NOT NULL, `nextRunAt` INTEGER, `createdAt` INTEGER NOT NULL)"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `task_runs` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `scheduledTaskId` INTEGER NOT NULL, " +
                        "`taskTitle` TEXT NOT NULL, `startedAt` INTEGER NOT NULL, `finishedAt` INTEGER, " +
                        "`status` TEXT NOT NULL, `output` TEXT NOT NULL, `error` TEXT, " +
                        "FOREIGN KEY(`scheduledTaskId`) REFERENCES `scheduled_tasks`(`id`) " +
                        "ON UPDATE NO ACTION ON DELETE CASCADE)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_task_runs_scheduledTaskId` " +
                        "ON `task_runs` (`scheduledTaskId`)"
                )
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
