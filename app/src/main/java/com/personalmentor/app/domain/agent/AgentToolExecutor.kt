package com.personalmentor.app.domain.agent

import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import javax.inject.Inject

/**
 * Single entry point for every tool of Agent Mode: browser (`browser_*`), GitHub (`github_*`) and the
 * existing task tools. Always returns a JSON string (`{"ok":true,...}` or `{"ok":false,"error":"..."}`).
 */
class AgentToolExecutor @Inject constructor(
    private val tasks: TaskToolExecutor,
    private val browser: BrowserToolExecutor,
    private val github: GitHubToolExecutor,
    private val macros: MacroToolExecutor,
    private val json: Json,
) {
    suspend fun execute(name: String, argumentsJson: String): String {
        if (!name.startsWith("browser_") && !name.startsWith("github_") && !name.startsWith("macro_")) {
            return tasks.execute(name, argumentsJson)
        }
        return try {
            val args = parse(argumentsJson)
            when {
                name.startsWith("browser_") -> browser.execute(name, args)
                name.startsWith("macro_") -> macros.execute(name, args)
                else -> github.execute(name, args)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            toolFail(e.message ?: "Tool call failed")
        }
    }

    /** Short, human-readable line for the chat, e.g. `browser_open · https://github.com`. */
    fun describe(name: String, argumentsJson: String): String {
        val args = runCatching { parse(argumentsJson) }.getOrNull() ?: return name
        val hint = HINT_KEYS.firstNotNullOfOrNull { key -> args.str(key)?.takeIf { it.isNotBlank() } }
            ?: args.long("id")?.let { "#$it" }
        return if (hint == null) name else "$name · ${hint.replace('\n', ' ').take(HINT_LENGTH)}"
    }

    private fun parse(argumentsJson: String): JsonObject =
        if (argumentsJson.isBlank()) JsonObject(emptyMap()) else json.parseToJsonElement(argumentsJson).jsonObject

    private companion object {
        val HINT_KEYS = listOf("url", "query", "repo", "title", "path", "direction", "name")
        const val HINT_LENGTH = 60
    }
}
