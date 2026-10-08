package com.inc.app.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.inc.app.data.local.IncSyncDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Repository-level tests against a real (file-backed) Room database.
 *
 * The key test here is [data_survives_database_reopen]: it closes the whole
 * database connection and opens it again — the closest unit-level stand-in
 * for Android killing the process and the user relaunching the app. Data that
 * survives reopen survives process death, because SQLite on disk doesn't care
 * about the process.
 */
@RunWith(AndroidJUnit4::class)
class RoomNotesRepositoryTest {

    private lateinit var context: Context
    private lateinit var db: IncSyncDatabase
    private lateinit var repository: RoomNotesRepository

    private val dbName = "test-reopen.db"

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        context.deleteDatabase(dbName)
        db = openDb()
        repository = RoomNotesRepository(db.noteDao(), db.folderDao())
    }

    @After
    fun tearDown() {
        db.close()
        context.deleteDatabase(dbName)
    }

    private fun openDb(): IncSyncDatabase =
        Room.databaseBuilder(context, IncSyncDatabase::class.java, dbName).build()

    @Test
    fun createNote_persistsRow_pendingVersion1() = runTest {
        val id = repository.createNote("Meeting", "5 PM")

        val note = repository.getNote(id)
        assertNotNull(note)
        assertEquals("Meeting", note!!.title)
        assertEquals(1, note.version)
        assertEquals(com.inc.app.domain.model.SyncState.PENDING, note.syncState)
    }

    @Test
    fun updateNote_bumpsVersion_andStaysPending() = runTest {
        val id = repository.createNote("A", "B")
        repository.updateNote(id, "A2", "B2")

        val note = repository.getNote(id)!!
        assertEquals("A2", note.title)
        assertEquals(2, note.version)
        assertEquals(com.inc.app.domain.model.SyncState.PENDING, note.syncState)
    }

    @Test
    fun observeNotes_reflectsWrites_reactively() = runTest {
        assertTrue(repository.observeNotes().first().isEmpty())

        val id = repository.createNote("Reactive", "")

        val notes = repository.observeNotes().first()
        assertEquals(listOf(id), notes.map { it.id })
    }

    @Test
    fun data_survives_database_reopen() = runTest {
        val id = repository.createNote("Durable", "must survive")
        repository.updateNote(id, "Durable v2", "still here")

        // Simulate process death: tear the whole connection down…
        db.close()

        // …and start fresh, exactly as a relaunched app would.
        db = openDb()
        repository = RoomNotesRepository(db.noteDao(), db.folderDao())

        val note = repository.getNote(id)
        assertEquals("Durable v2", note?.title)
        assertEquals(2, note?.version)
        assertEquals(com.inc.app.domain.model.SyncState.PENDING, note?.syncState)
    }

    @Test
    fun deleteNote_removesRow_completely() = runTest {
        val id = repository.createNote("Temp", "")
        repository.deleteNote(id)

        assertEquals(null, repository.getNote(id))
        assertTrue(repository.observeNotes().first().isEmpty())
    }
}
