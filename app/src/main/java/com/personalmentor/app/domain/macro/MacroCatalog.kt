package com.personalmentor.app.domain.macro

enum class ParamKind { TEXT, NUMBER, CHOICE, TIME, FILE_NAME, URL, PACKAGE, VARIABLE }

data class ParamDef(
    val key: String,
    val label: String,
    val kind: ParamKind = ParamKind.TEXT,
    val options: List<String> = emptyList(),
    val default: String = "",
    val optional: Boolean = false,
    val min: Int = 0,
    val max: Int = Int.MAX_VALUE,
)

data class ItemDef(
    val type: String,
    val section: MacroSection,
    val category: String,
    val label: String,
    val description: String,
    val params: List<ParamDef> = emptyList(),
)

/** Every trigger, action and constraint a macro can use, grouped like the pickers on screen. */
object MacroCatalog {
    const val DAYS_ALL = "Every day"
    const val DAYS_WEEKDAYS = "Weekdays"
    const val DAYS_WEEKEND = "Weekend"
    val DAY_NAMES = listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")

    private const val P_APPS = "Applications"
    private const val P_BATTERY = "Battery/Power"
    private const val P_CONNECT = "Connectivity"
    private const val P_TIME = "Date/Time"
    private const val P_EVENTS = "Device Events"
    private const val P_INPUT = "User Input"
    private const val P_AGENT = "MacroDroid Specific"
    private const val P_AI = "AI"

    private fun t(type: String, category: String, label: String, description: String, vararg params: ParamDef) =
        ItemDef(type, MacroSection.TRIGGER, category, label, description, params.toList())

    private fun a(type: String, category: String, label: String, description: String, vararg params: ParamDef) =
        ItemDef(type, MacroSection.ACTION, category, label, description, params.toList())

    private fun c(type: String, category: String, label: String, description: String, vararg params: ParamDef) =
        ItemDef(type, MacroSection.CONSTRAINT, category, label, description, params.toList())

    private val level = ParamDef("level", "Battery level (%)", ParamKind.NUMBER, default = "20", min = 0, max = 100)
    private val time = ParamDef("time", "Time (HH:mm)", ParamKind.TIME, default = "08:00")
    private val varName = ParamDef("name", "Variable name", ParamKind.VARIABLE)
    private val aiVariable = ParamDef("variable", "Store answer in variable", ParamKind.VARIABLE, default = "ai_response", optional = true)

    val all: List<ItemDef> = listOf(
        // ---- triggers ----
        t("app_installed", P_APPS, "Application Installed", "When an app is installed"),
        t("app_removed", P_APPS, "Application Removed", "When an app is uninstalled"),
        t("battery_below", P_BATTERY, "Battery Level Below", "When the battery drops below a level", level),
        t("battery_above", P_BATTERY, "Battery Level Above", "When the battery rises above a level", level.copy(default = "80")),
        t("power_connected", P_BATTERY, "Power Connected", "When a charger is plugged in"),
        t("power_disconnected", P_BATTERY, "Power Disconnected", "When the charger is unplugged"),
        t("wifi_enabled", P_CONNECT, "Wi-Fi Enabled", "When Wi-Fi is switched on"),
        t("wifi_disabled", P_CONNECT, "Wi-Fi Disabled", "When Wi-Fi is switched off"),
        t("airplane_on", P_CONNECT, "Airplane Mode On", "When airplane mode is switched on"),
        t("airplane_off", P_CONNECT, "Airplane Mode Off", "When airplane mode is switched off"),
        t(
            "time_of_day", P_TIME, "Day/Time", "At a time of day",
            time, ParamDef("days", "Days", ParamKind.CHOICE, listOf(DAYS_ALL, DAYS_WEEKDAYS, DAYS_WEEKEND), DAYS_ALL),
        ),
        t(
            "interval", P_TIME, "Regular Interval", "Every N minutes (aligned to the clock)",
            ParamDef("minutes", "Every (minutes)", ParamKind.NUMBER, default = "15", min = 1, max = 1440),
        ),
        t("boot", P_EVENTS, "Device Boot", "When the phone has started"),
        t("screen_on", P_EVENTS, "Screen On", "When the screen turns on"),
        t("screen_off", P_EVENTS, "Screen Off", "When the screen turns off"),
        t("unlock", P_EVENTS, "Device Unlocked", "When the phone is unlocked"),
        t("manual", P_INPUT, "Run Button", "Only when you press Run (or ask the agent to run it)"),
        t("invoked", P_AGENT, "Called by Agent / Macro", "When the agent or another macro runs this macro"),

        // ---- constraints ----
        c("c_battery_above", P_BATTERY, "Battery Above", "Only if the battery is above a level", level.copy(default = "50")),
        c("c_battery_below", P_BATTERY, "Battery Below", "Only if the battery is below a level", level),
        c("c_charging", P_BATTERY, "Power Connected", "Only while charging"),
        c("c_not_charging", P_BATTERY, "Power Not Connected", "Only while not charging"),
        c("c_wifi_connected", P_CONNECT, "Wi-Fi Connected", "Only while connected to Wi-Fi"),
        c("c_wifi_disconnected", P_CONNECT, "Wi-Fi Not Connected", "Only while not on Wi-Fi"),
        c("c_airplane_on", P_CONNECT, "Airplane Mode On", "Only while airplane mode is on"),
        c("c_airplane_off", P_CONNECT, "Airplane Mode Off", "Only while airplane mode is off"),
        c(
            "c_time_between", P_TIME, "Time Between", "Only between two times (may cross midnight)",
            ParamDef("from", "From (HH:mm)", ParamKind.TIME, default = "22:00"),
            ParamDef("to", "To (HH:mm)", ParamKind.TIME, default = "07:00"),
        ),
        c(
            "c_day_of_week", P_TIME, "Day of Week", "Only on certain days",
            ParamDef("days", "Days", ParamKind.CHOICE, listOf(DAYS_WEEKDAYS, DAYS_WEEKEND) + DAY_NAMES, DAYS_WEEKDAYS),
        ),
        c("c_variable_equals", P_AGENT, "Variable Equals", "Only if a local variable has a value", varName, ParamDef("value", "Value", optional = true)),
        c("c_variable_not_equals", P_AGENT, "Variable Not Equal", "Only if a local variable differs from a value", varName, ParamDef("value", "Value", optional = true)),
        c("c_macro_enabled", P_AGENT, "Macro Enabled", "Only if another macro is switched on", ParamDef("name", "Macro name")),
        c("c_macro_disabled", P_AGENT, "Macro Disabled", "Only if another macro is switched off", ParamDef("name", "Macro name")),
        c("c_music_playing", "Media", "Music Playing", "Only while audio is playing"),
        c("c_music_stopped", "Media", "Music Not Playing", "Only while no audio is playing"),
        c("c_screen_on", "Screen", "Screen On", "Only while the screen is on"),
        c("c_screen_off", "Screen", "Screen Off", "Only while the screen is off"),

        // ---- actions ----
        a("ask_ai", P_AI, "Ask AI", "Send a prompt to the AI; the answer goes to a variable (default {ai_response})", ParamDef("prompt", "Prompt"), aiVariable),
        a("ai_summarize", P_AI, "Summarize Text", "Let the AI summarize a text", ParamDef("text", "Text"), aiVariable),
        a(
            "ai_translate", P_AI, "Translate Text", "Let the AI translate a text",
            ParamDef("text", "Text"), ParamDef("language", "Target language", default = "English"), aiVariable,
        ),
        a("clear_log", P_AGENT, "Clear Activity Log", "Empty the activity log"),
        a("launch_app", P_APPS, "Launch Application", "Open an app by package name (works while this app is open)", ParamDef("package", "Package name", ParamKind.PACKAGE)),
        a("flashlight", "Camera/Photo", "Flashlight", "Switch the flashlight on or off", ParamDef("state", "State", ParamKind.CHOICE, listOf("On", "Off"), "On")),
        a("wait", "Conditions/Loops", "Wait", "Pause before the next action", ParamDef("seconds", "Seconds", ParamKind.NUMBER, default = "5", min = 1, max = 3600)),
        a("stop_macro", "Conditions/Loops", "Stop Macro", "End the macro here"),
        a("toast", "Device Actions", "Show Toast", "Show a short message on screen", ParamDef("text", "Message")),
        a("vibrate", "Device Actions", "Vibrate", "Vibrate the phone", ParamDef("ms", "Duration (ms)", ParamKind.NUMBER, default = "300", min = 50, max = 5000)),
        a("speak", "Device Actions", "Speak Text", "Read a text aloud", ParamDef("text", "Text")),
        a(
            "append_file", "Files", "Append to File", "Add a line to a private file of this app",
            ParamDef("file", "File name", ParamKind.FILE_NAME, default = "macro.txt"), ParamDef("text", "Text"),
        ),
        a(
            "write_file", "Files", "Write File", "Replace the content of a private file of this app",
            ParamDef("file", "File name", ParamKind.FILE_NAME, default = "macro.txt"), ParamDef("text", "Text"),
        ),
        a("log", "Logging", "Add to Log", "Write a line to the activity log", ParamDef("text", "Text")),
        a("run_macro", "Macros", "Run Macro", "Run another macro by name", ParamDef("name", "Macro name")),
        a(
            "set_macro_enabled", "Macros", "Enable/Disable Macro", "Switch another macro on or off",
            ParamDef("name", "Macro name"), ParamDef("state", "State", ParamKind.CHOICE, listOf("Enable", "Disable"), "Enable"),
        ),
        a("play_sound", "Media", "Play Notification Sound", "Play the default notification sound"),
        a(
            "notify", "Notification", "Show Notification", "Post a notification",
            ParamDef("title", "Title", default = "Macro"), ParamDef("text", "Text"),
        ),
        a("set_variable", "Variables", "Set Variable", "Store a value in a local variable", varName, ParamDef("value", "Value", optional = true)),
        a(
            "increment_variable", "Variables", "Add to Variable", "Add a number to a local variable", varName,
            ParamDef("amount", "Amount", ParamKind.NUMBER, default = "1", min = 0, max = 1_000_000),
        ),
        a(
            "set_volume", "Volume", "Set Volume", "Set a volume level",
            ParamDef("stream", "Stream", ParamKind.CHOICE, listOf("Media", "Ring", "Alarm", "Notification"), "Media"),
            ParamDef("percent", "Level (%)", ParamKind.NUMBER, default = "50", min = 0, max = 100),
        ),
        a(
            "http_request", "Web Interactions", "HTTP Request", "Call a web address; the answer goes to {http_response} and {http_code}",
            ParamDef("method", "Method", ParamKind.CHOICE, listOf("GET", "POST"), "GET"),
            ParamDef("url", "URL (https)", ParamKind.URL),
            ParamDef("body", "Body (POST)", optional = true),
        ),
        a("open_url", "Web Interactions", "Open Website", "Open an address in the browser app (works while this app is open)", ParamDef("url", "URL (https)", ParamKind.URL)),
    )

    private val byType = all.associateBy { it.type }

    fun find(type: String): ItemDef? = byType[type]

    fun categories(section: MacroSection): List<String> =
        all.filter { it.section == section }.map { it.category }.distinct().sorted()

    fun items(section: MacroSection, category: String): List<ItemDef> =
        all.filter { it.section == section && it.category == category }

    fun search(section: MacroSection, query: String): List<ItemDef> {
        val q = query.trim().lowercase()
        return all.filter { it.section == section && (it.label.lowercase().contains(q) || it.description.lowercase().contains(q)) }
    }

    /** A new item with every parameter at its default value. */
    fun newItem(def: ItemDef): MacroItem = MacroItem(def.type, def.params.associate { it.key to it.default })

    /** Null when [item] is valid, otherwise a short message for the user. */
    fun validate(item: MacroItem, section: MacroSection? = null): String? {
        val def = find(item.type) ?: return "Unknown type: ${item.type}"
        if (section != null && def.section != section) return "${def.label} is a ${def.section.singular.lowercase()}, not a ${section.singular.lowercase()}"
        for (p in def.params) {
            val value = item.params[p.key].orEmpty().trim()
            if (value.isEmpty()) {
                if (p.optional) continue
                return "${p.label} is required"
            }
            val error = when (p.kind) {
                ParamKind.TEXT -> null
                ParamKind.NUMBER -> value.toIntOrNull()?.takeIf { it in p.min..p.max }
                    ?.let { null } ?: "${p.label} must be a number from ${p.min} to ${if (p.max == Int.MAX_VALUE) "any" else p.max.toString()}"
                ParamKind.CHOICE -> if (value in p.options) null else "${p.label} must be one of: ${p.options.joinToString()}"
                ParamKind.TIME -> if (parseTime(value) != null) null else "${p.label} must look like 08:30"
                ParamKind.FILE_NAME -> if (FILE_NAME.matches(value) && !value.contains("..")) null else "${p.label} may only use letters, digits, . _ -"
                ParamKind.URL -> if (value.startsWith("https://") && !value.any { it.isWhitespace() } && value.length > 8) null else "${p.label} must start with https://"
                ParamKind.PACKAGE -> if (PACKAGE.matches(value)) null else "${p.label} must look like com.example.app"
                ParamKind.VARIABLE -> if (VARIABLE.matches(value)) null else "${p.label} may only use letters, digits and _ (max 30)"
            }
            if (error != null) return error
        }
        return null
    }

    /** "Battery Level Below: 20" style one-liner for macro cards. */
    fun summary(item: MacroItem): String {
        val def = find(item.type) ?: return item.type
        val values = def.params.mapNotNull { p -> item.params[p.key]?.trim()?.takeIf { it.isNotEmpty() }?.take(40) }
        return if (values.isEmpty()) def.label else "${def.label}: ${values.joinToString(" · ")}"
    }

    /** Compact list of type ids and their parameters for the agent's tool description. */
    fun describeForAgent(): String = MacroSection.entries.joinToString("\n") { section ->
        "${section.title}: " + all.filter { it.section == section }.joinToString("; ") { d ->
            d.type + if (d.params.isEmpty()) "" else "(" + d.params.joinToString(",") { p ->
                p.key + if (p.options.isNotEmpty()) "=" + p.options.joinToString("|") else ""
            } + ")"
        }
    }

    /** "HH:mm" to minutes after midnight, or null. */
    fun parseTime(value: String): Int? {
        val m = TIME.matchEntire(value.trim()) ?: return null
        val h = m.groupValues[1].toInt()
        val min = m.groupValues[2].toInt()
        return if (h in 0..23 && min in 0..59) h * 60 + min else null
    }

    private val TIME = Regex("^(\\d{1,2}):(\\d{2})$")
    private val FILE_NAME = Regex("^[A-Za-z0-9_.-]{1,60}$")
    private val VARIABLE = Regex("^[A-Za-z0-9_]{1,30}$")
    private val PACKAGE = Regex("^[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z0-9_]+)+$")
}
