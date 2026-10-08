package com.personalmentor.app.data.settings

import android.content.Context
import com.personalmentor.app.BuildConfig
import com.personalmentor.app.domain.model.LlmSettings
import com.personalmentor.app.domain.repository.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Stores user overrides in app-private SharedPreferences (backups are disabled in the manifest).
 * Anything not overridden falls back to the BuildConfig values from local.properties.
 */
@Singleton
class SettingsRepositoryImpl @Inject constructor(
    @ApplicationContext context: Context,
) : SettingsRepository {

    private val prefs = context.getSharedPreferences("llm_settings", Context.MODE_PRIVATE)
    private val state = MutableStateFlow(load())
    override val settings: StateFlow<LlmSettings> = state.asStateFlow()

    override fun save(settings: LlmSettings) {
        val clean = settings.copy(
            baseUrl = settings.baseUrl.trim(),
            apiKey = settings.apiKey.trim(),
            model = settings.model.trim(),
            embeddingModel = settings.embeddingModel.trim(),
        )
        prefs.edit()
            .putString(BASE_URL, clean.baseUrl)
            .putString(API_KEY, clean.apiKey)
            .putString(MODEL, clean.model)
            .putString(EMBEDDING_MODEL, clean.embeddingModel)
            .apply()
        state.value = clean
    }

    override fun reset() {
        prefs.edit().clear().apply()
        state.value = load()
    }

    private fun load() = LlmSettings(
        baseUrl = prefs.getString(BASE_URL, null) ?: BuildConfig.LLM_BASE_URL,
        apiKey = prefs.getString(API_KEY, null) ?: BuildConfig.LLM_API_KEY,
        model = prefs.getString(MODEL, null) ?: BuildConfig.LLM_MODEL,
        embeddingModel = prefs.getString(EMBEDDING_MODEL, null) ?: BuildConfig.EMBEDDING_MODEL,
    )

    private companion object {
        const val BASE_URL = "base_url"
        const val API_KEY = "api_key"
        const val MODEL = "model"
        const val EMBEDDING_MODEL = "embedding_model"
    }
}
