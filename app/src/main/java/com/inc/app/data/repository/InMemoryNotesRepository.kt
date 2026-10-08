package com.inc.app.data.repository

import com.inc.app.domain.model.Folder
import com.inc.app.domain.model.Note
import com.inc.app.domain.model.SyncState
import com.inc.app.domain.repository.NotesRepository
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/**
 * Milestone 1 stand-in for Room.
 *
 * A [MutableStateFlow] playing the role the database will play: an in-process
 * source of truth that emits a new immutable list on every change, so the
 * repository contract (`Flow<List<Note>>`) is already final. Swapping this for
 * Room in Milestone 2 touches only the Hilt binding — no ViewModel changes.
 *
 * Every mutation goes through one atomic `update`, mirroring how Room
 * transactions will later guarantee "note updated + sync op recorded" happens
 * as a single unit.
 */
@Singleton
class InMemoryNotesRepository @Inject constructor() : NotesRepository {

    private val notes = MutableStateFlow<List<Note>>(emptyList())
    private val folders = MutableStateFlow<List<Folder>>(emptyList())

    override fun observeNotes(): Flow<List<Note>> =
        notes.map { list -> list.filter { it.deletedAt == null } }

    override fun observeFolders(): Flow<List<Folder>> = folders

    override suspend fun getNote(id: String): Note? =
        notes.value.firstOrNull { it.id == id && it.deletedAt == null }

    override suspend fun createNote(title: String, content: String, folderId: String?): String {
        val now = System.currentTimeMillis()
        val note = Note(
            id = UUID.randomUUID().toString(),
            userId = "local-user",
            title = title,
            content = content,
            folderId = folderId,
            isPinned = false,
            createdAt = now,
            updatedAt = now,
            version = 1,
            syncState = SyncState.PENDING,
            deletedAt = null,
        )
        notes.update { it + note }
        return note.id
    }

    override suspend fun updateNote(id: String, title: String, content: String) {
        notes.update { list ->
            list.map { note ->
                if (note.id == id && note.deletedAt == null) {
                    note.copy(
                        title = title,
                        content = content,
                        updatedAt = System.currentTimeMillis(),
                        version = note.version + 1,
                        syncState = SyncState.PENDING,
                    )
                } else {
                    note
                }
            }
        }
    }

    override suspend fun setPinned(id: String, pinned: Boolean) {
        notes.update { list ->
            list.map { note ->
                if (note.id == id && note.isPinned != pinned) {
                    note.copy(
                        isPinned = pinned,
                        updatedAt = System.currentTimeMillis(),
                        version = note.version + 1,
                        syncState = SyncState.PENDING,
                    )
                } else {
                    note
                }
            }
        }
    }

    override suspend fun deleteNote(id: String) {
        // Soft delete: the row survives locally (with deletedAt set) until a
        // sync worker delivers the DELETE to the server, then it is purged.
        // Milestone 1 has no sync worker yet, so the row is removed from the
        // visible list immediately and purged here for simplicity.
        notes.update { list ->
            list.filterNot { it.id == id }
        }
    }
}
