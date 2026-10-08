package com.personalmentor.app.data.remote

import com.personalmentor.app.data.remote.SseParser.Event
import org.junit.Assert.assertEquals
import org.junit.Test

class SseParserTest {

    @Test fun dataLineYieldsItsPayload() {
        assertEquals(Event.Data("""{"a":1}"""), SseParser.parseLine("""data: {"a":1}"""))
    }

    @Test fun spaceAfterColonIsOptional() {
        assertEquals(Event.Data("x"), SseParser.parseLine("data:x"))
        assertEquals(Event.Data("x"), SseParser.parseLine("data:   x  "))
    }

    @Test fun doneMarkerEndsTheStream() {
        assertEquals(Event.Done, SseParser.parseLine("data: [DONE]"))
        assertEquals(Event.Done, SseParser.parseLine("data:[DONE]"))
    }

    @Test fun commentsKeepAlivesAndOtherFieldsAreIgnored() {
        assertEquals(Event.Ignore, SseParser.parseLine(": keep-alive"))
        assertEquals(Event.Ignore, SseParser.parseLine(""))
        assertEquals(Event.Ignore, SseParser.parseLine("event: message"))
        assertEquals(Event.Ignore, SseParser.parseLine("id: 7"))
    }

    @Test fun emptyDataLineIsIgnored() {
        assertEquals(Event.Ignore, SseParser.parseLine("data:"))
        assertEquals(Event.Ignore, SseParser.parseLine("data:   "))
    }
}
