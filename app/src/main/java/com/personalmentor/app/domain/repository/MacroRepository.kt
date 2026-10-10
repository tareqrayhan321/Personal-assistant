package com.personalmentor.app.domain.repository

import com.personalmentor.app.domain.macro.Macro
import kotlinx.coroutines.flow.StateFlow

interface MacroRepository {
    val macros: StateFlow<List<Macro>>

    fun get(id: Long): Macro? = macros.value.firstOrNull { it.id == id }

    fun findByName(name: String): Macro? =
        macros.value.firstOrNull { it.name.trim().equals(name.trim(), ignoreCase = true) }

    /** Inserts (id 0 gets a new id) or replaces; returns the stored macro. */
    fun save(macro: Macro): Macro

    fun delete(id: Long)

    /** Remembers when a macro last ran and whether it failed. */
    fun recordRun(id: Long, at: Long, error: String?)
}
