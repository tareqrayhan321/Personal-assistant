package com.personalmentor.app.di

import com.personalmentor.app.data.reminder.AlarmReminderScheduler
import com.personalmentor.app.data.repository.ChatRepositoryImpl
import com.personalmentor.app.data.schedule.AlarmScheduleTrigger
import com.personalmentor.app.data.repository.KnowledgeRepositoryImpl
import com.personalmentor.app.data.repository.ProjectRepositoryImpl
import com.personalmentor.app.data.repository.ReportRepositoryImpl
import com.personalmentor.app.data.repository.ScheduledTaskRepositoryImpl
import com.personalmentor.app.data.repository.TaskRepositoryImpl
import com.personalmentor.app.data.settings.SettingsRepositoryImpl
import com.personalmentor.app.domain.reminder.ReminderScheduler
import com.personalmentor.app.domain.schedule.ScheduleTrigger
import com.personalmentor.app.domain.repository.ChatRepository
import com.personalmentor.app.domain.repository.KnowledgeRepository
import com.personalmentor.app.domain.repository.ProjectRepository
import com.personalmentor.app.domain.repository.ReportRepository
import com.personalmentor.app.domain.repository.ScheduledTaskRepository
import com.personalmentor.app.domain.repository.SettingsRepository
import com.personalmentor.app.domain.repository.TaskRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds @Singleton
    abstract fun bindChatRepository(impl: ChatRepositoryImpl): ChatRepository

    @Binds @Singleton
    abstract fun bindTaskRepository(impl: TaskRepositoryImpl): TaskRepository

    @Binds @Singleton
    abstract fun bindKnowledgeRepository(impl: KnowledgeRepositoryImpl): KnowledgeRepository

    @Binds @Singleton
    abstract fun bindReportRepository(impl: ReportRepositoryImpl): ReportRepository

    @Binds @Singleton
    abstract fun bindSettingsRepository(impl: SettingsRepositoryImpl): SettingsRepository

    @Binds @Singleton
    abstract fun bindReminderScheduler(impl: AlarmReminderScheduler): ReminderScheduler

    @Binds @Singleton
    abstract fun bindScheduledTaskRepository(impl: ScheduledTaskRepositoryImpl): ScheduledTaskRepository

    @Binds @Singleton
    abstract fun bindProjectRepository(impl: ProjectRepositoryImpl): ProjectRepository

    @Binds @Singleton
    abstract fun bindScheduleTrigger(impl: AlarmScheduleTrigger): ScheduleTrigger
}
