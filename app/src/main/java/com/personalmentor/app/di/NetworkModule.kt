package com.personalmentor.app.di

import com.personalmentor.app.BuildConfig
import com.personalmentor.app.data.remote.AuthInterceptor
import com.personalmentor.app.data.remote.EmbeddingsApi
import com.personalmentor.app.data.remote.LlmApi
import com.personalmentor.app.domain.repository.SettingsRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideJson(): Json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        encodeDefaults = true // sends defaults such as "type": "function"
    }

    @Provides
    @Singleton
    fun provideOkHttpClient(settings: SettingsRepository): OkHttpClient = OkHttpClient.Builder()
        .addInterceptor(AuthInterceptor(settings))
        .apply {
            if (BuildConfig.DEBUG) {
                addInterceptor(
                    HttpLoggingInterceptor().apply {
                        // HEADERS only: Level.BODY buffers the whole response and would break SSE streaming.
                        level = HttpLoggingInterceptor.Level.HEADERS
                        redactHeader("Authorization")
                    }
                )
            }
        }
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS) // LLM responses can be slow
        .build()

    @Provides
    @Singleton
    fun provideRetrofit(client: OkHttpClient, json: Json): Retrofit = Retrofit.Builder()
        // Placeholder: AuthInterceptor swaps in the base URL from Settings on every request.
        .baseUrl("http://localhost/")
        .client(client)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()

    @Provides
    @Singleton
    fun provideLlmApi(retrofit: Retrofit): LlmApi = retrofit.create(LlmApi::class.java)

    @Provides
    @Singleton
    fun provideEmbeddingsApi(retrofit: Retrofit): EmbeddingsApi = retrofit.create(EmbeddingsApi::class.java)
}
