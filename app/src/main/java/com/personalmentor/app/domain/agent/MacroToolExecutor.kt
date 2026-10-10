package com.personalmentor.app.domain.agent

import com.personalmentor.app.domain.macro.Macro
import com.personalmentor.app.domain.macro.MacroCatalog
import com.personalmentor.app.domain.macro.MacroExecutor
import com.personalmentor.app.domain.macro.MacroItem
import com.personalmentor.app.domain.macro.MacroSection
import com.personalmentor.app.domain.macro.RunOutcome
import com.personalmentor.app.domain.repository.MacroRepository
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put
import javax.inject.Inject

object MacroTools {
    const val LIST = "macro_list"
    const val CREATE = "macro_create"
    const val RUN = "macro_run"
    const val SET_ENABLED = "macro_set_enabled"
    const val DELETE = "macro_delete"
    val ALL = listOf(LIST, CREATE, RUN, SET_ENABLED, DELETE)
}

/** Lets the agent build and run the user's automations. Anything that changes or runs a macro asks first. */
class MacroToolExecutor @Inject constructor(
    private val repository: MacroRepository,
    private val executor: MacroExecutor,
    private val approver: ActionApprover,
) {
    suspend fun execute(name: String, args: JsonObject): String = when (name) {
        MacroTools.LIST -> list()
        MacroTools.CREATE -> create(args)
        MacroTools.RUN -> run(args)
        MacroTools.SET_ENABLED -> setEnabled(args)
        MacroTools.DELETE -> delete(args)
        else -> toolFail("Unknown tool: $name")
    }

    private fun list(): String = toolOk {
        put("macros", JsonArray(repository.macros.value.map { m ->
            buildJsonObject {
                put("name", m.name)
                put("enabled", m.enabled)
                put("triggers", JsonArray(m.triggers.map { JsonPrimitive(MacroCatalog.summary(it)) }))
                put("actions", JsonArray(m.actions.map { JsonPrimitive(MacroCatalog.summary(it)) }))
                put("constraints", JsonArray(m.constraints.map { JsonPrimitive(MacroCatalog.summary(it)) }))
                m.lastRunAt?.let { put("last_run_at", it) }
                m.lastError?.let { put("last_error", it) }
            }
        }))
    }

    private suspend fun create(args: JsonObject): String {
        val name = args.str("name")?.trim().orEmpty()
        if (name.isEmpty()) return toolFail("name is required")
        if (repository.findByName(name) != null) return toolFail("A macro called \"$name\" already exists.")
        val triggers = parseItems(args["triggers"], MacroSection.TRIGGER).getOrElse { return toolFail(it.message.orEmpty()) }
        val actions = parseItems(args["actions"], MacroSection.ACTION).getOrElse { return toolFail(it.message.orEmpty()) }
        val constraints = parseItems(args["constraints"], MacroSection.CONSTRAINT).getOrElse { return toolFail(it.message.orEmpty()) }
        if (triggers.isEmpty()) return toolFail("At least one trigger is required (use manual for a run-only macro).")
        if (actions.isEmpty()) return toolFail("At least one action is required.")

        val detail = buildString {
            append(name)
            triggers.forEach { append("\nWhen: ").append(MacroCatalog.summary(it)) }
            constraints.forEach { append("\nOnly if: ").append(MacroCatalog.summary(it)) }
            actions.forEach { append("\nThen: ").append(MacroCatalog.summary(it)) }
        }
        if (!approver.confirm("Create macro", detail, ApprovalLevel.SENSITIVE)) return toolFail(DECLINED_MESSAGE)
        val saved = repository.save(
            Macro(
                name = name,
                category = args.str("category")?.trim()?.takeIf { it.isNotEmpty() } ?: Macro.DEFAULT_CATEGORY,
                triggers = triggers, actions = actions, constraints = constraints,
                editedAt = System.currentTimeMillis(),
            ),
        )
        return toolOk { put("created", saved.name) }
    }

    private suspend fun run(args: JsonObject): String {
        val macro = findMacro(args) ?: return toolFail(NOT_FOUND)
        val detail = macro.name + macro.actions.joinToString("") { "\nThen: " + MacroCatalog.summary(it) }
        if (!approver.confirm("Run macro", detail, ApprovalLevel.SENSITIVE)) return toolFail(DECLINED_MESSAGE)
        return when (val outcome = executor.run(macro.id, "agent")) {
            RunOutcome.Completed -> toolOk { put("result", "completed") }
            RunOutcome.Stopped -> toolOk { put("result", "stopped by a Stop Macro action") }
            RunOutcome.ConstraintsNotMet -> toolOk { put("result", "skipped: its constraints are not met right now") }
            RunOutcome.AlreadyRunning -> toolFail("That macro is already running.")
            RunOutcome.NotFound -> toolFail(NOT_FOUND)
            is RunOutcome.Failed -> toolFail(outcome.message)
        }
    }

    private suspend fun setEnabled(args: JsonObject): String {
        val macro = findMacro(args) ?: return toolFail(NOT_FOUND)
        val enabled = args.flag("enabled") ?: return toolFail("enabled (true/false) is required")
        val verb = if (enabled) "Enable" else "Disable"
        if (!approver.confirm("$verb macro", macro.name, ApprovalLevel.SENSITIVE)) return toolFail(DECLINED_MESSAGE)
        repository.save(macro.copy(enabled = enabled))
        return toolOk { put("enabled", enabled) }
    }

    private suspend fun delete(args: JsonObject): String {
        val macro = findMacro(args) ?: return toolFail(NOT_FOUND)
        if (!approver.confirm("Delete macro", macro.name, ApprovalLevel.SENSITIVE)) return toolFail(DECLINED_MESSAGE)
        repository.delete(macro.id)
        return toolOk { put("deleted", macro.name) }
    }

    private fun findMacro(args: JsonObject): Macro? = args.str("name")?.let { repository.findByName(it) }

    /** Items from `[{"type":"...","params":{...}}]`; missing params take the catalog default. */
    private fun parseItems(element: JsonElement?, section: MacroSection): Result<List<MacroItem>> {
        if (element == null) return Result.success(emptyList())
        val array = element as? JsonArray ?: return Result.failure(IllegalArgumentException("${section.title.lowercase()} must be an array"))
        val items = mutableListOf<MacroItem>()
        for (entry in array) {
            val obj = entry as? JsonObject ?: return Result.failure(IllegalArgumentException("Each ${section.singular.lowercase()} must be an object with type and params"))
            val type = obj.str("type").orEmpty()
            val def = MacroCatalog.find(type)
                ?: return Result.failure(IllegalArgumentException("Unknown ${section.singular.lowercase()} type \"$type\""))
            val given = (obj["params"] as? JsonObject).orEmpty().mapValues { (_, v) -> (v as? JsonPrimitive)?.contentOrNull.orEmpty() }
            val params = def.params.associate { p -> p.key to (given[p.key] ?: p.default) }
            val item = MacroItem(type, params)
            MacroCatalog.validate(item, section)?.let { return Result.failure(IllegalArgumentException("${def.label}: $it")) }
            items += item
        }
        return Result.success(items)
    }

    private fun JsonObject?.orEmpty(): JsonObject = this ?: JsonObject(emptyMap())

    private companion object {
        const val NOT_FOUND = "No macro with that name. Use macro_list to see the names."
    }
}
