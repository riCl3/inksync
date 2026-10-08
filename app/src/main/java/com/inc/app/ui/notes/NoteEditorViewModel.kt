package com.inc.app.ui.notes

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.inc.app.domain.repository.NotesRepository
import com.inc.app.ui.navigation.Routes
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Editor state. `isNew` until the first real input creates the row, so opening
 * a new note and typing nothing leaves no empty note behind.
 */
data class EditorUiState(
    val noteId: String? = null,
    val title: String = "",
    val content: String = "",
    val isPinned: Boolean = false,
    val isNew: Boolean = true,
    val notFound: Boolean = false,
    val updatedAt: Long? = null,
)

/**
 * Write-through editor model.
 *
 * Concept (important for the offline-first story): there is no explicit
 * "Save" button and no draft kept only in memory. Every keystroke updates the
 * local source of truth through the repository, so the data survives process
 * death, is immediately visible on the home screen, and — from Milestone 5 on —
 * automatically produces a pending sync operation.
 *
 * The note id comes from [SavedStateHandle] (the navigation route), so the
 * editor also restores correctly after the system kills the process.
 */
@HiltViewModel
class NoteEditorViewModel @Inject constructor(
    private val repository: NotesRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val routeId: String? = savedStateHandle[Routes.NOTE_ARG]

    private val _uiState = MutableStateFlow(EditorUiState())
    val uiState: StateFlow<EditorUiState> = _uiState.asStateFlow()

    private var persistedId: String? = null

    init {
        if (routeId == null || routeId == Routes.NEW_NOTE) {
            _uiState.update { it.copy(isNew = true) }
        } else {
            viewModelScope.launch {
                val note = repository.getNote(routeId)
                if (note == null) {
                    _uiState.update { it.copy(notFound = true) }
                } else {
                    persistedId = note.id
                    _uiState.update {
                        EditorUiState(
                            noteId = note.id,
                            title = note.title,
                            content = note.content,
                            isPinned = note.isPinned,
                            isNew = false,
                            updatedAt = note.updatedAt,
                        )
                    }
                }
            }
        }
    }

    fun onTitleChange(value: String) {
        _uiState.update { it.copy(title = value) }
        persist()
    }

    fun onContentChange(value: String) {
        _uiState.update { it.copy(content = value) }
        persist()
    }

    fun togglePinned() {
        val pinned = !_uiState.value.isPinned
        _uiState.update { it.copy(isPinned = pinned) }
        val id = persistedId ?: return
        viewModelScope.launch { repository.setPinned(id, pinned) }
    }

    fun delete() {
        val id = persistedId ?: return
        viewModelScope.launch { repository.deleteNote(id) }
    }

    /**
     * Writes the current state through to the repository. Each call re-reads
     * the *latest* state when it executes, so bursts of keystrokes can never
     * write stale text — last write wins with the newest value. From Milestone
     * 5 on this same call site also records the pending sync operation in one
     * atomic transaction.
     */
    private fun persist() {
        viewModelScope.launch {
            val state = _uiState.value
            val id = persistedId
            when {
                id == null -> {
                    // Don't create a row until there is something to save.
                    if (state.title.isBlank() && state.content.isBlank()) return@launch
                    val newId = repository.createNote(state.title, state.content)
                    persistedId = newId
                    _uiState.update {
                        it.copy(noteId = newId, isNew = false)
                    }
                }
                else -> repository.updateNote(id, state.title, state.content)
            }
        }
    }
}
