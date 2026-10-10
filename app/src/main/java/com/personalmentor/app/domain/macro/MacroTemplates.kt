package com.personalmentor.app.domain.macro

data class MacroTemplate(val name: String, val description: String, val macro: Macro)

/** Ready-made macros shown in the Templates tab. */
object MacroTemplates {
    val all: List<MacroTemplate> = listOf(
        MacroTemplate(
            "Low battery alert", "Notify when the battery drops below 20% and the phone is not charging.",
            Macro(
                name = "Low battery alert",
                triggers = listOf(MacroItem("battery_below", mapOf("level" to "20"))),
                actions = listOf(MacroItem("notify", mapOf("title" to "Battery low", "text" to "Battery is at {battery}%"))),
                constraints = listOf(MacroItem("c_not_charging")),
            ),
        ),
        MacroTemplate(
            "Morning greeting", "Speak a greeting at 08:00 on weekdays.",
            Macro(
                name = "Morning greeting",
                triggers = listOf(MacroItem("time_of_day", mapOf("time" to "08:00", "days" to MacroCatalog.DAYS_WEEKDAYS))),
                actions = listOf(MacroItem("speak", mapOf("text" to "Good morning. It is {time}."))),
            ),
        ),
        MacroTemplate(
            "Charger log", "Write a line to the activity log whenever the charger is plugged in.",
            Macro(
                name = "Charger log",
                triggers = listOf(MacroItem("power_connected")),
                actions = listOf(MacroItem("log", mapOf("text" to "Charger connected at {time}, battery {battery}%"))),
            ),
        ),
        MacroTemplate(
            "Screen-on welcome", "Show a short message every time the screen turns on, only while on Wi-Fi.",
            Macro(
                name = "Screen-on welcome",
                triggers = listOf(MacroItem("screen_on")),
                actions = listOf(MacroItem("toast", mapOf("text" to "Welcome back"))),
                constraints = listOf(MacroItem("c_wifi_connected")),
            ),
        ),
    )
}
