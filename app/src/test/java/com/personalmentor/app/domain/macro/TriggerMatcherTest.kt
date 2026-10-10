package com.personalmentor.app.domain.macro

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

class TriggerMatcherTest {
    private fun item(type: String, vararg params: Pair<String, String>) = MacroItem(type, mapOf(*params))
    private val monday0800 = LocalDateTime.of(2026, 10, 12, 8, 0)
    private val saturday0800 = LocalDateTime.of(2026, 10, 17, 8, 0)

    @Test fun batteryBelowFiresOnlyWhenCrossingTheLevel() {
        val t = item("battery_below", "level" to "20")
        assertTrue(TriggerMatcher.matches(t, MacroEvent("battery", level = 19, previousLevel = 20)))
        assertFalse(TriggerMatcher.matches(t, MacroEvent("battery", level = 18, previousLevel = 19)))
        assertFalse(TriggerMatcher.matches(t, MacroEvent("battery", level = 25, previousLevel = 26)))
    }

    @Test fun batteryAboveFiresOnlyWhenCrossingTheLevel() {
        val t = item("battery_above", "level" to "80")
        assertTrue(TriggerMatcher.matches(t, MacroEvent("battery", level = 81, previousLevel = 80)))
        assertFalse(TriggerMatcher.matches(t, MacroEvent("battery", level = 82, previousLevel = 81)))
    }

    @Test fun timeOfDayRespectsTheDayRule() {
        val weekdays = item("time_of_day", "time" to "08:00", "days" to MacroCatalog.DAYS_WEEKDAYS)
        assertTrue(TriggerMatcher.matches(weekdays, MacroEvent("tick", time = monday0800)))
        assertFalse(TriggerMatcher.matches(weekdays, MacroEvent("tick", time = saturday0800)))
        assertFalse(TriggerMatcher.matches(weekdays, MacroEvent("tick", time = monday0800.plusMinutes(1))))
    }

    @Test fun intervalFiresOnMultiplesOfTheClock() {
        val every15 = item("interval", "minutes" to "15")
        assertTrue(TriggerMatcher.matches(every15, MacroEvent("tick", time = LocalDateTime.of(2026, 10, 12, 8, 45))))
        assertFalse(TriggerMatcher.matches(every15, MacroEvent("tick", time = LocalDateTime.of(2026, 10, 12, 8, 46))))
    }

    @Test fun simpleEventsMapToTheirTriggers() {
        assertTrue(TriggerMatcher.matches(item("screen_on"), MacroEvent("screen_on")))
        assertTrue(TriggerMatcher.matches(item("power_connected"), MacroEvent("power_connected")))
        assertFalse(TriggerMatcher.matches(item("power_connected"), MacroEvent("power_disconnected")))
        assertFalse(TriggerMatcher.matches(item("manual"), MacroEvent("screen_on")))
    }

    @Test fun manualAndInvokedAreNotAutomatic() {
        assertFalse(TriggerMatcher.isAutomatic(item("manual")))
        assertFalse(TriggerMatcher.isAutomatic(item("invoked")))
        assertTrue(TriggerMatcher.isAutomatic(item("screen_on")))
    }
}
