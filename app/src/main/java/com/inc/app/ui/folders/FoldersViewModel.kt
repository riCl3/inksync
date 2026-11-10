package com.inc.app.ui.folders

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.inc.app.domain.model.Folder
import com.inc.app.domain.repository.NotesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class FoldersUiState(
    val folders: List<Folder> = emptyList(),
    val isLoading: Boolean = true,
)

@HiltViewModel
class FoldersViewModel @Inject constructor(
    private val repository: NotesRepository,
) : ViewModel() {
    val uiState: StateFlow<FoldersUiState> = repository.observeFolders()
        .map { FoldersUiState(folders = it, isLoading = false) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), FoldersUiState())

    fun createFolder(name: String) {
        viewModelScope.launch { repository.createFolder(name) }
    }

    fun renameFolder(id: String, name: String) {
        viewModelScope.launch { repository.renameFolder(id, name) }
    }

    fun deleteFolder(id: String) {
        viewModelScope.launch { repository.deleteFolder(id) }
    }
}
