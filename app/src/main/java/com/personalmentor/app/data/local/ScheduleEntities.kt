package com.personalmentor.app.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "scheduled_tasks")
data class ScheduledTaskEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val prompt: String,
    val repeatRule: String,
    val hour: Int,
    val minute: Int,
    val startEpochDay: Long,
    val endEpochDay: Long?,
    val skipConfirmations: Boolean,
    val runMode: String,
    val connectors: String,
    val agentModel: String?,
    val projectId: Long?,
    val cloudComputerId: Long?,
    val enabled: Boolean,
    val nextRunAt: Long?,
    val createdAt: Long,
)

@Entity(
    tableName = "task_runs",
    foreignKeys = [
        ForeignKey(
            entity = ScheduledTaskEntity::class,
            parentColumns = ["id"],
            childColumns = ["scheduledTaskId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("scheduledTaskId")],
)
data class TaskRunEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val scheduledTaskId: Long,
    val taskTitle: String,
    val startedAt: Long,
    val finishedAt: Long?,
    val status: String,
    val output: String,
    val error: String?,
)

@Entity(tableName = "projects")
data class ProjectEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val instructions: String,
    val createdAt: Long,
)

@Entity(tableName = "cloud_computers")
data class CloudComputerEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val url: String,
    val token: String,
    val createdAt: Long,
)
