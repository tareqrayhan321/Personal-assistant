package com.personalmentor.app.data.schedule

import android.content.Context
import com.personalmentor.app.data.repository.LlmAssistantResponder
import com.personalmentor.app.domain.agent.RunApprovalPolicy
import com.personalmentor.app.domain.model.RunMode
import com.personalmentor.app.domain.model.RunStatus
import com.personalmentor.app.domain.repository.CloudComputerRepository
import com.personalmentor.app.domain.repository.ProjectRepository
import com.personalmentor.app.domain.repository.ScheduledTaskRepository
import com.personalmentor.app.domain.repository.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/** Runs one scheduled task end to end and records the result in the run history. */
@Singleton
class ScheduledTaskRunner @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: ScheduledTaskRepository,
    private val projects: ProjectRepository,
    private val computers: CloudComputerRepository,
    private val llm: LlmAssistantResponder,
    private val settings: SettingsRepository,
) {
    suspend fun run(taskId: Long) {
        val task = repository.get(taskId) ?: return
        val runId = repository.startRun(task)
        var status = RunStatus.FAILED
        var output = ""
        var error: String? = null
        try {
            check(!settings.current().useFake) { "AI is not configured. Add an API key in Settings." }
            val earlier = if (task.runMode == RunMode.SAME_TASK) {
                repository.recentSuccessfulRuns(task.id, SAME_TASK_HISTORY).asReversed().map { task.prompt to it.output }
            } else {
                emptyList()
            }
            val instructions = task.projectId?.let { projects.get(it) }?.instructions
            val computer = task.cloudComputerId?.let { id ->
                computers.get(id) ?: error("The cloud computer selected for this task no longer exists.")
            }
            val policy = RunApprovalPolicy(task.skipConfirmations) {
                ScheduleNotifier.showApprovalNeeded(context, task.id, task.title)
            }
            output = withContext(policy) {
                llm.runScheduled(task.prompt, earlier, task.connectors, task.agentModel, instructions, computer)
            }.take(MAX_OUTPUT_CHARS)
            status = RunStatus.SUCCESS
        } catch (e: CancellationException) {
            withContext(NonCancellable) { repository.finishRun(runId, RunStatus.FAILED, output, "Interrupted") }
            throw e
        } catch (e: Exception) {
            error = e.message?.takeIf { it.isNotBlank() } ?: "The run failed."
        }
        repository.finishRun(runId, status, output, error)
        ScheduleNotifier.showResult(context, task.id, task.title, status == RunStatus.SUCCESS, error ?: output)
    }

    private companion object {
        const val SAME_TASK_HISTORY = 3
        const val MAX_OUTPUT_CHARS = 20_000
    }
}
