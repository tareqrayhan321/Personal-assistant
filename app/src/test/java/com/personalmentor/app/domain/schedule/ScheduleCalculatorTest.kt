package com.personalmentor.app.domain.schedule

import com.personalmentor.app.domain.model.ScheduleRepeat
import com.personalmentor.app.domain.model.ScheduledTask
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

class ScheduleCalculatorTest {
    private val zone = ZoneId.of("UTC")

    private fun ms(date: String, time: String = "00:00"): Long =
        LocalDateTime.parse("${date}T$time").atZone(zone).toInstant().toEpochMilli()

    private fun task(repeat: ScheduleRepeat, start: String, hour: Int = 8, end: String? = null) = ScheduledTask(
        title = "t", prompt = "p", repeat = repeat, hour = hour, minute = 0,
        startEpochDay = LocalDate.parse(start).toEpochDay(), endEpochDay = end?.let { LocalDate.parse(it).toEpochDay() },
    )

    @Test fun dailyBeforeTimeRunsToday() =
        assertEquals(ms("2026-10-10", "08:00"), ScheduleCalculator.nextRun(task(ScheduleRepeat.DAILY, "2026-10-01"), ms("2026-10-10", "07:00"), zone))

    @Test fun dailyAfterTimeRunsTomorrow() =
        assertEquals(ms("2026-10-11", "08:00"), ScheduleCalculator.nextRun(task(ScheduleRepeat.DAILY, "2026-10-01"), ms("2026-10-10", "09:00"), zone))

    @Test fun weekdaysSkipWeekend() = // 2026-10-10 is a Saturday
        assertEquals(ms("2026-10-12", "08:00"), ScheduleCalculator.nextRun(task(ScheduleRepeat.WEEKDAYS, "2026-10-01"), ms("2026-10-10", "07:00"), zone))

    @Test fun weeklyKeepsStartWeekday() =
        assertEquals(ms("2026-10-17", "08:00"), ScheduleCalculator.nextRun(task(ScheduleRepeat.WEEKLY, "2026-10-03"), ms("2026-10-10", "09:00"), zone))

    @Test fun monthlyClampsToLastDay() =
        assertEquals(ms("2027-02-28", "08:00"), ScheduleCalculator.nextRun(task(ScheduleRepeat.MONTHLY, "2026-10-31"), ms("2027-01-31", "09:00"), zone))

    @Test fun onceInFutureRuns() =
        assertEquals(ms("2026-10-20", "08:00"), ScheduleCalculator.nextRun(task(ScheduleRepeat.ONCE, "2026-10-20"), ms("2026-10-10"), zone))

    @Test fun oncePastIsOver() =
        assertNull(ScheduleCalculator.nextRun(task(ScheduleRepeat.ONCE, "2026-10-01"), ms("2026-10-10"), zone))

    @Test fun endDateStopsSchedule() =
        assertNull(ScheduleCalculator.nextRun(task(ScheduleRepeat.DAILY, "2026-10-01", end = "2026-10-10"), ms("2026-10-10", "09:00"), zone))

    @Test fun futureStartWaitsForStart() =
        assertEquals(ms("2026-11-01", "08:00"), ScheduleCalculator.nextRun(task(ScheduleRepeat.DAILY, "2026-11-01"), ms("2026-10-10"), zone))
}
