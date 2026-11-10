package com.inc.app.data.local

import androidx.room.Entity
import androidx.room.Fts4

/**
 * Full-text search index over note titles and bodies.
 *
 * Uses an external-content FTS4 table backed by [NoteEntity]: Room keeps the
 * two in sync via generated triggers, so repository writes never have to
 * touch this table manually.
 */
@Entity(tableName = "notes_fts")
@Fts4(contentEntity = NoteEntity::class)
data class NoteFtsEntity(
    val title: String,
    val content: String,
)
