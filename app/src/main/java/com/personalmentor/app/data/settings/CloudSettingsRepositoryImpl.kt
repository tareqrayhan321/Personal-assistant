package com.personalmentor.app.data.settings

import android.content.Context
import com.personalmentor.app.domain.cloud.CloudSettings
import com.personalmentor.app.domain.repository.CloudSettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * App-private SharedPreferences, like the LLM settings (backups are disabled in the manifest). Off by default.
 * The owner token never goes into BuildConfig, so it cannot be extracted from a shipped APK.
 */
@Singleton
class CloudSettingsRepositoryImpl @Inject constructor(
    @ApplicationContext context: Context,
) : CloudSettingsRepository {

    private val prefs = context.getSharedPreferences("cloud_settings", Context.MODE_PRIVATE)
    private val state = MutableStateFlow(load())
    override val settings: StateFlow<CloudSettings> = state.asStateFlow()

    override fun save(settings: CloudSettings) {
        val clean = settings.copy(baseUrl = settings.baseUrl.trim(), ownerToken = settings.ownerToken.trim())
        prefs.edit()
            .putBoolean(ENABLED, clean.enabled)
            .putString(BASE_URL, clean.baseUrl)
            .putString(TOKEN, clean.ownerToken)
            .apply()
        state.value = clean
    }

    override fun clear() {
        prefs.edit().clear().apply()
        state.value = load()
    }

    private fun load() = CloudSettings(
        enabled = prefs.getBoolean(ENABLED, false),
        baseUrl = prefs.getString(BASE_URL, null).orEmpty(),
        ownerToken = prefs.getString(TOKEN, null).orEmpty(),
    )

    private companion object {
        const val ENABLED = "enabled"
        const val BASE_URL = "base_url"
        const val TOKEN = "owner_token"
    }
}
