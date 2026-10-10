package com.personalmentor.app.data.local

import com.personalmentor.app.domain.model.Connector
import com.personalmentor.app.domain.model.CloudComputer
import com.personalmentor.app.domain.model.Project
import com.personalmentor.app.domain.model.RunMode
import com.personalmentor.app.domain.model.RunStatus
import com.personalmentor.app.domain.model.ScheduleRepeat
import com.personalmentor.app.domain.model.ScheduledTask
import com.personalmentor.app.domain.model.TaskRun

fun ScheduledTaskEntity.toDomain() = ScheduledTask(
    id = id, title = title, prompt = prompt, repeat = ScheduleRepeat.fromName(repeatRule),
    hour = hour, minute = minute, startEpochDay = startEpochDay, endEpochDay = endEpochDay,
    skipConfirmations = skipConfirmations, runMode = RunMode.fromName(runMode),
    connectors = Connector.parseSet(connectors), agentModel = agentModel, projectId = projectId,
    cloudComputerId = cloudComputerId,
    enabled = enabled, nextRunAt = nextRunAt, createdAt = createdAt,
)

fun ScheduledTask.toEntity() = ScheduledTaskEntity(
    id = id, title = title, prompt = prompt, repeatRule = repeat.name,
    hour = hour, minute = minute, startEpochDay = startEpochDay, endEpochDay = endEpochDay,
    skipConfirmations = skipConfirmations, runMode = runMode.name,
    connectors = Connector.encode(connectors), agentModel = agentModel, projectId = projectId,
    cloudComputerId = cloudComputerId,
    enabled = enabled, nextRunAt = nextRunAt, createdAt = createdAt,
)

fun TaskRunEntity.toDomain() = TaskRun(
    id = id, scheduledTaskId = scheduledTaskId, taskTitle = taskTitle, startedAt = startedAt,
    finishedAt = finishedAt, status = RunStatus.fromName(status), output = output, error = error,
)

fun TaskRun.toEntity() = TaskRunEntity(
    id = id, scheduledTaskId = scheduledTaskId, taskTitle = taskTitle, startedAt = startedAt,
    finishedAt = finishedAt, status = status.name, output = output, error = error,
)

fun ProjectEntity.toDomain() = Project(id = id, name = name, instructions = instructions, createdAt = createdAt)

fun Project.toEntity() = ProjectEntity(id = id, name = name, instructions = instructions, createdAt = createdAt)

fun CloudComputerEntity.toDomain() = CloudComputer(id = id, name = name, url = url, token = token, createdAt = createdAt)

fun CloudComputer.toEntity() = CloudComputerEntity(id = id, name = name, url = url, token = token, createdAt = createdAt)
