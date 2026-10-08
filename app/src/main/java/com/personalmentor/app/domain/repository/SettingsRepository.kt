package com.personalmentor.app.domain.repository

import com.personalmentor.app.domain.model.LlmSettings
import kotlinx.coroutines.flow.StateFlow

interface SettingsRepository {
    val settings: StateFlow<LlmSettings>
    fun current(): LlmSettings = settings.value
    fun save(settings: LlmSettings)

    /** Back to the values from local.properties / build defaults. */
    fun reset()
}
