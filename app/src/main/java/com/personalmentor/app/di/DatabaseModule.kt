package com.personalmentor.app.di

import android.content.Context
import androidx.room.Room
import com.personalmentor.app.data.local.AppDatabase
import com.personalmentor.app.data.local.ChatMessageDao
import com.personalmentor.app.data.local.CloudComputerDao
import com.personalmentor.app.data.local.KnowledgeDao
import com.personalmentor.app.data.local.ProjectDao
import com.personalmentor.app.data.local.ScheduledTaskDao
import com.personalmentor.app.data.local.TaskRunDao
import com.personalmentor.app.data.local.TaskDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, AppDatabase.NAME)
            .addMigrations(AppDatabase.MIGRATION_1_2, AppDatabase.MIGRATION_2_3, AppDatabase.MIGRATION_3_4, AppDatabase.MIGRATION_4_5, AppDatabase.MIGRATION_5_6, AppDatabase.MIGRATION_6_7)
            .build()

    @Provides
    fun provideChatMessageDao(db: AppDatabase): ChatMessageDao = db.chatMessageDao()

    @Provides
    fun provideTaskDao(db: AppDatabase): TaskDao = db.taskDao()

    @Provides
    fun provideKnowledgeDao(db: AppDatabase): KnowledgeDao = db.knowledgeDao()

    @Provides
    fun provideScheduledTaskDao(db: AppDatabase): ScheduledTaskDao = db.scheduledTaskDao()

    @Provides
    fun provideTaskRunDao(db: AppDatabase): TaskRunDao = db.taskRunDao()

    @Provides
    fun provideProjectDao(db: AppDatabase): ProjectDao = db.projectDao()

    @Provides
    fun provideCloudComputerDao(db: AppDatabase): CloudComputerDao = db.cloudComputerDao()
}
