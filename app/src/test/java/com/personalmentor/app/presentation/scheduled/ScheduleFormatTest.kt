package com.personalmentor.app.presentation.scheduled

import org.junit.Assert.assertEquals
import org.junit.Test

class ScheduleFormatTest {
    @Test fun underOneSecond() = assertEquals("under 1 s", formatDuration(400))
    @Test fun seconds() = assertEquals("12 s", formatDuration(12_900))
    @Test fun minutesAndSeconds() = assertEquals("3 min 5 s", formatDuration(185_000))
}
