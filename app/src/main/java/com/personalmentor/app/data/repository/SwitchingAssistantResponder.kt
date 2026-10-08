package com.personalmentor.app.data.repository

import com.personalmentor.app.domain.assistant.AssistantResponder
import com.personalmentor.app.domain.model.AssistantMode
import com.personalmentor.app.domain.model.ChatMessage
import com.personalmentor.app.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Provider

/** Picks the real LLM or the offline demo per request, so Settings changes apply immediately. */
class SwitchingAssistantResponder @Inject constructor(
    private val settings: SettingsRepository,
    private val llm: Provider<LlmAssistantResponder>,
    private val fake: Provider<FakeAssistantResponder>,
) : AssistantResponder {
    override fun respond(mode: AssistantMode, history: List<ChatMessage>): Flow<String> =
        if (settings.current().useFake) fake.get().respond(mode, history) else llm.get().respond(mode, history)
}
