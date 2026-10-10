package com.personalmentor.app.presentation.scheduled

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.personalmentor.app.domain.model.Project
import com.personalmentor.app.domain.model.CloudComputer
import com.personalmentor.app.domain.computer.ComputerApi
import com.personalmentor.app.domain.computer.ComputerException
import com.personalmentor.app.domain.repository.CloudComputerRepository
import com.personalmentor.app.domain.model.ScheduledTask
import com.personalmentor.app.domain.model.TaskRun
import com.personalmentor.app.domain.repository.ProjectRepository
import com.personalmentor.app.domain.repository.ScheduledTaskRepository
import com.personalmentor.app.domain.repository.SettingsRepository
import com.personalmentor.app.domain.schedule.ScheduleTrigger
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ScheduledTasksViewModel @Inject constructor(
    private val repository: ScheduledTaskRepository,
    private val projectRepository: ProjectRepository,
    private val settings: SettingsRepository,
    private val trigger: ScheduleTrigger,
    private val computerRepository: CloudComputerRepository,
    private val computerApi: ComputerApi,
) : ViewModel() {

    init {
        // Recover from a reboot / force-stop (alarms are lost) and from runs a killed process left half-done.
        viewModelScope.launch {
            repository.markStaleRunsFailed()
            repository.rearmAll()
        }
    }

    val schedules: StateFlow<List<ScheduledTask>> = repository.observeSchedules()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val runs: StateFlow<List<TaskRun>> = repository.observeRuns()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val projects: StateFlow<List<Project>> = projectRepository.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val computers: StateFlow<List<CloudComputer>> = computerRepository.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val defaultModel: String get() = settings.current().model

    fun onSave(task: ScheduledTask) {
        viewModelScope.launch { repository.save(task) }
    }

    fun onToggle(task: ScheduledTask, enabled: Boolean) {
        viewModelScope.launch { repository.setEnabled(task.id, enabled) }
    }

    fun onSaveProject(project: Project, onSaved: (Long) -> Unit) {
        viewModelScope.launch { onSaved(projectRepository.save(project)) }
    }

    fun onDeleteProject(project: Project) {
        viewModelScope.launch { projectRepository.delete(project.id) }
    }

    fun onSaveComputer(computer: CloudComputer, onSaved: (Long) -> Unit) {
        viewModelScope.launch { onSaved(computerRepository.save(computer)) }
    }

    fun onDeleteComputer(computer: CloudComputer) {
        viewModelScope.launch { computerRepository.delete(computer.id) }
    }

    fun onTestComputer(computer: CloudComputer, onResult: (String) -> Unit) {
        viewModelScope.launch {
            val message = try {
                val health = computerApi.call(computer, "health")
                "Connected" + (health["name"]?.toString()?.trim('"')?.takeIf { it.isNotBlank() }?.let { " to $it" } ?: "")
            } catch (e: ComputerException) {
                e.message ?: "Could not connect."
            }
            onResult(message)
        }
    }

    fun onRunNow(task: ScheduledTask) = trigger.runNow(task.id)

    fun onDelete(task: ScheduledTask) {
        viewModelScope.launch { repository.delete(task.id) }
    }

    fun onDeleteRun(run: TaskRun) {
        viewModelScope.launch { repository.deleteRun(run.id) }
    }
}
