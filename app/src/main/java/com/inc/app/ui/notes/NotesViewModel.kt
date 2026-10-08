package com.inc.app.ui.notes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.inc.app.domain.model.Note
import com.inc.app.domain.model.SyncState
import com.inc.app.domain.model.SyncStatus
import com.inc.app.domain.repository.NotesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * UI state for the notes home screen.
 *
 * The screen never touches the repository directly — it renders this immutable
 * snapshot. Every change (create/edit/pin/delete) flows back through the
 * repository, which emits a new list, which [NotesViewModel] maps into a new
 * state object, which Compose recomposes from. That loop is the "unidirectional
 * data flow" part of the architecture.
 */
data class NotesUiState(
    val pinned: List<Note> = emptyList(),
    val notes: List<Note> = emptyList(),
    val syncStatus: SyncStatus = SyncStatus.PENDING,
    val isLoading: Boolean = true,
) {
    val isEmpty: Boolean get() = pinned.isEmpty() && notes.isEmpty()
}

@HiltViewModel
class NotesViewModel @Inject constructor(
    private val repository: NotesRepository,
) : ViewModel() {

    /**
     * `stateIn(WhileSubscribed(5s))`: the upstream Flow is collected only while
     * the screen is visible (survives rotation without re-querying, stops when
     * the user leaves — no leaks, no wasted work).
     *
     * The sync status here is aggregated from per-note states: anything not
     * SYNCED means the account has work waiting. In Milestone 4+ this is
     * replaced by real connectivity + worker state, but the UI contract
     * (a single [SyncStatus] to render) is already final.
     */
    val uiState: StateFlow<NotesUiState> = repository.observeNotes()
        .map { notes ->
            val sorted = notes.sortedByDescending { it.updatedAt }
            NotesUiState(
                pinned = sorted.filter { it.isPinned },
                notes = sorted.filterNot { it.isPinned },
                syncStatus = if (notes.any { it.syncState != SyncState.SYNCED }) {
                    SyncStatus.PENDING
                } else {
                    SyncStatus.SYNCED
                },
                isLoading = false,
            )
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = NotesUiState(),
        )

    fun setPinned(id: String, pinned: Boolean) {
        viewModelScope.launch { repository.setPinned(id, pinned) }
    }

    fun deleteNote(id: String) {
        viewModelScope.launch { repository.deleteNote(id) }
    }
}
