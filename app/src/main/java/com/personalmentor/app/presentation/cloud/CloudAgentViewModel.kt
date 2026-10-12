package com.personalmentor.app.presentation.cloud

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.personalmentor.app.domain.cloud.CloudAgentException
import com.personalmentor.app.domain.cloud.CloudConnection
import com.personalmentor.app.domain.cloud.CloudJob
import com.personalmentor.app.domain.cloud.CloudSettings
import com.personalmentor.app.domain.repository.CloudJobRepository
import com.personalmentor.app.domain.repository.CloudSettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CloudUiState(
    val form: CloudSettings,
    val saved: CloudSettings,
    val connection: CloudConnection,
    val jobs: List<CloudJob> = emptyList(),
    val loadingJobs: Boolean = false,
    val prompt: String = "",
    val submitting: Boolean = false,
    val detail: CloudJob? = null,
    val error: String? = null,
) {
    val dirty: Boolean get() = form != saved
    /** Saving is allowed for any change, but an enabled form must be complete. */
    val canSave: Boolean get() = dirty && (!form.enabled || form.configured)
    val canUseCloud: Boolean get() = !dirty && saved.blocker() == null
}

internal fun initialConnection(s: CloudSettings): CloudConnection = when {
    !s.enabled -> CloudConnection.Off
    !s.configured -> CloudConnection.NotConfigured
    else -> CloudConnection.Unchecked
}

@HiltViewModel
class CloudAgentViewModel @Inject constructor(
    private val settingsRepository: CloudSettingsRepository,
    private val jobs: CloudJobRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(
        settingsRepository.current().let { CloudUiState(form = it, saved = it, connection = initialConnection(it)) },
    )
    val state: StateFlow<CloudUiState> = _state.asStateFlow()

    init {
        // One request when the screen opens, and only if the user turned the cloud agent on. No polling.
        if (_state.value.canUseCloud) refresh()
    }

    fun onEnabled(v: Boolean) = edit { it.copy(enabled = v) }
    fun onUrl(v: String) = edit { it.copy(baseUrl = v) }
    fun onToken(v: String) = edit { it.copy(ownerToken = v) }
    fun onPrompt(v: String) = _state.update { it.copy(prompt = v) }
    fun onErrorShown() = _state.update { it.copy(error = null) }
    fun onDismissDetail() = _state.update { it.copy(detail = null) }

    fun onSave() {
        val s = _state.value
        if (!s.canSave) return
        settingsRepository.save(s.form)
        val saved = settingsRepository.current()
        _state.update { it.copy(form = saved, saved = saved, connection = initialConnection(saved), jobs = if (saved.enabled) it.jobs else emptyList()) }
    }

    /** Turns the cloud agent off and forgets the token; local features are untouched. */
    fun onForget() {
        settingsRepository.clear()
        val cur = settingsRepository.current()
        _state.update { CloudUiState(form = cur, saved = cur, connection = initialConnection(cur)) }
    }

    fun onTest() {
        if (!_state.value.canUseCloud) return
        _state.update { it.copy(connection = CloudConnection.Checking) }
        viewModelScope.launch {
            val result = attempt { jobs.checkConnection() }
            _state.update {
                it.copy(
                    connection = result.fold(
                        onSuccess = { CloudConnection.Connected },
                        onFailure = { e -> CloudConnection.Failed((e as CloudAgentException).kind, e.message.orEmpty()) },
                    ),
                )
            }
            if (result.isSuccess) refresh()
        }
    }

    fun refresh() {
        if (!_state.value.canUseCloud) return
        _state.update { it.copy(loadingJobs = true) }
        viewModelScope.launch {
            attempt { jobs.recent() }
                .onSuccess { list -> _state.update { it.copy(jobs = list, loadingJobs = false, connection = CloudConnection.Connected) } }
                .onFailure { e -> fail(e) }
        }
    }

    fun onSubmit() {
        val s = _state.value
        if (!s.canUseCloud || s.submitting || s.prompt.isBlank()) return
        _state.update { it.copy(submitting = true) }
        viewModelScope.launch {
            attempt { jobs.submit(s.prompt) }
                .onSuccess { _state.update { st -> st.copy(prompt = "", submitting = false) }; refresh() }
                .onFailure { e -> fail(e) }
        }
    }

    fun onOpen(job: CloudJob) {
        _state.update { it.copy(detail = job) } // show what the list has right away
        viewModelScope.launch { attempt { jobs.get(job.id) }.onSuccess { full -> _state.update { it.copy(detail = full) } } }
    }

    fun onCancel(job: CloudJob) {
        if (!job.status.isCancellable) return
        viewModelScope.launch {
            attempt { jobs.cancel(job.id) }
                .onSuccess { updated -> _state.update { it.copy(detail = if (it.detail?.id == job.id) updated else it.detail) }; refresh() }
                .onFailure { e -> fail(e) }
        }
    }

    private fun fail(e: Throwable) = _state.update {
        val kind = (e as? CloudAgentException)?.kind
        it.copy(
            loadingJobs = false,
            submitting = false,
            error = e.message ?: "Something went wrong.",
            connection = if (kind != null) CloudConnection.Failed(kind, e.message.orEmpty()) else it.connection,
        )
    }

    /** Wraps the call so only [CloudAgentException] comes out; anything unexpected becomes a generic one. */
    private suspend fun <T> attempt(block: suspend () -> T): Result<T> =
        try {
            Result.success(block())
        } catch (e: CancellationException) {
            throw e
        } catch (e: CloudAgentException) {
            Result.failure(e)
        } catch (e: Exception) {
            Result.failure(CloudAgentException(com.personalmentor.app.domain.cloud.CloudErrorKind.SERVER, "Unexpected error: ${e.javaClass.simpleName}"))
        }

    private fun edit(block: (CloudSettings) -> CloudSettings) = _state.update { it.copy(form = block(it.form)) }
}
