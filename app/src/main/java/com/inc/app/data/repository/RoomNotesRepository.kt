package com.inc.app.data.repository

import com.inc.app.data.local.FolderDao
import com.inc.app.data.local.NoteDao
import com.inc.app.data.local.NoteEntity
import com.inc.app.data.local.NoteFtsDao
import com.inc.app.data.local.FolderEntity
import com.inc.app.data.local.IncSyncDatabase
import com.inc.app.data.local.PendingOpDao
import com.inc.app.data.local.PendingOpEntity
import com.inc.app.data.remote.IncSyncApi
import com.inc.app.data.remote.TokenStore
import com.inc.app.data.sync.SyncScheduler
import com.inc.app.data.local.toDomain
import com.inc.app.domain.model.Folder
import com.inc.app.domain.model.Note
import com.inc.app.domain.model.SyncState
import com.inc.app.domain.repository.NotesRepository
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.map

/**
 * Room-backed implementation: the local source of truth.
 *
 * The repository contract did NOT change from Milestone 1 — only this class
 * and one Hilt binding did. That is the payoff of having introduced the
 * interface where a second implementation genuinely existed.
 *
 * Responsibilities kept here (not in DAOs, not in ViewModels):
 *  - mapping entity ↔ domain at the boundary (persistence types never leak
 *    into the UI),
 *  - stamping rows with server-facing fields (userId placeholder until
 *    Milestone 3 auth, `version`, `syncState = PENDING` on change).
 *
 * Note what the DAO already guarantees: every mutation is a single SQL
 * statement, so "note updated" and "marked pending" cannot be observed
 * separately. In Milestone 5 the same repository methods wrap note-write +
 * sync-operation-write in one Room transaction — the seam already exists.
 */
@Singleton
class RoomNotesRepository @Inject constructor(
    private val noteDao: NoteDao,
    private val api: IncSyncApi,
    private val noteFtsDao: NoteFtsDao,
    private val folderDao: FolderDao,
    private val pendingOpDao: PendingOpDao,
    private val tokenStore: TokenStore,
    private val db: IncSyncDatabase,
    private val syncScheduler: SyncScheduler,
) : NotesRepository {

    private val currentUserId: String
        get() = tokenStore.userId.value ?: "local-user"

    override fun observeNotes(): Flow<List<Note>> =
        noteDao.observeVisible().map { entities -> entities.map { it.toDomain() } }

    override fun observeFolders(): Flow<List<Folder>> =
        folderDao.observeAll().map { entities -> entities.map { it.toDomain() } }

    override fun searchNotes(query: String): Flow<List<Note>> =
        if (query.isBlank()) kotlinx.coroutines.flow.flowOf(emptyList())
        else kotlinx.coroutines.flow.flow {
            try {
                emitAll(noteFtsDao.search(query).map { entities -> entities.map { it.toDomain() } })
            } catch (e: Exception) {
                emit(emptyList())
            }
        }

    override suspend fun getNote(id: String): Note? =
        noteDao.getById(id)?.takeIf { it.deletedAt == null }?.toDomain()

    override suspend fun createNote(title: String, content: String, folderId: String?): String {
        val now = System.currentTimeMillis()
        val id = UUID.randomUUID().toString()
        db.withTransaction {
            noteDao.upsert(
                NoteEntity(
                    id = id,
                    userId = currentUserId,
                    title = title,
                    content = content,
                    folderId = folderId,
                    isPinned = false,
                    createdAt = now,
                    updatedAt = now,
                    version = 1,
                    syncState = SyncState.PENDING.name,
                    deletedAt = null,
                ),
            )
            pendingOpDao.insert(
                PendingOpEntity(opType = "CREATE", noteId = id, payload = null, createdAt = now),
            )
        }
        syncScheduler.schedule()
        return id
    }

    override suspend fun updateNote(id: String, title: String, content: String) {
        val now = System.currentTimeMillis()
        db.withTransaction {
            noteDao.updateContent(id = id, title = title, content = content, updatedAt = now)
            pendingOpDao.insert(
                PendingOpEntity(opType = "UPDATE", noteId = id, payload = null, createdAt = now),
            )
        }
        syncScheduler.schedule()
    }

    override suspend fun setPinned(id: String, pinned: Boolean) {
        val now = System.currentTimeMillis()
        db.withTransaction {
            noteDao.setPinned(id = id, pinned = pinned, updatedAt = now)
            pendingOpDao.insert(
                PendingOpEntity(opType = "UPDATE", noteId = id, payload = null, createdAt = now),
            )
        }
        syncScheduler.schedule()
    }

    override suspend fun deleteNote(id: String) {
        val now = System.currentTimeMillis()
        db.withTransaction {
            noteDao.delete(id)
            pendingOpDao.insert(
                PendingOpEntity(opType = "DELETE", noteId = id, payload = null, createdAt = now),
            )
        }
        syncScheduler.schedule()
    }

    override suspend fun createFolder(name: String): String {
        val now = System.currentTimeMillis()
        val id = UUID.randomUUID().toString()
        db.withTransaction {
            folderDao.upsert(
                FolderEntity(
                    id = id,
                    userId = currentUserId,
                    name = name,
                    createdAt = now,
                    updatedAt = now,
                    version = 1,
                    syncState = SyncState.PENDING.name,
                    syncedVersion = 0,
                ),
            )
            pendingOpDao.insert(
                PendingOpEntity(opType = "FOLDER_CREATE", noteId = id, payload = null, createdAt = now),
            )
        }
        syncScheduler.schedule()
        return id
    }

    override suspend fun renameFolder(id: String, name: String) {
        val now = System.currentTimeMillis()
        db.withTransaction {
            val existing = folderDao.getById(id) ?: return@withTransaction
            folderDao.upsert(
                existing.copy(
                    name = name,
                    updatedAt = now,
                    version = existing.version + 1,
                    syncState = SyncState.PENDING.name,
                ),
            )
            pendingOpDao.insert(
                PendingOpEntity(opType = "FOLDER_RENAME", noteId = id, payload = null, createdAt = now),
            )
        }
        syncScheduler.schedule()
    }

    override suspend fun deleteFolder(id: String) {
        val now = System.currentTimeMillis()
        db.withTransaction {
            folderDao.delete(id)
            pendingOpDao.insert(
                PendingOpEntity(opType = "FOLDER_DELETE", noteId = id, payload = null, createdAt = now),
            )
        }
        syncScheduler.schedule()
    }

    override suspend fun resolveConflictKeepLocal(id: String) {
        val now = System.currentTimeMillis()
        db.withTransaction {
            val note = noteDao.getById(id) ?: return@withTransaction
            noteDao.upsert(
                note.copy(
                    syncState = SyncState.PENDING.name,
                    updatedAt = now,
                    version = note.version,
                ),
            )
            pendingOpDao.insert(
                PendingOpEntity(opType = "UPDATE", noteId = id, payload = null, createdAt = now),
            )
        }
        syncScheduler.schedule()
    }

    override suspend fun resolveConflictKeepServer(id: String) {
        val now = System.currentTimeMillis()
        try {
            val server = api.getNote(id)
            db.withTransaction {
                val note = noteDao.getById(id) ?: return@withTransaction
                noteDao.upsert(
                    note.copy(
                        title = server.title,
                        content = server.content,
                        folderId = server.folderId,
                        isPinned = server.isPinned,
                        updatedAt = now,
                        version = server.version,
                        syncState = SyncState.SYNCED.name,
                        syncedVersion = server.version,
                    ),
                )
            }
        } catch (e: Exception) {
            // Leave conflict state; user can retry.
        }
    }
}
