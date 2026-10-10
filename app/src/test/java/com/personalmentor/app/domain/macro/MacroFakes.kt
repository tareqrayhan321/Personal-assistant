package com.personalmentor.app.domain.macro

import com.personalmentor.app.domain.repository.MacroRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.time.LocalDateTime

class InMemoryMacroRepository(initial: List<Macro> = emptyList()) : MacroRepository {
    private val state = MutableStateFlow(initial)
    override val macros: StateFlow<List<Macro>> = state
    private var next = (initial.maxOfOrNull { it.id } ?: 0L) + 1

    override fun save(macro: Macro): Macro {
        val stored = if (macro.id == 0L) macro.copy(id = next++) else macro
        state.value = if (state.value.any { it.id == stored.id }) state.value.map { if (it.id == stored.id) stored else it } else state.value + stored
        return stored
    }

    override fun delete(id: Long) { state.value = state.value.filterNot { it.id == id } }

    override fun recordRun(id: Long, at: Long, error: String?) {
        state.value = state.value.map { if (it.id == id) it.copy(lastRunAt = at, lastError = error) else it }
    }
}

class FakeDevice : MacroDevice {
    var time: LocalDateTime = LocalDateTime.of(2026, 10, 12, 9, 30) // a Monday
    var battery = 55
    var charging = false
    var wifi = true
    var screenOn = true
    val calls = mutableListOf<String>()
    var failLaunch = false
    var httpResult = HttpResult(200, "pong")

    override fun now() = time
    override fun batteryLevel() = battery
    override fun isCharging() = charging
    override fun isScreenOn() = screenOn
    override fun isWifiConnected() = wifi
    override fun isAirplaneMode() = false
    override fun isMusicActive() = false
    override fun notify(title: String, text: String) { calls += "notify:$title|$text" }
    override fun toast(text: String) { calls += "toast:$text" }
    override fun vibrate(ms: Int) { calls += "vibrate:$ms" }
    override fun speak(text: String) { calls += "speak:$text" }
    override fun playSound() { calls += "sound" }
    override fun setFlashlight(on: Boolean) { calls += "torch:$on" }
    override fun setVolume(stream: String, percent: Int) { calls += "volume:$stream:$percent" }
    var aiAnswer = "42"
    override suspend fun askAi(prompt: String): String { calls += "ai:$prompt"; return aiAnswer }
    override fun openUrl(url: String) { calls += "url:$url" }
    override fun launchApp(packageName: String) {
        if (failLaunch) throw IllegalStateException("App $packageName is not installed")
        calls += "launch:$packageName"
    }
    override fun writeFile(name: String, text: String, append: Boolean) { calls += "file:$name:$append:$text" }
    override suspend fun httpRequest(method: String, url: String, body: String): HttpResult { calls += "http:$method:$url"; return httpResult }
}
