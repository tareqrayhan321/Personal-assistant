package com.personalmentor.app.domain.reminder

import com.personalmentor.app.domain.model.Repeat
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit

object RepeatSchedule {
    /**
     * The first occurrence strictly after [now] in the series that contains [from], keeping the local
     * time of day (also across daylight-saving changes). Missed occurrences are skipped, not replayed.
     * Returns [from] unchanged for [Repeat.NONE].
     */
    fun nextAfter(from: Long, repeat: Repeat, now: Long, zone: ZoneId = ZoneId.systemDefault()): Long {
        if (repeat == Repeat.NONE) return from
        val stepDays = if (repeat == Repeat.DAILY) 1L else 7L
        var t = ZonedDateTime.ofInstant(Instant.ofEpochMilli(from), zone)
        val gapDays = ChronoUnit.DAYS.between(t.toLocalDate(), Instant.ofEpochMilli(now).atZone(zone).toLocalDate())
        if (gapDays > 0) t = t.plusDays(gapDays / stepDays * stepDays)
        while (t.toInstant().toEpochMilli() <= now) t = t.plusDays(stepDays)
        return t.toInstant().toEpochMilli()
    }
}
