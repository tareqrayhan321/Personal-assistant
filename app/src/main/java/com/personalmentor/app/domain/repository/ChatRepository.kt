package com.personalmentor.app.domain.repository

import com.personalmentor.app.domain.model.AssistantMode
import com.personalmentor.app.domain.model.ChatMessage
import kotlinx.coroutines.flow.Flow

interface ChatRepository {
    fun observeMessages(mode: AssistantMode): Flow<List<ChatMessage>>

    /** Persists the user message, asks the assistant for a reply, persists the reply. */
    suspend fun sendMessage(text: String, mode: AssistantMode): Result<Unit>

    suspend fun clear(mode: AssistantMode)
}
