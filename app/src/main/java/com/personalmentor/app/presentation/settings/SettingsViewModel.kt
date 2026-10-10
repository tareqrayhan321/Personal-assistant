package com.personalmentor.app.presentation.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.personalmentor.app.data.remote.ModelCatalog
import com.personalmentor.app.data.remote.ProviderKind
import com.personalmentor.app.domain.model.LlmSettings
import com.personalmentor.app.domain.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import javax.inject.Inject

data class ProviderPreset(val label: String, val baseUrl: String, val model: String, val embeddingModel: String?)

val PROVIDER_PRESETS = listOf(
    ProviderPreset("OpenAI", "https://api.openai.com/", "gpt-4o-mini", "text-embedding-3-small"),
    // OpenRouter has no fixed model: the picker lists the free ones and the first is chosen on load.
    ProviderPreset("OpenRouter", "https://openrouter.ai/api/", "", null),
    ProviderPreset("Gemini", "https://generativelanguage.googleapis.com/v1beta/openai/", "gemini-2.5-flash", "gemini-embedding-001"),
    ProviderPreset("Anthropic", "https://api.anthropic.com/", "claude-haiku-5-5", null),
    ProviderPreset("Ollama (emulator)", "http://10.0.2.2:11434/", "llama3.1", "nomic-embed-text"),
)

/** The model picker's contents: what to choose from, whether it is loading, and a note (or error) to show. */
data class ModelListState(
    val items: List<String> = emptyList(),
    val loading: Boolean = false,
    val note: String? = null,
    val isError: Boolean = false,
)

data class SettingsUiState(
    val form: LlmSettings,
    val saved: LlmSettings,
    val models: ModelListState = ModelListState(),
) {
    val provider: ProviderKind get() = ProviderKind.of(form.baseUrl)
    val baseUrlValid: Boolean get() = form.baseUrl.trim().toHttpUrlOrNull() != null
    val valid: Boolean get() = baseUrlValid && form.model.isNotBlank() && form.embeddingModel.isNotBlank()
    val dirty: Boolean get() = form != saved
    val canSave: Boolean get() = valid && dirty
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repository: SettingsRepository,
    private val catalog: ModelCatalog,
) : ViewModel() {

    private val _state = MutableStateFlow(repository.current().let { SettingsUiState(it, it) })
    val state: StateFlow<SettingsUiState> = _state.asStateFlow()

    init {
        refreshModels()
    }

    fun onBaseUrl(v: String) {
        val before = _state.value.provider
        edit { it.copy(baseUrl = v) }
        if (_state.value.provider != before) refreshModels()
    }
    fun onApiKey(v: String) = edit { it.copy(apiKey = v) }
    fun onModel(v: String) = edit { it.copy(model = v) }
    fun onEmbeddingModel(v: String) = edit { it.copy(embeddingModel = v) }

    fun onPreset(p: ProviderPreset) {
        edit { it.copy(baseUrl = p.baseUrl, model = p.model, embeddingModel = p.embeddingModel ?: it.embeddingModel) }
        refreshModels()
    }

    /** Loads the provider's model list (free ones for OpenRouter / Gemini) and keeps the chosen model inside it. */
    fun refreshModels() {
        val form = _state.value.form
        val kind = ProviderKind.of(form.baseUrl)
        if (kind == ProviderKind.OTHER) {
            _state.update { it.copy(models = ModelListState()) }
            return
        }
        _state.update { it.copy(models = it.models.copy(loading = true, note = null, isError = false)) }
        viewModelScope.launch {
            val result = catalog.load(kind, form.apiKey)
            val loaded = result.getOrNull()?.models.orEmpty()
            val items = loaded.ifEmpty { catalog.fallback(kind) }
            val note = result.getOrNull()?.note
                ?: result.exceptionOrNull()?.message
                ?: "No models found."
            val failed = result.isFailure
            _state.update { cur ->
                if (cur.provider != kind) return@update cur // the user switched provider meanwhile
                // Only a successful load is trusted enough to replace the current choice; a fallback list never does.
                val model = when {
                    items.isEmpty() -> cur.form.model
                    cur.form.model.isBlank() -> items.first()
                    loaded.isNotEmpty() && cur.form.model !in items -> items.first()
                    else -> cur.form.model
                }
                cur.copy(
                    form = cur.form.copy(model = model),
                    models = ModelListState(items = items, loading = false, note = note, isError = failed),
                )
            }
        }
    }

    fun onSave() {
        val s = _state.value
        if (!s.canSave) return
        repository.save(s.form)
        val saved = repository.current()
        _state.update { it.copy(form = saved, saved = saved) }
    }

    fun onReset() {
        repository.reset()
        val cur = repository.current()
        _state.update { it.copy(form = cur, saved = cur) }
        refreshModels()
    }

    private fun edit(block: (LlmSettings) -> LlmSettings) = _state.update { it.copy(form = block(it.form)) }
}
