package com.personalmentor.app.data.macro

import android.content.Context
import com.personalmentor.app.domain.macro.Macro
import com.personalmentor.app.domain.repository.MacroRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Macros as one small JSON file in the app's private storage (a few dozen macros at most, so no database
 * migration is needed). Every change is written atomically and then keeps the background service in step.
 */
@Singleton
class JsonMacroRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val json: Json,
) : MacroRepository {

    @Serializable
    private data class Store(val nextId: Long = 1, val macros: List<Macro> = emptyList())

    private val file = File(context.filesDir, "macros.json")
    private var store = load()
    private val state = MutableStateFlow(store.macros)
    override val macros: StateFlow<List<Macro>> = state.asStateFlow()

    @Synchronized
    override fun save(macro: Macro): Macro {
        val stored = if (macro.id == 0L) macro.copy(id = store.nextId) else macro
        val list = if (store.macros.any { it.id == stored.id }) {
            store.macros.map { if (it.id == stored.id) stored else it }
        } else {
            store.macros + stored
        }
        commit(store.copy(nextId = maxOf(store.nextId, stored.id + 1), macros = list))
        return stored
    }

    @Synchronized
    override fun delete(id: Long) {
        commit(store.copy(macros = store.macros.filterNot { it.id == id }))
    }

    @Synchronized
    override fun recordRun(id: Long, at: Long, error: String?) {
        if (store.macros.none { it.id == id }) return
        commit(store.copy(macros = store.macros.map { if (it.id == id) it.copy(lastRunAt = at, lastError = error) else it }))
    }

    private fun commit(next: Store) {
        store = next
        state.value = next.macros
        runCatching {
            val tmp = File(file.parentFile, "macros.json.tmp")
            tmp.writeText(json.encodeToString(Store.serializer(), next))
            if (!tmp.renameTo(file)) {
                file.delete()
                tmp.renameTo(file)
            }
        }
        MacroServiceController.sync(context, next.macros)
    }

    private fun load(): Store = runCatching {
        if (file.exists()) json.decodeFromString(Store.serializer(), file.readText()) else Store()
    }.getOrDefault(Store())
}
