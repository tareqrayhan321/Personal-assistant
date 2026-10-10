package com.personalmentor.app.presentation.macro

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.personalmentor.app.domain.macro.Macro
import com.personalmentor.app.domain.macro.MacroExecutor
import com.personalmentor.app.domain.macro.MacroItem
import com.personalmentor.app.domain.macro.MacroSection
import com.personalmentor.app.domain.macro.MacroVariable
import com.personalmentor.app.domain.macro.RunOutcome
import com.personalmentor.app.domain.repository.MacroRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MacroEditorViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: MacroRepository,
    private val executor: MacroExecutor,
) : ViewModel() {

    private val original: Macro? = (savedStateHandle.get<Long>("id") ?: 0L).takeIf { it != 0L }?.let { repository.get(it) }
    private val _macro = MutableStateFlow(original ?: Macro())
    val macro: StateFlow<Macro> = _macro.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    /** Categories already in use, offered when choosing a category. */
    val categories: List<String>
        get() = (repository.macros.value.map { it.category } + Macro.DEFAULT_CATEGORY).distinct().sorted()

    fun messageShown() { _message.value = null }

    fun rename(name: String) = _macro.update { it.copy(name = name) }
    fun setNotes(notes: String) = _macro.update { it.copy(notes = notes) }
    fun setCategory(category: String) = _macro.update { it.copy(category = category.trim().ifEmpty { Macro.DEFAULT_CATEGORY }) }

    fun addItem(section: MacroSection, item: MacroItem) = _macro.update { it.withItems(section, it.items(section) + item) }

    fun replaceItem(section: MacroSection, index: Int, item: MacroItem) = _macro.update { m ->
        m.withItems(section, m.items(section).mapIndexed { i, old -> if (i == index) item else old })
    }

    fun removeItem(section: MacroSection, index: Int) = _macro.update { m ->
        m.withItems(section, m.items(section).filterIndexed { i, _ -> i != index })
    }

    /** Returns an error text, or null when the variable was added. */
    fun addVariable(name: String, value: String): String? {
        val clean = name.trim()
        if (!VARIABLE.matches(clean)) return "Use letters, digits and _ (max 30)"
        if (_macro.value.variables.any { it.name == clean }) return "That variable already exists"
        _macro.update { it.copy(variables = it.variables + MacroVariable(clean, value)) }
        return null
    }

    fun removeVariable(name: String) = _macro.update { m -> m.copy(variables = m.variables.filterNot { it.name == name }) }

    /** Saves unless nothing was entered or nothing changed. Returns the stored macro, if any. */
    fun persist(): Macro? {
        val m = _macro.value
        val isEmpty = m.name.isBlank() && m.triggers.isEmpty() && m.actions.isEmpty() && m.constraints.isEmpty()
        if (m.id == 0L && isEmpty) return null
        if (m == original) return m
        if (m.id != 0L && m == repository.get(m.id)) return m
        var name = m.name.trim().ifEmpty { "Untitled macro" }
        var n = 2
        while (repository.findByName(name)?.takeIf { it.id != m.id } != null) name = "${m.name.trim().ifEmpty { "Untitled macro" }} ${n++}"
        val saved = repository.save(m.copy(name = name, editedAt = System.currentTimeMillis()))
        _macro.value = saved
        return saved
    }

    fun delete() {
        _macro.value.id.takeIf { it != 0L }?.let { repository.delete(it) }
    }

    /** Saves, then runs the macro once ignoring its triggers (constraints still apply). */
    fun testRun() {
        viewModelScope.launch {
            val saved = persist()
            if (saved == null || saved.actions.isEmpty()) {
                _message.value = "Add at least one action first"
                return@launch
            }
            _message.value = when (val outcome = executor.run(saved.id, "manual")) {
                RunOutcome.Completed -> "Finished"
                RunOutcome.Stopped -> "Stopped by a Stop Macro action"
                RunOutcome.ConstraintsNotMet -> "Skipped: constraints are not met right now"
                RunOutcome.AlreadyRunning -> "Already running"
                RunOutcome.NotFound -> "Macro not found"
                is RunOutcome.Failed -> "Failed: ${outcome.message}"
            }
        }
    }

    private companion object {
        val VARIABLE = Regex("^[A-Za-z0-9_]{1,30}$")
    }
}
