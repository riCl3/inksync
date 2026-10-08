package com.inc.app.data.local

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Room DAO tests running against a real SQLite database on the emulator.
 *
 * Why instrumented rather than mocked: these tests verify SQLite's actual
 * behavior (constraint enforcement, UPDATE counts, Flow invalidation) — a
 * mocked DAO would only test the mock.
 *
 * The in-memory database (`allowDestructiveMigration` n/a, name ":memory:")
 * keeps tests fast and isolated; each test gets a fresh instance.
 */
@RunWith(AndroidJUnit4::class)
class NoteDaoTest {

    private lateinit var db: IncSyncDatabase
    private lateinit var dao: NoteDao

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext,
            IncSyncDatabase::class.java,
        ).build()
        dao = db.noteDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun entity(
        id: String = "n1",
        title: String = "Title",
        content: String = "Body",
        pinned: Boolean = false,
        syncState: String = "PENDING",
        updatedAt: Long = 1_000L,
    ) = NoteEntity(
        id = id,
        userId = "u1",
        title = title,
        content = content,
        folderId = null,
        isPinned = pinned,
        createdAt = 0L,
        updatedAt = updatedAt,
        version = 1,
        syncState = syncState,
        deletedAt = null,
    )

    @Test
    fun insertAndReadBack() = runTest {
        dao.upsert(entity())
        val loaded = dao.getById("n1")
        assertEquals("Title", loaded?.title)
        assertEquals("Body", loaded?.content)
    }

    @Test
    fun observeVisible_ordersByUpdatedAtDescending() = runTest {
        dao.upsert(entity(id = "old", updatedAt = 100L))
        dao.upsert(entity(id = "new", updatedAt = 200L))

        val visible = dao.observeVisible().first()
        assertEquals(listOf("new", "old"), visible.map { it.id })
    }

    @Test
    fun updateContent_incrementsVersion_andMarksPending() = runTest {
        dao.upsert(entity(syncState = "SYNCED"))

        val affected = dao.updateContent(
            id = "n1",
            title = "Updated",
            content = "New body",
            updatedAt = 2_000L,
        )

        assertEquals(1, affected)
        val loaded = dao.getById("n1")!!
        assertEquals(2, loaded.version)
        assertEquals("PENDING", loaded.syncState)
        assertEquals("Updated", loaded.title)
        assertEquals(2_000L, loaded.updatedAt)
    }

    @Test
    fun updateContent_onMissingRow_affectsNothing() = runTest {
        val affected = dao.updateContent("ghost", "t", "c", 0L)
        assertEquals(0, affected)
    }

    @Test
    fun setPinned_isIdempotent_whenAlreadyInTargetState() = runTest {
        dao.upsert(entity(pinned = false))

        val first = dao.setPinned("n1", pinned = true, updatedAt = 3_000L)
        val second = dao.setPinned("n1", pinned = true, updatedAt = 4_000L)

        assertEquals(1, first) // change happened
        assertEquals(0, second) // no-op: same value → no version bump
        val loaded = dao.getById("n1")!!
        assertTrue(loaded.isPinned)
        assertEquals(2, loaded.version) // exactly one bump
    }

    @Test
    fun delete_removesRowAndItsGoneFromObservation() = runTest {
        dao.upsert(entity())

        dao.delete("n1")

        assertNull(dao.getById("n1"))
        assertTrue(dao.observeVisible().first().isEmpty())
    }

    @Test
    fun countUnsynced_countsOnlyUnsyncedVisibleRows() = runTest {
        dao.upsert(entity(id = "a", syncState = "SYNCED"))
        dao.upsert(entity(id = "b", syncState = "PENDING"))
        dao.upsert(entity(id = "c", syncState = "FAILED"))

        assertEquals(2, dao.countUnsynced())
    }
}
