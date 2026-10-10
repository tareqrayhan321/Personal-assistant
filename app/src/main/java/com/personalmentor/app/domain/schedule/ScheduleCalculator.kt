package com.personalmentor.app.domain.schedule

import com.personalmentor.app.domain.model.ScheduleRepeat
import com.personalmentor.app.domain.model.ScheduledTask
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

object ScheduleCalculator {
    private const val MAX_SCAN_DAYS = 800

    /**
     * The first run strictly after [after], keeping the local time of day (also across daylight-saving changes).
     * Missed runs are skipped, not replayed. Null when the schedule is over (one-off in the past, or past its end date).
     */
    fun nextRun(task: ScheduledTask, after: Long, zone: ZoneId = ZoneId.systemDefault()): Long? {
        val time = LocalTime.of(task.hour, task.minute)
        val start = LocalDate.ofEpochDay(task.startEpochDay)
        val today = Instant.ofEpochMilli(after).atZone(zone).toLocalDate()
        var date = if (today.isAfter(start)) today else start
        for (i in 0 until MAX_SCAN_DAYS) {
            task.endEpochDay?.let { if (date.toEpochDay() > it) return null }
            if (task.repeat == ScheduleRepeat.ONCE && date.isAfter(start)) return null
            if (matches(task.repeat, date, start)) {
                val candidate = ZonedDateTime.of(date, time, zone).toInstant().toEpochMilli()
                if (candidate > after) return candidate
            }
            date = date.plusDays(1)
        }
        return null
    }

    private fun matches(repeat: ScheduleRepeat, date: LocalDate, start: LocalDate): Boolean = when (repeat) {
        ScheduleRepeat.ONCE -> date == start
        ScheduleRepeat.DAILY -> true
        ScheduleRepeat.WEEKDAYS -> date.dayOfWeek != DayOfWeek.SATURDAY && date.dayOfWeek != DayOfWeek.SUNDAY
        ScheduleRepeat.WEEKLY -> date.dayOfWeek == start.dayOfWeek
        ScheduleRepeat.MONTHLY -> date.dayOfMonth == minOf(start.dayOfMonth, date.lengthOfMonth())
    }
}
