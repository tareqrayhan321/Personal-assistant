package com.personalmentor.app.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import okhttp3.ResponseBody
import retrofit2.http.Body
import retrofit2.http.Headers
import retrofit2.http.POST
import retrofit2.http.Streaming

/** OpenAI-compatible chat completions endpoint, consumed as a Server-Sent Events stream. */
interface LlmApi {
    @Streaming
    @Headers("Accept: text/event-stream")
    @POST("v1/chat/completions")
    suspend fun chatCompletionStream(@Body request: ChatCompletionRequest): ResponseBody
}

// ---- request ----

@Serializable
data class ChatCompletionRequest(
    val model: String,
    val messages: List<ApiMessage>,
    val tools: List<ToolSpec>? = null,
    val stream: Boolean = true,
    val temperature: Double = 0.7,
)

@Serializable
data class ApiMessage(
    val role: String,
    val content: String? = null,
    @SerialName("tool_calls") val toolCalls: List<ToolCall>? = null,
    @SerialName("tool_call_id") val toolCallId: String? = null,
)

@Serializable
data class ToolCall(
    val id: String,
    val type: String = "function",
    val function: FunctionCall,
)

@Serializable
data class FunctionCall(val name: String, val arguments: String)

@Serializable
data class ToolSpec(val type: String = "function", val function: FunctionSpec)

@Serializable
data class FunctionSpec(val name: String, val description: String, val parameters: JsonObject)

// ---- streaming response chunks ----

@Serializable
data class ChatCompletionChunk(val choices: List<ChunkChoice> = emptyList())

@Serializable
data class ChunkChoice(
    val delta: Delta = Delta(),
    @SerialName("finish_reason") val finishReason: String? = null,
)

@Serializable
data class Delta(
    val content: String? = null,
    @SerialName("tool_calls") val toolCalls: List<ToolCallDelta>? = null,
)

@Serializable
data class ToolCallDelta(
    val index: Int = 0,
    val id: String? = null,
    val function: FunctionDelta? = null,
)

@Serializable
data class FunctionDelta(val name: String? = null, val arguments: String? = null)
