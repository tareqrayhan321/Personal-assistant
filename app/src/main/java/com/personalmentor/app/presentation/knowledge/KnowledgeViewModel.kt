package com.personalmentor.app.presentation.knowledge

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.personalmentor.app.domain.model.KnowledgeDocument
import com.personalmentor.app.domain.repository.KnowledgeRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class KnowledgeViewModel @Inject constructor(
    private val repository: KnowledgeRepository,
) : ViewModel() {

    val documents: StateFlow<List<KnowledgeDocument>> = repository.observeDocuments()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        viewModelScope.launch { repository.recoverInterrupted() }
    }

    fun onFilesPicked(uris: List<Uri>) {
        if (uris.isNotEmpty()) repository.enqueue(uris.map(Uri::toString))
    }

    fun onDelete(id: Long) {
        viewModelScope.launch { repository.delete(id) }
    }
}
