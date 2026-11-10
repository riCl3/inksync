package com.inc.app.data.sync

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.TestListenableWorkerBuilder
import com.inc.app.data.local.IncSyncDatabase
import com.inc.app.data.remote.IncSyncApi
import com.inc.app.data.remote.TokenStore
import com.inc.app.domain.model.SyncState
import kotlinx.coroutines.runBlocking
import okhttp3.ResponseBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import androidx.test.ext.junit.runners.AndroidJUnit4
import retrofit2.Response

@RunWith(AndroidJUnit4::class)
class SyncWorkerTest {

    private lateinit var context: Context
    private lateinit var db: IncSyncDatabase
    private lateinit var tokenStore: TokenStore

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        db = androidx.room.Room.inMemoryDatabaseBuilder(context, IncSyncDatabase::class.java).build()
        tokenStore = TokenStore(context)
        runBlocking { tokenStore.setSession("tok", "u1") }
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun seedPending() {
        runBlocking {
            db.noteDao().upsert(
                com.inc.app.data.local.NoteEntity(
                    id = "n1", userId = "u1", title = "t", content = "c", folderId = null,
                    isPinned = false, createdAt = 1, updatedAt = 2, version = 1,
                    syncState = SyncState.PENDING.name, deletedAt = null,
                ),
            )
            db.pendingOpDao().insert(
                com.inc.app.data.local.PendingOpEntity(opType = "CREATE", noteId = "n1", payload = null, createdAt = 1),
            )
        }
    }

    @Test
    fun retriesOnServerError() {
        seedPending()
        val failingApi = object : IncSyncApi {
            override suspend fun register(body: com.inc.app.data.remote.RegisterIn) = throw NotImplementedError()
            override suspend fun login(body: com.inc.app.data.remote.LoginIn) = throw NotImplementedError()
            override suspend fun me() = throw NotImplementedError()
            override suspend fun listNotes(): List<com.inc.app.data.remote.NoteOut> = emptyList()
            override suspend fun createNote(body: com.inc.app.data.remote.NoteCreate): Response<com.inc.app.data.remote.NoteOut> =
                Response.error(500, ResponseBody.create(null, "err"))
            override suspend fun getNote(id: String) = throw NotImplementedError()
            override suspend fun updateNote(id: String, body: com.inc.app.data.remote.NoteUpdate) = throw NotImplementedError()
            override suspend fun deleteNote(id: String) = throw NotImplementedError()
            override suspend fun listFolders(): List<com.inc.app.data.remote.FolderOut> = emptyList()
            override suspend fun createFolder(body: com.inc.app.data.remote.FolderCreate) = throw NotImplementedError()
            override suspend fun updateFolder(id: String, body: com.inc.app.data.remote.FolderUpdate) = throw NotImplementedError()
            override suspend fun deleteFolder(id: String) = throw NotImplementedError()
        }

        val factory = object : WorkerFactory() {
            override fun createWorker(
                appContext: Context,
                workerClassName: String,
                workerParameters: WorkerParameters,
            ): androidx.work.ListenableWorker =
                SyncWorker(appContext, workerParameters, db.noteDao(), db.folderDao(), db.pendingOpDao(), failingApi, tokenStore)
        }

        val worker = TestListenableWorkerBuilder<SyncWorker>(context)
            .setWorkerFactory(factory)
            .build()

        val result = runBlocking { worker.doWork() }
        assertEquals(androidx.work.ListenableWorker.Result.retry(), result)
    }

    @Test
    fun succeedsOnOk() {
        seedPending()
        val okApi = object : IncSyncApi {
            override suspend fun register(body: com.inc.app.data.remote.RegisterIn) = throw NotImplementedError()
            override suspend fun login(body: com.inc.app.data.remote.LoginIn) = throw NotImplementedError()
            override suspend fun me() = throw NotImplementedError()
            override suspend fun listNotes(): List<com.inc.app.data.remote.NoteOut> = emptyList()
            override suspend fun createNote(body: com.inc.app.data.remote.NoteCreate): Response<com.inc.app.data.remote.NoteOut> {
                val note = com.inc.app.data.remote.NoteOut(
                    id = body.id, userId = "u1", title = body.title, content = body.content,
                    folderId = body.folderId, isPinned = body.isPinned, version = 1,
                    createdAt = "2026-01-01T00:00:00Z", updatedAt = "2026-01-01T00:00:00Z", deletedAt = null,
                )
                return Response.success(note)
            }
            override suspend fun getNote(id: String) = throw NotImplementedError()
            override suspend fun updateNote(id: String, body: com.inc.app.data.remote.NoteUpdate) = throw NotImplementedError()
            override suspend fun deleteNote(id: String) = throw NotImplementedError()
            override suspend fun listFolders(): List<com.inc.app.data.remote.FolderOut> = emptyList()
            override suspend fun createFolder(body: com.inc.app.data.remote.FolderCreate) = throw NotImplementedError()
            override suspend fun updateFolder(id: String, body: com.inc.app.data.remote.FolderUpdate) = throw NotImplementedError()
            override suspend fun deleteFolder(id: String) = throw NotImplementedError()
        }

        val factory = object : WorkerFactory() {
            override fun createWorker(
                appContext: Context,
                workerClassName: String,
                workerParameters: WorkerParameters,
            ): androidx.work.ListenableWorker =
                SyncWorker(appContext, workerParameters, db.noteDao(), db.folderDao(), db.pendingOpDao(), okApi, tokenStore)
        }

        val worker = TestListenableWorkerBuilder<SyncWorker>(context)
            .setWorkerFactory(factory)
            .build()

        val result = runBlocking { worker.doWork() }
        assertEquals(androidx.work.ListenableWorker.Result.success(), result)
        runBlocking {
            assertEquals(SyncState.SYNCED.name, db.noteDao().getById("n1")?.syncState)
            assertTrue(db.pendingOpDao().getAll().isEmpty())
        }
    }
}
