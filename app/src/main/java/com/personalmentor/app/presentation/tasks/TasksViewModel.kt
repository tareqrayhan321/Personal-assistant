package com.personalmentor.app.presentation.tasks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.personalmentor.app.domain.model.TodoTask
import com.personalmentor.app.domain.usecase.CanScheduleExactAlarmsUseCase
import com.personalmentor.app.domain.usecase.DeleteTaskUseCase
import com.personalmentor.app.domain.usecase.ObserveTasksUseCase
import com.personalmentor.app.domain.usecase.RescheduleRemindersUseCase
import com.personalmentor.app.domain.usecase.SetTaskDoneUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class TasksViewModel @Inject constructor(
    observeTasks: ObserveTasksUseCase,
    private val setTaskDone: SetTaskDoneUseCase,
    private val deleteTask: DeleteTaskUseCase,
    private val canScheduleExactAlarms: CanScheduleExactAlarmsUseCase,
    private val rescheduleReminders: RescheduleRemindersUseCase,
) : ViewModel() {

    val tasks: StateFlow<List<TodoTask>> = observeTasks()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val exactAlarmAllowed = MutableStateFlow(canScheduleExactAlarms())

    /** Show the banner only when it matters: exact alarms are off AND a reminder is still pending. */
    val showExactAlarmBanner: StateFlow<Boolean> = combine(tasks, exactAlarmAllowed) { list, allowed ->
        val now = System.currentTimeMillis()
        !allowed && list.any { !it.isDone && (it.remindAt ?: 0L) > now }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    /** Call on every resume: the user may have just come back from the system settings screen. */
    fun onScreenResumed() {
        val allowed = canScheduleExactAlarms()
        val justAllowed = allowed && !exactAlarmAllowed.value
        exactAlarmAllowed.value = allowed
        if (justAllowed) viewModelScope.launch { rescheduleReminders() } // upgrade inexact alarms to exact
    }

    fun onToggle(task: TodoTask) {
        viewModelScope.launch { setTaskDone(task.id, !task.isDone) }
    }

    fun onDelete(task: TodoTask) {
        viewModelScope.launch { deleteTask(task.id) }
    }
}
