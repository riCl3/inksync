package com.inc.app.data.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.inc.app.data.local.NoteDao
import com.inc.app.data.local.PendingOpDao
import com.inc.app.data.remote.IncSyncApi
import com.inc.app.data.remote.NoteCreate
import com.inc.app.data.remote.NoteUpdate
import com.inc.app.data.remote.TokenStore
import com.inc.app.domain.model.SyncState
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.delay
import retrofit2.HttpException
import java.io.IOException

@HiltWorker
class SyncWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val noteDao: NoteDao,
    private val pendingOpDao: PendingOpDao,
    private val api: IncSyncApi,
    private val tokenStore: TokenStore,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val token = tokenStore.token.value
        if (token == null) {
            // Not authenticated yet: leave ops queued, succeed quietly.
            return Result.success()
        }

        val ops = pendingOpDao.getAll()
        if (ops.isEmpty()) return Result.success()

        for (op in ops) {
            try {
                when (op.opType) {
                    "CREATE" -> handleCreate(op)
                    "UPDATE" -> handleUpdate(op)
                    "DELETE" -> handleDelete(op)
                }
            } catch (e: IOException) {
                // Transient: retry with backoff.
                return Result.retry()
            } catch (e: HttpException) {
                if (e.code() in 500..599) return Result.retry()
                // Permanent for this op: mark failed and drop it so it doesn't
                // block the queue forever.
                markFailed(op, "HTTP ${e.code()}")
            } catch (e: Exception) {
                markFailed(op, e.message)
            }
        }
        return Result.success()
    }

    private suspend fun handleCreate(op: com.inc.app.data.local.PendingOpEntity) {
        val note = noteDao.getById(op.noteId)
        if (note == null) {
            pendingOpDao.delete(op.id)
            return
        }
        val res = api.createNote(
            com.inc.app.data.remote.NoteCreate(
                id = note.id,
                title = note.title,
                content = note.content,
                folderId = note.folderId,
                isPinned = note.isPinned,
            ),
        )
        if (res.isSuccessful) {
            val server = res.body()!!
            noteDao.updateSyncState(note.id, SyncState.SYNCED.name, server.version, server.version)
            pendingOpDao.delete(op.id)
        } else if (res.code() == 409) {
            // Id owned by another user — permanent failure.
            noteDao.updateSyncState(note.id, SyncState.FAILED.name, note.version, note.syncedVersion)
            pendingOpDao.delete(op.id)
        } else if (res.code() in 500..599) {
            throw retrofit2.HttpException(res)
        } else {
            markFailed(op, "HTTP ${res.code()}")
        }
    }

    private suspend fun handleUpdate(op: com.inc.app.data.local.PendingOpEntity) {
        val note = noteDao.getById(op.noteId)
        if (note == null) {
            pendingOpDao.delete(op.id)
            return
        }
        if (note.syncedVersion == 0) {
            // Not created on server yet; skip, create op will handle it.
            return
        }
        val res = api.updateNote(
            note.id,
            com.inc.app.data.remote.NoteUpdate(
                title = note.title,
                content = note.content,
                folderId = note.folderId,
                isPinned = note.isPinned,
                baseVersion = note.syncedVersion,
            ),
        )
        if (res.isSuccessful) {
            val server = res.body()!!
            noteDao.updateSyncState(note.id, SyncState.SYNCED.name, server.version, server.version)
            pendingOpDao.delete(op.id)
        } else if (res.code() == 409) {
            // Conflict: parse server state, mark CONFLICT, do not overwrite content.
            val body = res.errorBody()?.string()
            // Best-effort parse of conflict current version
            val serverVersion = parseConflictVersion(body)
            if (serverVersion != null) {
                noteDao.updateSyncState(note.id, SyncState.CONFLICT.name, note.version, serverVersion)
            } else {
                noteDao.updateSyncState(note.id, SyncState.CONFLICT.name, note.version, note.syncedVersion)
            }
            pendingOpDao.delete(op.id)
        } else if (res.code() in 500..599) {
            throw retrofit2.HttpException(res)
        } else {
            markFailed(op, "HTTP ${res.code()}")
        }
    }

    private suspend fun handleDelete(op: com.inc.app.data.local.PendingOpEntity) {
        val res = api.deleteNote(op.noteId)
        if (res.isSuccessful || res.code() == 404) {
            pendingOpDao.delete(op.id)
        } else if (res.code() in 500..599) {
            throw retrofit2.HttpException(res)
        } else {
            markFailed(op, "HTTP ${res.code()}")
        }
    }

    private suspend fun markFailed(op: com.inc.app.data.local.PendingOpEntity, error: String?) {
        pendingOpDao.markFailed(op.id, error)
        // Mark the note failed if it still exists.
        noteDao.getById(op.noteId)?.let { note ->
            noteDao.updateSyncState(note.id, SyncState.FAILED.name, note.version, note.syncedVersion)
        }
        pendingOpDao.delete(op.id)
    }

    private fun parseConflictVersion(body: String?): Int? {
        if (body == null) return null
        return try {
            // crude: find "version":<n> in current object
            val regex = Regex("\"version\"\\s*:\\s*(\\d+)")
            regex.find(body)?.groupValues?.get(1)?.toInt()
        } catch (e: Exception) {
            null
        }
    }
}
