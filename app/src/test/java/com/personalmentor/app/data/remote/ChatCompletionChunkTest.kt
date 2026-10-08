package com.personalmentor.app.data.remote

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ChatCompletionChunkTest {
    // Same options as the app's Json (see NetworkModule.provideJson).
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false; encodeDefaults = true }

    private fun decode(s: String) = json.decodeFromString<ChatCompletionChunk>(s)

    @Test fun contentDelta() {
        val chunk = decode("""{"id":"c1","object":"chat.completion.chunk","choices":[{"index":0,"delta":{"content":"হ্যালো"},"finish_reason":null}]}""")
        assertEquals("হ্যালো", chunk.choices.single().delta.content)
        assertNull(chunk.choices.single().finishReason)
    }

    @Test fun roleOnlyFirstChunkHasNoContent() {
        val chunk = decode("""{"choices":[{"delta":{"role":"assistant","content":""}}]}""")
        assertEquals("", chunk.choices.single().delta.content)
    }

    @Test fun toolCallFragments() {
        val first = decode("""{"choices":[{"delta":{"tool_calls":[{"index":0,"id":"call_1","type":"function","function":{"name":"create_task","arguments":""}}]}}]}""")
        val part = first.choices.single().delta.toolCalls!!.single()
        assertEquals(0, part.index)
        assertEquals("call_1", part.id)
        assertEquals("create_task", part.function?.name)

        val next = decode("""{"choices":[{"delta":{"tool_calls":[{"index":0,"function":{"arguments":"{\"title\":"}}]}}]}""")
        assertEquals("{\"title\":", next.choices.single().delta.toolCalls!!.single().function?.arguments)
        assertNull(next.choices.single().delta.toolCalls!!.single().id)
    }

    @Test fun finishReasonIsRead() {
        val chunk = decode("""{"choices":[{"delta":{},"finish_reason":"stop"}]}""")
        assertEquals("stop", chunk.choices.single().finishReason)
    }

    @Test fun usageOnlyChunkHasNoChoices() {
        assertEquals(emptyList<ChunkChoice>(), decode("""{"choices":[],"usage":{"total_tokens":5}}""").choices)
    }
}
