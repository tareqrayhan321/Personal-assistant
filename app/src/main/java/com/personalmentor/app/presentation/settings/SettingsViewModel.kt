package com.personalmentor.app.presentation.settings

import androidx.lifecycle.ViewModel
import com.personalmentor.app.domain.model.LlmSettings
import com.personalmentor.app.domain.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import javax.inject.Inject

data class ProviderPreset(val label: String, val baseUrl: String, val model: String, val embeddingModel: String?)

val PROVIDER_PRESETS = listOf(
    ProviderPreset("OpenAI", "https://api.openai.com/", "gpt-4o-mini", "text-embedding-3-small"),
    ProviderPreset("OpenRouter", "https://openrouter.ai/api/", "openai/gpt-4o-mini", null),
    ProviderPreset("Ollama (emulator)", "http://10.0.2.2:11434/", "llama3.1", "nomic-embed-text"),
)

data class SettingsUiState(
    val form: LlmSettings,
    val saved: LlmSettings,
) {
    val baseUrlValid: Boolean get() = form.baseUrl.trim().toHttpUrlOrNull() != null
    val valid: Boolean get() = baseUrlValid && form.model.isNotBlank() && form.embeddingModel.isNotBlank()
    val dirty: Boolean get() = form != saved
    val canSave: Boolean get() = valid && dirty
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repository: SettingsRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(repository.current().let { SettingsUiState(it, it) })
    val state: StateFlow<SettingsUiState> = _state.asStateFlow()

    fun onBaseUrl(v: String) = edit { it.copy(baseUrl = v) }
    fun onApiKey(v: String) = edit { it.copy(apiKey = v) }
    fun onModel(v: String) = edit { it.copy(model = v) }
    fun onEmbeddingModel(v: String) = edit { it.copy(embeddingModel = v) }

    fun onPreset(p: ProviderPreset) = edit {
        it.copy(baseUrl = p.baseUrl, model = p.model, embeddingModel = p.embeddingModel ?: it.embeddingModel)
    }

    fun onSave() {
        val s = _state.value
        if (!s.canSave) return
        repository.save(s.form)
        val saved = repository.current()
        _state.value = SettingsUiState(saved, saved)
    }

    fun onReset() {
        repository.reset()
        val cur = repository.current()
        _state.value = SettingsUiState(cur, cur)
    }

    private fun edit(block: (LlmSettings) -> LlmSettings) = _state.update { it.copy(form = block(it.form)) }
}
