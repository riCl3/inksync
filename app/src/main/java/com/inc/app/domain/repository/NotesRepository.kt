package com.inc.app.domain.repository

import com.inc.app.domain.model.Folder
import com.inc.app.domain.model.Note
import kotlinx.coroutines.flow.Flow

/**
 * The single API the UI layer talks to for note data.
 *
 * Why an interface with one implementation today? Because two genuinely
 * different implementations will live behind it over the project's life: the
 * in-memory source (Milestone 1) and Room as the local source of truth
 * (Milestone 2) that also records pending sync operations in the same
 * transaction. ViewModels never know which one they hold; tests inject fakes.
 */
interface NotesRepository {
    /** Reactive read: emits the full note list whenever anything changes. */
    fun observeNotes(): Flow<List<Note>>

    fun observeFolders(): Flow<List<Folder>>

    suspend fun getNote(id: String): Note?

    /** Creates a note and returns its id. */
    suspend fun createNote(title: String, content: String, folderId: String? = null): String

    suspend fun updateNote(id: String, title: String, content: String)

    suspend fun setPinned(id: String, pinned: Boolean)

    suspend fun deleteNote(id: String)
}
