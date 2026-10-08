package com.personalmentor.app.domain.usecase

import com.personalmentor.app.domain.model.AssistantMode
import com.personalmentor.app.domain.model.ChatMessage
import com.personalmentor.app.domain.model.ReportReason
import com.personalmentor.app.domain.repository.ChatRepository
import com.personalmentor.app.domain.repository.ReportRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class ObserveMessagesUseCase @Inject constructor(private val repository: ChatRepository) {
    operator fun invoke(mode: AssistantMode): Flow<List<ChatMessage>> = repository.observeMessages(mode)
}

class SendMessageUseCase @Inject constructor(private val repository: ChatRepository) {
    suspend operator fun invoke(text: String, mode: AssistantMode): Result<Unit> =
        repository.sendMessage(text.trim(), mode)
}

class ClearChatUseCase @Inject constructor(private val repository: ChatRepository) {
    suspend operator fun invoke(mode: AssistantMode) = repository.clear(mode)
}

class ReportMessageUseCase @Inject constructor(private val repository: ReportRepository) {
    /** Only assistant replies can be reported; the note is trimmed and capped at 500 characters. */
    suspend operator fun invoke(message: ChatMessage, reason: ReportReason, note: String): Result<Unit> {
        if (message.sender != com.personalmentor.app.domain.model.Sender.ASSISTANT) {
            return Result.failure(IllegalArgumentException("Only assistant replies can be reported"))
        }
        return repository.report(message, reason, note.trim().take(500))
    }
}
