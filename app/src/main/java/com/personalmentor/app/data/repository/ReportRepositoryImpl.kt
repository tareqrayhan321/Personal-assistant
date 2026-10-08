package com.personalmentor.app.data.repository

import com.personalmentor.app.BuildConfig
import com.personalmentor.app.domain.model.ChatMessage
import com.personalmentor.app.domain.model.ReportReason
import com.personalmentor.app.domain.repository.ReportRepository
import com.personalmentor.app.domain.repository.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Posts a flagged AI response to the developer's report endpoint (BuildConfig.REPORT_URL).
 * Uses its own client: the LLM client rewrites every URL to the base URL chosen in Settings.
 * Only the reported reply, the reason, the note and the model/app version are sent.
 */
@Singleton
class ReportRepositoryImpl @Inject constructor(
    private val settings: SettingsRepository,
) : ReportRepository {

    private val client = OkHttpClient.Builder().callTimeout(15, TimeUnit.SECONDS).build()

    override suspend fun report(message: ChatMessage, reason: ReportReason, note: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            val url = BuildConfig.REPORT_URL.toHttpUrlOrNull()
                ?: return@withContext Result.failure(IOException("Reporting is not configured in this build."))
            val body = buildJsonObject {
                put("reason", reason.name.lowercase())
                put("note", note)
                put("response", message.text.take(MAX_RESPONSE_CHARS))
                put("mode", message.mode.name.lowercase())
                put("model", settings.current().model)
                put("app_version", BuildConfig.VERSION_NAME)
                put("sent_at", System.currentTimeMillis())
            }.toString().toRequestBody("application/json".toMediaType())
            val request = Request.Builder().url(url).post(body).apply {
                if (BuildConfig.REPORT_TOKEN.isNotBlank()) header("Authorization", "Bearer ${BuildConfig.REPORT_TOKEN}")
            }.build()
            try {
                client.newCall(request).execute().use { r ->
                    if (r.isSuccessful) Result.success(Unit)
                    else Result.failure(IOException("The report could not be sent (HTTP ${r.code})."))
                }
            } catch (e: IOException) {
                Result.failure(IOException("The report could not be sent. Check your connection.", e))
            }
        }

    private companion object {
        const val MAX_RESPONSE_CHARS = 8_000
    }
}
