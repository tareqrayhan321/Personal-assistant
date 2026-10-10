package com.personalmentor.app.data.remote

import com.personalmentor.app.domain.repository.SettingsRepository
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException

/**
 * Applies the current Settings to every request: rewrites scheme/host/port/path prefix to the
 * configured base URL (Retrofit is built with a placeholder) and adds the bearer key if set.
 */
class AuthInterceptor(private val settings: SettingsRepository) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val s = settings.current()
        val base = s.baseUrl.trim().toHttpUrlOrNull()
            ?: throw IOException("Invalid base URL in Settings")
        val original = chain.request()
        val basePath = base.encodedPath.trimEnd('/')
        // Gemini's OpenAI-compatible base already ends in /v1beta/openai, so its paths have no /v1 prefix.
        val path = if (basePath.endsWith("/openai")) original.url.encodedPath.removePrefix("/v1") else original.url.encodedPath
        val url = original.url.newBuilder()
            .scheme(base.scheme)
            .host(base.host)
            .port(base.port)
            .encodedPath(basePath + path)
            .build()
        val request = original.newBuilder().url(url).apply {
            if (s.apiKey.isNotBlank()) header("Authorization", "Bearer ${s.apiKey}")
            if (base.host == "api.anthropic.com") {
                if (s.apiKey.isNotBlank()) header("x-api-key", s.apiKey)
                header("anthropic-version", "2023-06-01")
            }
        }.build()
        return chain.proceed(request)
    }
}
