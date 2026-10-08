package com.inc.app.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/**
 * Data access for notes.
 *
 * Concepts:
 *  - Return types do the thinking: `Flow` from Room means "invalidate this
 *    query whenever the table changes" — the UI's reactive chain needs no
 *    manual refresh. `suspend` for commands (one-shot writes).
 *  - Mutations are single SQL statements, hence atomic without an explicit
 *    transaction. The `version = version + 1` and `syncState = 'PENDING'`
 *    clauses mean a note can never be observed half-updated: the row always
 *    carries its own "this changed and hasn't synced" marker.
 *  - Filtering `deletedAt IS NULL` happens in SQL — deleted rows never cross
 *    into memory (privacy + memory).
 */
@Dao
interface NoteDao {

    @Query("SELECT * FROM notes WHERE deletedAt IS NULL ORDER BY updatedAt DESC")
    fun observeVisible(): Flow<List<NoteEntity>>

    @Query("SELECT * FROM notes WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): NoteEntity?

    @Upsert
    suspend fun upsert(note: NoteEntity)

    @Query(
        """
        UPDATE notes
        SET title = :title, content = :content,
            updatedAt = :updatedAt, version = version + 1, syncState = 'PENDING'
        WHERE id = :id
        """,
    )
    suspend fun updateContent(
        id: String,
        title: String,
        content: String,
        updatedAt: Long,
    ): Int

    @Query(
        """
        UPDATE notes
        SET isPinned = :pinned, updatedAt = :updatedAt,
            version = version + 1, syncState = 'PENDING'
        WHERE id = :id AND isPinned != :pinned
        """,
    )
    suspend fun setPinned(id: String, pinned: Boolean, updatedAt: Long): Int

    @Query("DELETE FROM notes WHERE id = :id")
    suspend fun delete(id: String)

    @Query("SELECT COUNT(*) FROM notes WHERE syncState != 'SYNCED' AND deletedAt IS NULL")
    suspend fun countUnsynced(): Int
}

@Dao
interface FolderDao {

    @Query("SELECT * FROM folders ORDER BY name COLLATE NOCASE ASC")
    fun observeAll(): Flow<List<FolderEntity>>

    @Upsert
    suspend fun upsert(folder: FolderEntity)

    @Query("DELETE FROM folders WHERE id = :id")
    suspend fun delete(id: String)
}
