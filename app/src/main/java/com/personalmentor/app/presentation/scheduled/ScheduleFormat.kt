package com.personalmentor.app.presentation.scheduled

import com.personalmentor.app.domain.model.ScheduleRepeat
import com.personalmentor.app.domain.model.ScheduledTask
import java.text.DateFormat
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.util.Date
import java.util.Locale

internal const val MILLIS_PER_DAY = 86_400_000L

internal fun formatTime(hour: Int, minute: Int): String =
    LocalTime.of(hour, minute).format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT))

internal fun formatDay(epochDay: Long): String =
    LocalDate.ofEpochDay(epochDay).format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))

internal fun formatMillis(millis: Long): String =
    DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(millis))

internal fun formatDuration(millis: Long): String {
    val seconds = millis / 1000
    return when {
        seconds < 1 -> "under 1 s"
        seconds < 60 -> "$seconds s"
        else -> "${seconds / 60} min ${seconds % 60} s"
    }
}

internal fun weekdayName(epochDay: Long): String =
    LocalDate.ofEpochDay(epochDay).dayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault())

internal fun scheduleSummary(task: ScheduledTask): String {
    val time = formatTime(task.hour, task.minute)
    val base = when (task.repeat) {
        ScheduleRepeat.ONCE -> "Once · ${formatDay(task.startEpochDay)} · $time"
        ScheduleRepeat.DAILY -> "Daily · $time"
        ScheduleRepeat.WEEKDAYS -> "Weekdays · $time"
        ScheduleRepeat.WEEKLY -> "Weekly on ${weekdayName(task.startEpochDay)} · $time"
        ScheduleRepeat.MONTHLY -> "Monthly on day ${LocalDate.ofEpochDay(task.startEpochDay).dayOfMonth} · $time"
    }
    return task.endEpochDay?.let { "$base · until ${formatDay(it)}" } ?: base
}
