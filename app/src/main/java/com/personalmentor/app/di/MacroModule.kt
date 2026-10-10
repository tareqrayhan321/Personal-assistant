package com.personalmentor.app.di

import com.personalmentor.app.data.macro.AndroidMacroDevice
import com.personalmentor.app.data.macro.JsonMacroRepository
import com.personalmentor.app.domain.macro.MacroDevice
import com.personalmentor.app.domain.repository.MacroRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class MacroModule {
    @Binds @Singleton
    abstract fun bindMacroRepository(impl: JsonMacroRepository): MacroRepository

    @Binds @Singleton
    abstract fun bindMacroDevice(impl: AndroidMacroDevice): MacroDevice
}
