package com.personalmentor.app.data.computer

import com.personalmentor.app.domain.computer.CloudComputerUrl
import com.personalmentor.app.domain.computer.ComputerApi
import com.personalmentor.app.domain.computer.ComputerException
import com.personalmentor.app.domain.model.CloudComputer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
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
 * Own OkHttp client on purpose: the app-wide client rewrites requests to the LLM base URL and adds the LLM key,
 * which must never reach (or be replaced by the token of) another host. Redirects are not followed, so the token
 * cannot be forwarded elsewhere.
 */
@Singleton
class OkHttpComputerApi @Inject constructor(
    private val json: Json,
) : ComputerApi {

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .followRedirects(false)
            .followSslRedirects(false)
            .build()
    }

    override suspend fun call(computer: CloudComputer, endpoint: String, body: JsonObject?, timeoutSeconds: Int): JsonObject =
        withContext(Dispatchers.IO) {
            val base = CloudComputerUrl.normalize(computer.url)
                ?: throw ComputerException("The cloud computer URL is not valid. Use an https:// address.")
            val url = base.toHttpUrlOrNull()?.newBuilder()?.addPathSegment(endpoint)?.build()
                ?: throw ComputerException("The cloud computer URL is not valid.")
            val request = Request.Builder()
                .url(url)
                .header("Authorization", "Bearer ${computer.token}")
                .header("User-Agent", "PersonalMentor-CloudComputer")
                .method(
                    if (body == null) "GET" else "POST",
                    body?.let { json.encodeToString(JsonObject.serializer(), it).toRequestBody(JSON) },
                )
                .build()
            val callClient = client.newBuilder().readTimeout(timeoutSeconds.toLong(), TimeUnit.SECONDS).build()
            try {
                callClient.newCall(request).execute().use { response ->
                    val text = response.body?.string().orEmpty()
                    val parsed = runCatching { json.parseToJsonElement(text).jsonObject }.getOrNull()
                    if (!response.isSuccessful || parsed == null) {
                        val reason = (parsed?.get("error") as? JsonPrimitive)?.contentOrNull ?: "unexpected answer"
                        val hint = if (response.code == 401) " Check the access token." else ""
                        throw ComputerException("Cloud computer answered ${response.code}: $reason.$hint")
                    }
                    parsed
                }
            } catch (e: IOException) {
                throw ComputerException("Could not reach the cloud computer: ${e.message ?: "network error"}")
            }
        }

    private companion object {
        val JSON = "application/json".toMediaType()
    }
}
