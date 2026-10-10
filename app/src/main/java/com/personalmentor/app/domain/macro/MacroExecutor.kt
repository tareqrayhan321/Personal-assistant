package com.personalmentor.app.domain.macro

import com.personalmentor.app.domain.repository.MacroRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

/** Everything a macro can do to or ask of the phone. The Android side lives in data/macro. */
interface MacroDevice {
    fun now(): LocalDateTime
    fun batteryLevel(): Int
    fun isCharging(): Boolean
    fun isScreenOn(): Boolean
    fun isWifiConnected(): Boolean
    fun isAirplaneMode(): Boolean
    fun isMusicActive(): Boolean

    fun notify(title: String, text: String)
    fun toast(text: String)
    fun vibrate(ms: Int)
    fun speak(text: String)
    fun playSound()
    fun setFlashlight(on: Boolean)
    fun setVolume(stream: String, percent: Int)
    /** Sends a prompt to the configured LLM and returns its text answer. */
    suspend fun askAi(prompt: String): String
    fun openUrl(url: String)
    fun launchApp(packageName: String)
    fun writeFile(name: String, text: String, append: Boolean)
    suspend fun httpRequest(method: String, url: String, body: String): HttpResult
}

data class HttpResult(val code: Int, val body: String)

/** Something that happened on the phone. [type] values: tick, battery, power_connected, screen_on, boot, … */
data class MacroEvent(
    val type: String,
    val level: Int = 0,
    val previousLevel: Int = 0,
    val time: LocalDateTime? = null,
    val extra: String = "",
)

sealed interface RunOutcome {
    data object Completed : RunOutcome
    data object ConstraintsNotMet : RunOutcome
    data object Stopped : RunOutcome
    data object NotFound : RunOutcome
    data object AlreadyRunning : RunOutcome
    data class Failed(val message: String) : RunOutcome
}

object TriggerMatcher {
    fun isAutomatic(item: MacroItem) = item.type != "manual" && item.type != "invoked"

    fun matches(item: MacroItem, event: MacroEvent): Boolean = when (item.type) {
        "app_installed" -> event.type == "app_installed"
        "app_removed" -> event.type == "app_removed"
        "battery_below" -> event.type == "battery" && item.number("level")?.let { event.previousLevel >= it && event.level < it } == true
        "battery_above" -> event.type == "battery" && item.number("level")?.let { event.previousLevel <= it && event.level > it } == true
        "power_connected" -> event.type == "power_connected"
        "power_disconnected" -> event.type == "power_disconnected"
        "wifi_enabled" -> event.type == "wifi_on"
        "wifi_disabled" -> event.type == "wifi_off"
        "airplane_on" -> event.type == "airplane_on"
        "airplane_off" -> event.type == "airplane_off"
        "boot" -> event.type == "boot"
        "screen_on" -> event.type == "screen_on"
        "screen_off" -> event.type == "screen_off"
        "unlock" -> event.type == "unlock"
        "time_of_day" -> event.type == "tick" && event.time != null &&
            MacroCatalog.parseTime(item.params["time"].orEmpty()) == event.time.hour * 60 + event.time.minute &&
            dayMatches(item.params["days"].orEmpty(), event.time.dayOfWeek)
        "interval" -> event.type == "tick" && event.time != null && item.number("minutes")?.let { n ->
            n > 0 && (event.time.toLocalDate().toEpochDay() * 1440 + event.time.hour * 60 + event.time.minute) % n == 0L
        } == true
        else -> false
    }

    fun dayMatches(rule: String, day: DayOfWeek): Boolean = when (rule) {
        MacroCatalog.DAYS_ALL, "" -> true
        MacroCatalog.DAYS_WEEKDAYS -> day != DayOfWeek.SATURDAY && day != DayOfWeek.SUNDAY
        MacroCatalog.DAYS_WEEKEND -> day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY
        else -> day.name.equals(rule, ignoreCase = true)
    }

    private fun MacroItem.number(key: String): Int? = params[key]?.trim()?.toIntOrNull()
}

/**
 * Runs macros: checks constraints, then performs the actions in order. Local variables live per run;
 * `{name}` in action texts is replaced by a variable or one of {battery}, {time}, {date}, {trigger},
 * {http_response}, {http_code}.
 */
@Singleton
class MacroExecutor @Inject constructor(
    private val repository: MacroRepository,
    private val device: MacroDevice,
) {
    private val _running = MutableStateFlow<Set<Long>>(emptySet())
    val running: StateFlow<Set<Long>> = _running.asStateFlow()

    private val _log = MutableStateFlow<List<String>>(emptyList())
    val log: StateFlow<List<String>> = _log.asStateFlow()

    @Volatile private var lastTickMinute: LocalDateTime? = null

    /** Feeds one event to every enabled macro whose trigger matches. */
    suspend fun onEvent(event: MacroEvent) {
        if (event.type == "tick") {
            val minute = event.time?.withSecond(0)?.withNano(0)
            if (minute == null || minute == lastTickMinute) return
            lastTickMinute = minute
        }
        val due = repository.macros.value.filter { m -> m.enabled && m.triggers.any { TriggerMatcher.matches(it, event) } }
        if (due.isEmpty()) return
        coroutineScope { due.map { m -> async { run(m.id, event.type) } }.awaitAll() }
    }

    suspend fun run(id: Long, trigger: String, depth: Int = 0): RunOutcome {
        val macro = repository.get(id) ?: return RunOutcome.NotFound
        if (depth > MAX_DEPTH) return finish(macro, RunOutcome.Failed("Macros call each other too deeply"))
        if (id in _running.value) return RunOutcome.AlreadyRunning
        _running.update { it + id }
        try {
            val vars = macro.variables.associate { it.name to it.value }.toMutableMap()
            vars["trigger"] = trigger
            if (!macro.constraints.all { checkConstraint(it, vars) }) {
                addLog("${macro.name}: skipped (constraints not met)")
                return RunOutcome.ConstraintsNotMet
            }
            for (action in macro.actions) {
                when (val result = runAction(action, vars, depth)) {
                    ActionResult.Next -> Unit
                    ActionResult.Stop -> return finish(macro, RunOutcome.Stopped)
                    is ActionResult.Error -> return finish(macro, RunOutcome.Failed(result.message))
                }
            }
            return finish(macro, RunOutcome.Completed)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return finish(macro, RunOutcome.Failed(e.message ?: "Unexpected error"))
        } finally {
            _running.update { it - id }
        }
    }

    private fun finish(macro: Macro, outcome: RunOutcome): RunOutcome {
        val error = (outcome as? RunOutcome.Failed)?.message
        repository.recordRun(macro.id, System.currentTimeMillis(), error)
        addLog("${macro.name}: " + (error?.let { "failed - $it" } ?: if (outcome == RunOutcome.Stopped) "stopped" else "done"))
        return outcome
    }

    private sealed interface ActionResult {
        data object Next : ActionResult
        data object Stop : ActionResult
        data class Error(val message: String) : ActionResult
    }

    private suspend fun runAction(item: MacroItem, vars: MutableMap<String, String>, depth: Int): ActionResult {
        MacroCatalog.validate(item, MacroSection.ACTION)?.let { return ActionResult.Error(it) }
        fun p(key: String) = expand(item.params[key].orEmpty(), vars)
        fun n(key: String) = item.params[key]?.trim()?.toIntOrNull() ?: 0
        when (item.type) {
            "wait" -> delay(n("seconds") * 1000L)
            "stop_macro" -> return ActionResult.Stop
            "toast" -> device.toast(p("text"))
            "vibrate" -> device.vibrate(n("ms"))
            "speak" -> device.speak(p("text"))
            "play_sound" -> device.playSound()
            "flashlight" -> device.setFlashlight(item.params["state"] == "On")
            "set_volume" -> device.setVolume(item.params["stream"].orEmpty(), n("percent"))
            "notify" -> device.notify(p("title"), p("text"))
            "launch_app" -> device.launchApp(item.params["package"].orEmpty().trim())
            "open_url" -> device.openUrl(p("url"))
            "append_file" -> device.writeFile(item.params["file"].orEmpty().trim(), p("text"), append = true)
            "write_file" -> device.writeFile(item.params["file"].orEmpty().trim(), p("text"), append = false)
            "log" -> addLog(p("text"))
            "clear_log" -> _log.value = emptyList()
            "ask_ai" -> askInto(item, vars, p("prompt"))
            "ai_summarize" -> askInto(item, vars, "Summarize the following text in a few short sentences:\n\n" + p("text"))
            "ai_translate" -> askInto(
                item, vars,
                "Translate the following text into ${item.params["language"].orEmpty().trim()}. Reply with the translation only:\n\n" + p("text"),
            )
            "set_variable" -> vars[item.params["name"].orEmpty().trim()] = p("value")
            "increment_variable" -> {
                val name = item.params["name"].orEmpty().trim()
                vars[name] = ((vars[name]?.toLongOrNull() ?: 0L) + n("amount")).toString()
            }
            "run_macro" -> {
                val target = repository.findByName(item.params["name"].orEmpty())
                    ?: return ActionResult.Error("Macro \"${item.params["name"]}\" not found")
                val outcome = run(target.id, "macro", depth + 1)
                if (outcome is RunOutcome.Failed) return ActionResult.Error(outcome.message)
            }
            "set_macro_enabled" -> {
                val target = repository.findByName(item.params["name"].orEmpty())
                    ?: return ActionResult.Error("Macro \"${item.params["name"]}\" not found")
                repository.save(target.copy(enabled = item.params["state"] == "Enable"))
            }
            "http_request" -> {
                val result = device.httpRequest(item.params["method"].orEmpty(), p("url"), p("body"))
                vars["http_code"] = result.code.toString()
                vars["http_response"] = result.body
            }
            else -> return ActionResult.Error("Unknown action: ${item.type}")
        }
        return ActionResult.Next
    }

    private suspend fun askInto(item: MacroItem, vars: MutableMap<String, String>, prompt: String) {
        val answer = device.askAi(prompt)
        val name = item.params["variable"].orEmpty().trim().ifEmpty { "ai_response" }
        vars[name] = answer
        vars["ai_response"] = answer
    }

    private fun checkConstraint(item: MacroItem, vars: Map<String, String>): Boolean {
        fun level() = item.params["level"]?.trim()?.toIntOrNull() ?: 0
        val now = device.now()
        return when (item.type) {
            "c_battery_above" -> device.batteryLevel() > level()
            "c_battery_below" -> device.batteryLevel() < level()
            "c_charging" -> device.isCharging()
            "c_not_charging" -> !device.isCharging()
            "c_wifi_connected" -> device.isWifiConnected()
            "c_wifi_disconnected" -> !device.isWifiConnected()
            "c_airplane_on" -> device.isAirplaneMode()
            "c_airplane_off" -> !device.isAirplaneMode()
            "c_music_playing" -> device.isMusicActive()
            "c_music_stopped" -> !device.isMusicActive()
            "c_screen_on" -> device.isScreenOn()
            "c_screen_off" -> !device.isScreenOn()
            "c_day_of_week" -> TriggerMatcher.dayMatches(item.params["days"].orEmpty(), now.dayOfWeek)
            "c_time_between" -> {
                val from = MacroCatalog.parseTime(item.params["from"].orEmpty())
                val to = MacroCatalog.parseTime(item.params["to"].orEmpty())
                val minute = now.hour * 60 + now.minute
                if (from == null || to == null) false
                else if (from <= to) minute in from until to else minute >= from || minute < to
            }
            "c_macro_enabled" -> repository.findByName(item.params["name"].orEmpty())?.enabled == true
            "c_macro_disabled" -> repository.findByName(item.params["name"].orEmpty())?.enabled == false
            "c_variable_equals" -> vars[item.params["name"].orEmpty().trim()].orEmpty() == item.params["value"].orEmpty()
            "c_variable_not_equals" -> vars[item.params["name"].orEmpty().trim()].orEmpty() != item.params["value"].orEmpty()
            else -> false
        }
    }

    /** Replaces {name} with a variable or a built-in; unknown names stay as written. */
    fun expand(text: String, vars: Map<String, String>): String {
        if (!text.contains('{')) return text
        val now = device.now()
        return PLACEHOLDER.replace(text) { m ->
            val key = m.groupValues[1]
            when {
                vars.containsKey(key) -> vars.getValue(key)
                key == "battery" -> device.batteryLevel().toString()
                key == "time" -> now.format(TIME_FORMAT)
                key == "date" -> now.format(DATE_FORMAT)
                else -> m.value
            }
        }
    }

    private fun addLog(line: String) {
        val stamp = device.now().format(TIME_FORMAT)
        _log.update { (it + "$stamp  $line").takeLast(MAX_LOG) }
    }

    private companion object {
        const val MAX_DEPTH = 5
        const val MAX_LOG = 100
        val PLACEHOLDER = Regex("\\{([A-Za-z0-9_]+)\\}")
        val TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss")
        val DATE_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    }
}
