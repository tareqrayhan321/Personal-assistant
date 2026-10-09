package com.personalmentor.app.presentation.agent

import android.content.Context
import android.webkit.WebView
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.personalmentor.app.data.browser.BrowserUiState
import com.personalmentor.app.data.browser.WebViewBrowserController
import com.personalmentor.app.domain.github.GitHubApi
import com.personalmentor.app.domain.model.ApprovalMode
import com.personalmentor.app.domain.repository.AgentSettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import javax.inject.Inject

sealed interface GitHubCheck {
    data object Idle : GitHubCheck
    data object Checking : GitHubCheck
    data class Connected(val login: String) : GitHubCheck
    data class Failed(val message: String) : GitHubCheck
}

data class AgentUiState(
    val token: String,
    val savedToken: String,
    val approvalMode: ApprovalMode,
    val github: GitHubCheck = GitHubCheck.Idle,
) {
    val tokenDirty: Boolean get() = token.trim() != savedToken
}

@HiltViewModel
class AgentViewModel @Inject constructor(
    private val settings: AgentSettingsRepository,
    private val githubApi: GitHubApi,
    private val browser: WebViewBrowserController,
) : ViewModel() {

    private val _state = MutableStateFlow(
        settings.current().let { AgentUiState(token = it.githubToken, savedToken = it.githubToken, approvalMode = it.approvalMode) },
    )
    val state: StateFlow<AgentUiState> = _state.asStateFlow()
    val browserState: StateFlow<BrowserUiState> = browser.ui

    fun onToken(value: String) = _state.update { it.copy(token = value, github = GitHubCheck.Idle) }

    /** The approval mode is a safety setting, so it applies immediately instead of waiting for Save. */
    fun onApprovalMode(mode: ApprovalMode) {
        settings.save(settings.current().copy(approvalMode = mode))
        _state.update { it.copy(approvalMode = mode) }
    }

    fun onSaveToken() {
        settings.save(settings.current().copy(githubToken = _state.value.token))
        val saved = settings.current().githubToken
        _state.update { it.copy(token = saved, savedToken = saved) }
    }

    fun onTestGitHub() {
        onSaveToken()
        viewModelScope.launch {
            _state.update { it.copy(github = GitHubCheck.Checking) }
            val result = try {
                val response = githubApi.call("GET", listOf("user"))
                val body = response.body as? JsonObject
                if (response.ok) {
                    GitHubCheck.Connected(((body?.get("login") as? JsonPrimitive)?.contentOrNull) ?: "unknown")
                } else {
                    val message = (body?.get("message") as? JsonPrimitive)?.contentOrNull ?: "request failed"
                    GitHubCheck.Failed("GitHub ${response.code}: $message")
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                GitHubCheck.Failed(e.message ?: "Could not reach GitHub")
            }
            _state.update { it.copy(github = result) }
        }
    }

    // ---- Browser tab ----

    fun attachBrowser(context: Context): WebView = browser.attach(context)
    fun detachBrowser() = browser.detach()
    fun go(input: String) = browser.navigate(input)
    fun goBack() = browser.goBack()
    fun goForward() = browser.goForward()
    fun reload() = browser.reload()
    fun clearBrowsingData() = browser.clearBrowsingData()
}
