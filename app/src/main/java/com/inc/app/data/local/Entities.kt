package com.inc.app.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.inc.app.domain.model.Folder
import com.inc.app.domain.model.Note
import com.inc.app.domain.model.SyncState

/**
 * Room table for notes.
 *
 * Schema decisions (Milestone 2):
 *  - Client-generated UUID primary key: offline-first means the row must be
 *    creatable before any server has ever seen it. Server-assigned ids would
 *    make offline creation impossible.
 *  - `version` (optimistic concurrency) and `syncState` live *with the data*,
 *    so a process kill can never separate a note from its sync state.
 *  - `deletedAt` exists from day one: once deletions must be synchronized
 *    (Milestone 5) the row has to survive locally until the server confirms.
 *    Milestone 2 still hard-deletes — one method to change later.
 *  - No framework annotations on the *domain* model: this entity maps to it
 *    at the repository boundary, keeping UI code free of persistence types.
 */
@Entity(tableName = "notes")
data class NoteEntity(
    @PrimaryKey
    val id: String,
    val userId: String,
    val title: String,
    val content: String,
    val folderId: String?,
    val isPinned: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
    /** Optimistic-concurrency token; incremented on every accepted edit. */
    val version: Int,
    /** Stored as the enum's name — explicit mapping, no converter magic. */
    val syncState: String,
    val deletedAt: Long?,
    /** Last version acknowledged by the server (0 = never synced). */
    val syncedVersion: Int = 0,
)

@Entity(tableName = "folders")
data class FolderEntity(
    @PrimaryKey
    val id: String,
    val userId: String,
    val name: String,
    val createdAt: Long,
    val updatedAt: Long,
)

fun NoteEntity.toDomain() = Note(
    id = id,
    userId = userId,
    title = title,
    content = content,
    folderId = folderId,
    isPinned = isPinned,
    createdAt = createdAt,
    updatedAt = updatedAt,
    version = version,
    syncState = SyncState.valueOf(syncState),
    deletedAt = deletedAt,
)

fun Note.toEntity() = NoteEntity(
    id = id,
    userId = userId,
    title = title,
    content = content,
    folderId = folderId,
    isPinned = isPinned,
    createdAt = createdAt,
    updatedAt = updatedAt,
    version = version,
    syncState = syncState.name,
    deletedAt = deletedAt,
)

/**
 * A queued synchronization operation.
 *
 * Every local write produces one of these in the SAME transaction as the
 * note row change, so a process kill can never separate a note from its
 * "needs to sync" marker. The WorkManager worker drains this table.
 */
@Entity(tableName = "pending_ops")
data class PendingOpEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    /** CREATE / UPDATE / DELETE */
    val opType: String,
    val noteId: String,
    /** JSON snapshot of the note to push (null for DELETE). */
    val payload: String?,
    val createdAt: Long,
    val attempts: Int = 0,
    val lastError: String? = null,
)

fun FolderEntity.toDomain() = Folder(
    id = id,
    userId = userId,
    name = name,
    createdAt = createdAt,
    updatedAt = updatedAt,
)
