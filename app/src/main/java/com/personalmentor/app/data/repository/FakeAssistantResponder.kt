package com.personalmentor.app.data.repository

import com.personalmentor.app.domain.assistant.AssistantResponder
import com.personalmentor.app.domain.model.AssistantMode
import com.personalmentor.app.domain.model.ChatMessage
import com.personalmentor.app.domain.model.Sender
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject

/** Offline stand-in used when no LLM is configured. Streams word by word like the real thing. */
class FakeAssistantResponder @Inject constructor() : AssistantResponder {
    override fun respond(mode: AssistantMode, history: List<ChatMessage>): Flow<String> = flow {
        delay(500)
        val lastUserText = history.lastOrNull { it.sender == Sender.USER }?.text.orEmpty()
        val reply = when (mode) {
            AssistantMode.TASK ->
                "Offline demo: no LLM is configured, so I can't act on \"$lastUserText\" yet. " +
                    "Set LLM_API_KEY in local.properties to enable the task agent."
            AssistantMode.MENTOR ->
                "Offline demo: no LLM is configured. Set LLM_API_KEY in local.properties to get real answers to \"$lastUserText\"."
            AssistantMode.AGENT ->
                "Offline demo: no LLM is configured, so the agent cannot browse or use GitHub for \"$lastUserText\" yet. " +
                    "Set LLM_API_KEY in local.properties (or in Settings) to enable Agent Mode."
        }
        reply.split(" ").forEach {
            emit("$it ")
            delay(50)
        }
    }
}
