package com.inc.app.domain.model

/**
 * Domain models.
 *
 * Plain Kotlin classes with no Room/Retrofit annotations on purpose: the UI
 * depends on [com.inc.app.domain], not on persistence or networking details.
 * Room (Milestone 2) and the API (Milestone 3) map into these types at the
 * data-source boundary.
 */

/**
 * Per-note synchronization state.
 *
 * The UI renders [SyncStatus] (app-wide); this is the state of one row in the
 * local database. It is written by the repository when a change is persisted
 * and rewritten by the sync worker as operations succeed or fail.
 */
enum class SyncState {
    /** Local change exists that has not reached the server yet. */
    PENDING,

    /** This version of the note matches the server's version. */
    SYNCED,

    /** An operation for this note is being sent right now. */
    SYNCING,

    /** Last attempt failed permanently; it will not be retried automatically. */
    FAILED,

    /** Another device modified this note; needs a resolution decision. */
    CONFLICT,
}

/** App-level status shown in the UI (chip in the top bar). */
enum class SyncStatus {
    SYNCED,
    SYNCING,
    OFFLINE,
    PENDING,
    FAILED,
    CONFLICT,
}

data class Note(
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
    val syncState: SyncState,
    /** Soft delete: rows are kept locally until the deletion is synced. */
    val deletedAt: Long?,
)

data class Folder(
    val id: String,
    val userId: String,
    val name: String,
    val createdAt: Long,
    val updatedAt: Long,
)
