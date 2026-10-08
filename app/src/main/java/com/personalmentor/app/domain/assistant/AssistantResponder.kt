package com.personalmentor.app.domain.assistant

import com.personalmentor.app.domain.model.AssistantMode
import com.personalmentor.app.domain.model.ChatMessage
import kotlinx.coroutines.flow.Flow

/**
 * Produces the assistant's reply as a stream of text deltas.
 *
 * Implementations: [com.personalmentor.app.data.repository.LlmAssistantResponder] (real LLM, SSE streaming,
 * tool calling in Task Mode) and [com.personalmentor.app.data.repository.FakeAssistantResponder] (offline).
 * Next: a RAG-backed Mentor implementation (embed -> vector search -> grounded prompt).
 */
interface AssistantResponder {
    fun respond(mode: AssistantMode, history: List<ChatMessage>): Flow<String>
}
