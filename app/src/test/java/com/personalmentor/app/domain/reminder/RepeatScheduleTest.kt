package com.personalmentor.app.domain.reminder

import com.personalmentor.app.domain.model.Repeat
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

class RepeatScheduleTest {
    private val ny = ZoneId.of("America/New_York")
    private fun at(s: String, zone: ZoneId = ny) = LocalDateTime.parse(s).atZone(zone).toInstant().toEpochMilli()

    @Test fun noRepeatReturnsInput() {
        assertEquals(5L, RepeatSchedule.nextAfter(5L, Repeat.NONE, 100L))
    }

    @Test fun dailyGoesToTomorrowAtTheSameTime() {
        val next = RepeatSchedule.nextAfter(at("2026-10-08T08:00"), Repeat.DAILY, at("2026-10-08T08:00:01"), ny)
        assertEquals(at("2026-10-09T08:00"), next)
    }

    @Test fun missedDaysAreSkipped() {
        val next = RepeatSchedule.nextAfter(at("2026-10-01T08:00"), Repeat.DAILY, at("2026-10-08T12:00"), ny)
        assertEquals(at("2026-10-09T08:00"), next)
    }

    @Test fun aFutureStartIsItsOwnNextOccurrence() {
        val next = RepeatSchedule.nextAfter(at("2026-10-08T20:00"), Repeat.DAILY, at("2026-10-08T08:00"), ny)
        assertEquals(at("2026-10-08T20:00"), next)
    }

    @Test fun weeklyKeepsTheWeekday() {
        // 2026-10-08 is a Thursday
        val next = RepeatSchedule.nextAfter(at("2026-09-10T09:30"), Repeat.WEEKLY, at("2026-10-08T10:00"), ny)
        assertEquals(at("2026-10-15T09:30"), next)
    }

    @Test fun localTimeSurvivesDaylightSavingChange() {
        // US clocks go forward on 2026-03-08
        val next = RepeatSchedule.nextAfter(at("2026-03-07T08:00"), Repeat.DAILY, at("2026-03-07T08:00:01"), ny)
        assertEquals(at("2026-03-08T08:00"), next)
    }
}
