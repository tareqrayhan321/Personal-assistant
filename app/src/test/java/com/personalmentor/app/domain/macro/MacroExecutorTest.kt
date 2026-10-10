package com.personalmentor.app.domain.macro

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MacroExecutorTest {
    private val device = FakeDevice()

    private fun item(type: String, vararg params: Pair<String, String>) = MacroItem(type, mapOf(*params))

    private fun setup(vararg macros: Macro): Pair<MacroExecutor, InMemoryMacroRepository> {
        val repo = InMemoryMacroRepository(macros.toList())
        return MacroExecutor(repo, device) to repo
    }

    @Test fun actionsRunInOrderAndExpandBuiltInValues() = runTest {
        val (executor, repo) = setup(
            Macro(
                id = 1, name = "m", triggers = listOf(item("manual")),
                actions = listOf(item("toast", "text" to "battery {battery}% at {time}"), item("vibrate", "ms" to "200")),
            ),
        )
        assertEquals(RunOutcome.Completed, executor.run(1, "manual"))
        assertEquals(listOf("toast:battery 55% at 09:30:00", "vibrate:200"), device.calls)
        assertNotNull(repo.get(1)!!.lastRunAt)
        assertNull(repo.get(1)!!.lastError)
    }

    @Test fun unmetConstraintsSkipTheActions() = runTest {
        device.charging = true
        val (executor, _) = setup(
            Macro(id = 1, name = "m", actions = listOf(item("toast", "text" to "x")), constraints = listOf(item("c_not_charging"))),
        )
        assertEquals(RunOutcome.ConstraintsNotMet, executor.run(1, "manual"))
        assertTrue(device.calls.isEmpty())
    }

    @Test fun timeConstraintMayCrossMidnight() = runTest {
        val (executor, _) = setup(
            Macro(id = 1, name = "m", actions = listOf(item("log", "text" to "ran")), constraints = listOf(item("c_time_between", "from" to "22:00", "to" to "07:00"))),
        )
        device.time = device.time.withHour(23)
        assertEquals(RunOutcome.Completed, executor.run(1, "manual"))
        device.time = device.time.withHour(12)
        assertEquals(RunOutcome.ConstraintsNotMet, executor.run(1, "manual"))
    }

    @Test fun variablesCanBeSetIncrementedAndTested() = runTest {
        val (executor, _) = setup(
            Macro(
                id = 1, name = "m",
                variables = listOf(MacroVariable("count", "1")),
                actions = listOf(
                    item("increment_variable", "name" to "count", "amount" to "2"),
                    item("toast", "text" to "count={count}"),
                ),
                constraints = listOf(item("c_variable_equals", "name" to "count", "value" to "1")),
            ),
        )
        assertEquals(RunOutcome.Completed, executor.run(1, "manual"))
        assertEquals(listOf("toast:count=3"), device.calls)
    }

    @Test fun stopMacroEndsTheRunEarly() = runTest {
        val (executor, _) = setup(
            Macro(id = 1, name = "m", actions = listOf(item("toast", "text" to "a"), item("stop_macro"), item("toast", "text" to "b"))),
        )
        assertEquals(RunOutcome.Stopped, executor.run(1, "manual"))
        assertEquals(listOf("toast:a"), device.calls)
    }

    @Test fun aFailingActionIsRecordedAndStopsTheRun() = runTest {
        device.failLaunch = true
        val (executor, repo) = setup(
            Macro(id = 1, name = "m", actions = listOf(item("launch_app", "package" to "com.example.app"), item("toast", "text" to "after"))),
        )
        val outcome = executor.run(1, "manual")
        assertTrue(outcome is RunOutcome.Failed)
        assertTrue(repo.get(1)!!.lastError!!.contains("not installed"))
        assertTrue(device.calls.isEmpty())
    }

    @Test fun httpAnswerIsAvailableToLaterActions() = runTest {
        val (executor, _) = setup(
            Macro(
                id = 1, name = "m",
                actions = listOf(item("http_request", "method" to "GET", "url" to "https://example.com/ping"), item("toast", "text" to "{http_code}:{http_response}")),
            ),
        )
        executor.run(1, "manual")
        assertEquals(listOf("http:GET:https://example.com/ping", "toast:200:pong"), device.calls)
    }

    @Test fun runMacroCallsAnotherMacroByName() = runTest {
        val (executor, _) = setup(
            Macro(id = 1, name = "outer", actions = listOf(item("run_macro", "name" to "inner"))),
            Macro(id = 2, name = "inner", actions = listOf(item("toast", "text" to "inner ran"))),
        )
        assertEquals(RunOutcome.Completed, executor.run(1, "manual"))
        assertEquals(listOf("toast:inner ran"), device.calls)
    }

    @Test fun onlyMatchingEnabledMacrosRunForAnEvent() = runTest {
        val (executor, _) = setup(
            Macro(id = 1, name = "on", triggers = listOf(item("screen_on")), actions = listOf(item("toast", "text" to "one"))),
            Macro(id = 2, name = "off", enabled = false, triggers = listOf(item("screen_on")), actions = listOf(item("toast", "text" to "two"))),
            Macro(id = 3, name = "other", triggers = listOf(item("screen_off")), actions = listOf(item("toast", "text" to "three"))),
        )
        executor.onEvent(MacroEvent("screen_on"))
        assertEquals(listOf("toast:one"), device.calls)
    }

    @Test fun aTickIsHandledOncePerMinute() = runTest {
        val (executor, _) = setup(
            Macro(id = 1, name = "m", triggers = listOf(item("interval", "minutes" to "1")), actions = listOf(item("toast", "text" to "x"))),
        )
        val t = device.time
        executor.onEvent(MacroEvent("tick", time = t.withSecond(5)))
        executor.onEvent(MacroEvent("tick", time = t.withSecond(25)))
        executor.onEvent(MacroEvent("tick", time = t.withSecond(45)))
        assertEquals(1, device.calls.size)
        executor.onEvent(MacroEvent("tick", time = t.plusMinutes(1)))
        assertEquals(2, device.calls.size)
    }

    @Test fun askAiStoresTheAnswerInTheNamedVariableAndInAiResponse() = runTest {
        val (executor, _) = setup(
            Macro(
                id = 1, name = "m",
                actions = listOf(
                    item("ask_ai", "prompt" to "What is {battery} + 1?", "variable" to "answer"),
                    item("toast", "text" to "{answer}/{ai_response}"),
                ),
            ),
        )
        assertEquals(RunOutcome.Completed, executor.run(1, "manual"))
        assertEquals(listOf("ai:What is 55 + 1?", "toast:42/42"), device.calls)
    }

    @Test fun macroEnabledConstraintLooksAtTheOtherMacro() = runTest {
        val (executor, _) = setup(
            Macro(id = 1, name = "a", actions = listOf(item("toast", "text" to "x")), constraints = listOf(item("c_macro_enabled", "name" to "b"))),
            Macro(id = 2, name = "b", enabled = false),
        )
        assertEquals(RunOutcome.ConstraintsNotMet, executor.run(1, "manual"))
    }
}
