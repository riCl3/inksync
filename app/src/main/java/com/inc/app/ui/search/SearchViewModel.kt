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
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class SearchUiState(
    val query: String = "",
    val results: List<Note> = emptyList(),
    val hasSearched: Boolean = false,
)

/**
 * Offline search.
 *
 * Concept: search never touches the network. The query is combined with the
 * local note stream, so results update reactively as the user types AND are
 * correct even while other edits are happening elsewhere in the app.
 *
 * Milestone 1 filters in memory because the list fits in memory. From
 * Milestone 10 this same [SearchUiState] contract is fed by a Room FTS table —
 * the screen will not change, only the data source behind the repository.
 * (No debounce on the query: filtering is cheap at this scale, and debouncing
 * would make the text field itself lag behind the user's typing.)
 */
@HiltViewModel
class SearchViewModel @Inject constructor(
    private val repository: NotesRepository,
) : ViewModel() {

    private val query = MutableStateFlow("")

    val uiState: StateFlow<SearchUiState> = combine(
        query,
        repository.observeNotes(),
    ) { q, notes ->
        val filtered = if (q.isBlank()) {
            emptyList()
        } else {
            val needle = q.trim()
            notes
                .filter { note ->
                    note.title.contains(needle, ignoreCase = true) ||
                        note.content.contains(needle, ignoreCase = true)
                }
                .sortedByDescending { it.updatedAt }
        }
        SearchUiState(
            query = q,
            results = filtered,
            hasSearched = q.isNotBlank(),
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = SearchUiState(),
    )

    fun onQueryChange(value: String) {
        query.value = value
    }
}
