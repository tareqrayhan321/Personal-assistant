package com.personalmentor.app.presentation.macro

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.personalmentor.app.data.macro.MacroServiceController
import com.personalmentor.app.domain.macro.Macro
import com.personalmentor.app.domain.macro.MacroExecutor
import com.personalmentor.app.domain.macro.MacroTemplate
import com.personalmentor.app.domain.repository.MacroRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import javax.inject.Inject

enum class MacroFilter(val label: String) {
    ALL("All"), ENABLED("Enabled"), DISABLED("Disabled"), ERRORS("Errors"), RUNNING("Running"), FAVORITES("Favorites"),
}

data class MacrosUiState(
    val macros: List<Macro> = emptyList(),
    val running: Set<Long> = emptySet(),
    val filter: MacroFilter = MacroFilter.ALL,
    val query: String = "",
    val searching: Boolean = false,
    val collapsed: Set<String> = emptySet(),
    val log: List<String> = emptyList(),
) {
    /** Macros after the chip filter and the search box, grouped by category (alphabetical). */
    val groups: List<Pair<String, List<Macro>>>
        get() = macros
            .filter { m ->
                when (filter) {
                    MacroFilter.ALL -> true
                    MacroFilter.ENABLED -> m.enabled
                    MacroFilter.DISABLED -> !m.enabled
                    MacroFilter.ERRORS -> m.lastError != null
                    MacroFilter.RUNNING -> m.id in running
                    MacroFilter.FAVORITES -> m.favorite
                }
            }
            .filter { query.isBlank() || it.name.contains(query.trim(), ignoreCase = true) }
            .groupBy { it.category }
            .toSortedMap(String.CASE_INSENSITIVE_ORDER)
            .map { (category, list) -> category to list.sortedBy { it.name.lowercase() } }
}

@HiltViewModel
class MacrosViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: MacroRepository,
    executor: MacroExecutor,
) : ViewModel() {

    private data class Local(
        val filter: MacroFilter = MacroFilter.ALL,
        val query: String = "",
        val searching: Boolean = false,
        val collapsed: Set<String> = emptySet(),
    )

    private val local = MutableStateFlow(Local())

    val state: StateFlow<MacrosUiState> = combine(repository.macros, executor.running, executor.log, local) { macros, running, log, l ->
        MacrosUiState(macros, running, l.filter, l.query, l.searching, l.collapsed, log)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MacrosUiState())

    init {
        // Make sure the background listener is up whenever the user opens the macros.
        MacroServiceController.sync(context, repository.macros.value)
    }

    fun setFilter(filter: MacroFilter) = local.update { it.copy(filter = filter) }
    fun setQuery(query: String) = local.update { it.copy(query = query) }
    fun toggleSearch() = local.update { it.copy(searching = !it.searching, query = "") }

    fun toggleCollapsed(category: String) =
        local.update { it.copy(collapsed = if (category in it.collapsed) it.collapsed - category else it.collapsed + category) }

    fun collapseAll(collapse: Boolean) =
        local.update { it.copy(collapsed = if (collapse) repository.macros.value.map { m -> m.category }.toSet() else emptySet()) }

    fun setEnabled(id: Long, enabled: Boolean) {
        repository.get(id)?.let { repository.save(it.copy(enabled = enabled)) }
    }

    fun setCategoryEnabled(category: String, enabled: Boolean) {
        repository.macros.value.filter { it.category == category }.forEach { repository.save(it.copy(enabled = enabled)) }
    }

    fun toggleFavorite(id: Long) {
        repository.get(id)?.let { repository.save(it.copy(favorite = !it.favorite)) }
    }

    /** Adds a copy of the template under a free name and returns its id. */
    fun addTemplate(template: MacroTemplate): Long {
        var name = template.macro.name
        var n = 2
        while (repository.findByName(name) != null) name = "${template.macro.name} ${n++}"
        return repository.save(template.macro.copy(name = name, editedAt = System.currentTimeMillis())).id
    }
}
