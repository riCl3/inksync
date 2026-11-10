@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.inc.app.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.inc.app.domain.model.Note
import com.inc.app.domain.repository.NotesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

data class SearchUiState(
    val query: String = "",
    val results: List<Note> = emptyList(),
    val hasSearched: Boolean = false,
)

@HiltViewModel
class SearchViewModel @Inject constructor(
    private val repository: NotesRepository,
) : ViewModel() {

    private val query = MutableStateFlow("")

    val uiState: StateFlow<SearchUiState> = query.flatMapLatest { q ->
        if (q.isBlank()) {
            flowOf(SearchUiState(query = q, results = emptyList(), hasSearched = false))
        } else {
            repository.searchNotes(q).map { results ->
                SearchUiState(query = q, results = results, hasSearched = true)
            }
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = SearchUiState(),
    )

    fun onQueryChange(value: String) {
        query.value = value
    }
}
