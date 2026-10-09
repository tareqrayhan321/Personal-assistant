package com.personalmentor.app.di

import com.personalmentor.app.data.browser.WebViewBrowserController
import com.personalmentor.app.data.github.OkHttpGitHubApi
import com.personalmentor.app.data.settings.AgentSettingsRepositoryImpl
import com.personalmentor.app.domain.agent.ActionApprover
import com.personalmentor.app.domain.agent.ApprovalGate
import com.personalmentor.app.domain.browser.BrowserController
import com.personalmentor.app.domain.github.GitHubApi
import com.personalmentor.app.domain.repository.AgentSettingsRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** Agent Mode wiring: in-app browser, GitHub client, approval dialog and agent settings. */
@Module
@InstallIn(SingletonComponent::class)
abstract class AgentModule {

    @Binds @Singleton
    abstract fun bindBrowserController(impl: WebViewBrowserController): BrowserController

    @Binds @Singleton
    abstract fun bindGitHubApi(impl: OkHttpGitHubApi): GitHubApi

    @Binds @Singleton
    abstract fun bindAgentSettings(impl: AgentSettingsRepositoryImpl): AgentSettingsRepository

    @Binds @Singleton
    abstract fun bindActionApprover(impl: ApprovalGate): ActionApprover
}
