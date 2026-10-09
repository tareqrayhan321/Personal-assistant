package com.personalmentor.app.data.settings

import android.content.Context
import com.personalmentor.app.domain.model.AgentSettings
import com.personalmentor.app.domain.model.ApprovalMode
import com.personalmentor.app.domain.repository.AgentSettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/** GitHub token + approval mode in app-private SharedPreferences (backups are disabled in the manifest). */
@Singleton
class AgentSettingsRepositoryImpl @Inject constructor(
    @ApplicationContext context: Context,
) : AgentSettingsRepository {

    private val prefs = context.getSharedPreferences("agent_settings", Context.MODE_PRIVATE)
    private val state = MutableStateFlow(load())
    override val settings: StateFlow<AgentSettings> = state.asStateFlow()

    override fun save(settings: AgentSettings) {
        val clean = settings.copy(githubToken = settings.githubToken.trim())
        prefs.edit()
            .putString(GITHUB_TOKEN, clean.githubToken)
            .putString(APPROVAL_MODE, clean.approvalMode.name)
            .apply()
        state.value = clean
    }

    private fun load() = AgentSettings(
        githubToken = prefs.getString(GITHUB_TOKEN, null).orEmpty(),
        approvalMode = prefs.getString(APPROVAL_MODE, null)
            ?.let { name -> ApprovalMode.entries.firstOrNull { it.name == name } }
            ?: ApprovalMode.SENSITIVE,
    )

    private companion object {
        const val GITHUB_TOKEN = "github_token"
        const val APPROVAL_MODE = "approval_mode"
    }
}
