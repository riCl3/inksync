package com.inc.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

/**
 * The app's local database — the primary source of truth for user-visible
 * data (per the architecture: UI ← Repository ← here).
 *
 * `exportSchema = true` writes a JSON schema per version into `app/schemas/`;
 * migration tests replay those to prove upgrades don't destroy user data.
 */
@Database(
    entities = [NoteEntity::class, FolderEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class IncSyncDatabase : RoomDatabase() {
    abstract fun noteDao(): NoteDao
    abstract fun folderDao(): FolderDao
}
