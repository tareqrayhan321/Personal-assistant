package com.personalmentor.app.di

import com.personalmentor.app.data.repository.SwitchingAssistantResponder
import com.personalmentor.app.domain.assistant.AssistantResponder
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class AssistantModule {

    /** Real LLM when configured (Settings or local.properties), otherwise the offline demo responder. */
    @Binds
    @Singleton
    abstract fun bindAssistantResponder(impl: SwitchingAssistantResponder): AssistantResponder
}
