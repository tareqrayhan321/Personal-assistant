package com.personalmentor.app.data.remote

/** Line-level parser for the Server-Sent Events stream of chat completions. */
object SseParser {
    sealed interface Event {
        data class Data(val payload: String) : Event
        data object Done : Event
        data object Ignore : Event
    }

    /** One line of the stream, without its line terminator. */
    fun parseLine(line: String): Event {
        if (!line.startsWith("data:")) return Event.Ignore // comments, keep-alives, other fields
        val payload = line.removePrefix("data:").trim()
        return when {
            payload.isEmpty() -> Event.Ignore
            payload == "[DONE]" -> Event.Done
            else -> Event.Data(payload)
        }
    }
}
