package com.personalmentor.app.domain.repository

import com.personalmentor.app.domain.model.AgentSettings
import kotlinx.coroutines.flow.StateFlow

interface AgentSettingsRepository {
    val settings: StateFlow<AgentSettings>
    fun current(): AgentSettings = settings.value
    fun save(settings: AgentSettings)
}
