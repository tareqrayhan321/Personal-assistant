package com.personalmentor.app.domain.macro

import kotlinx.serialization.Serializable

enum class MacroSection(val title: String, val singular: String) {
    TRIGGER("Triggers", "Trigger"),
    ACTION("Actions", "Action"),
    CONSTRAINT("Constraints", "Constraint"),
}

/** One trigger, action or constraint: a [type] id from [MacroCatalog] plus its parameter values. */
@Serializable
data class MacroItem(val type: String, val params: Map<String, String> = emptyMap())

@Serializable
data class MacroVariable(val name: String, val value: String = "")

/**
 * An automation: when any trigger fires and all constraints hold, the actions run in order.
 * Stored as JSON, so every field needs a default to keep old files readable.
 */
@Serializable
data class Macro(
    val id: Long = 0,
    val name: String = "",
    val enabled: Boolean = true,
    val favorite: Boolean = false,
    val category: String = DEFAULT_CATEGORY,
    val notes: String = "",
    val triggers: List<MacroItem> = emptyList(),
    val actions: List<MacroItem> = emptyList(),
    val constraints: List<MacroItem> = emptyList(),
    val variables: List<MacroVariable> = emptyList(),
    val lastRunAt: Long? = null,
    val lastError: String? = null,
    val editedAt: Long = 0,
) {
    fun items(section: MacroSection): List<MacroItem> = when (section) {
        MacroSection.TRIGGER -> triggers
        MacroSection.ACTION -> actions
        MacroSection.CONSTRAINT -> constraints
    }

    fun withItems(section: MacroSection, items: List<MacroItem>): Macro = when (section) {
        MacroSection.TRIGGER -> copy(triggers = items)
        MacroSection.ACTION -> copy(actions = items)
        MacroSection.CONSTRAINT -> copy(constraints = items)
    }

    companion object {
        const val DEFAULT_CATEGORY = "Uncategorized"
    }
}
