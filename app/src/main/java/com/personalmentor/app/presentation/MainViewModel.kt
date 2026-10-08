package com.personalmentor.app.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.personalmentor.app.domain.model.AssistantMode
import com.personalmentor.app.domain.model.ChatMessage
import com.personalmentor.app.domain.model.ReportReason
import com.personalmentor.app.domain.usecase.ClearChatUseCase
import com.personalmentor.app.domain.usecase.ObserveMessagesUseCase
import com.personalmentor.app.domain.usecase.ReportMessageUseCase
import com.personalmentor.app.domain.usecase.RescheduleRemindersUseCase
import com.personalmentor.app.domain.usecase.SendMessageUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ChatUiState(
    val mode: AssistantMode = AssistantMode.TASK,
    val messages: List<ChatMessage> = emptyList(),
    val input: String = "",
    val isLoading: Boolean = false,
    val error: String? = null,
) {
    val canSend: Boolean get() = input.isNotBlank() && !isLoading
}

@HiltViewModel
class MainViewModel @Inject constructor(
    private val observeMessages: ObserveMessagesUseCase,
    private val sendMessage: SendMessageUseCase,
    private val clearChat: ClearChatUseCase,
    private val rescheduleReminders: RescheduleRemindersUseCase,
    private val reportMessage: ReportMessageUseCase,
) : ViewModel() {

    private val mode = MutableStateFlow(AssistantMode.TASK)
    private val input = MutableStateFlow("")
    private val isLoading = MutableStateFlow(false)
    private val error = MutableStateFlow<String?>(null)

    init {
        // Re-arm pending alarms on app start. Recovers reminders the system dropped,
        // e.g. when exact-alarm access was revoked in Settings (which clears the app's alarms).
        viewModelScope.launch { rescheduleReminders() }
    }

    // Each mode has its own conversation; switching mode re-subscribes to that mode's history.
    @OptIn(ExperimentalCoroutinesApi::class)
    private val messages = mode.flatMapLatest { observeMessages(it) }

    val uiState: StateFlow<ChatUiState> = combine(mode, messages, input, isLoading, error) { m, msgs, text, loading, err ->
        ChatUiState(mode = m, messages = msgs, input = text, isLoading = loading, error = err)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ChatUiState())

    fun onModeChange(newMode: AssistantMode) {
        mode.value = newMode
    }

    fun onInputChange(text: String) {
        input.value = text
    }

    fun onSend() {
        val text = input.value.trim()
        if (text.isEmpty() || isLoading.value) return
        input.value = ""
        val sendingMode = mode.value
        viewModelScope.launch {
            isLoading.value = true
            sendMessage(text, sendingMode).onFailure {
                error.value = it.message ?: "Something went wrong. Please try again."
            }
            isLoading.value = false
        }
    }

    fun onClearChat() {
        viewModelScope.launch { clearChat(mode.value) }
    }

    fun onReport(message: ChatMessage, reason: ReportReason, note: String) {
        viewModelScope.launch {
            reportMessage(message, reason, note).onFailure {
                error.value = it.message ?: "The report could not be sent."
            }
        }
    }

    fun onErrorShown() {
        error.value = null
    }
}
