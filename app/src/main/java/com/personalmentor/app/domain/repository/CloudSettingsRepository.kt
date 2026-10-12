package com.personalmentor.app.domain.repository

import com.personalmentor.app.domain.cloud.CloudSettings
import kotlinx.coroutines.flow.StateFlow

interface CloudSettingsRepository {
    val settings: StateFlow<CloudSettings>
    fun current(): CloudSettings = settings.value
    fun save(settings: CloudSettings)
    /** Turns the cloud agent off and forgets the token. */
    fun clear()
}
