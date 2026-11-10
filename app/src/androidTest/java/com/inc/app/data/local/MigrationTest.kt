package com.inc.app.data.local

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException

@RunWith(AndroidJUnit4::class)
class MigrationTest {

    private val dbName = "migration-test.db"

    @get:Rule
    val helper: MigrationTestHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        "schemas",
    )

    @Test
    @Throws(IOException::class)
    fun migrate4To5_preservesNotesAndBuildsFts() {
        // Create v4 database with one note.
        helper.createDatabase(dbName, 4).apply {
            execSQL(
                "INSERT INTO notes (id, userId, title, content, folderId, isPinned, createdAt, updatedAt, version, syncState, deletedAt, syncedVersion) " +
                    "VALUES ('n1','u1','Hello world','body text',NULL,0,1,2,1,'SYNCED',NULL,1)",
            )
            execSQL(
                "INSERT INTO folders (id, userId, name, createdAt, updatedAt, version, syncState, syncedVersion) " +
                    "VALUES ('f1','u1','Folder',1,2,1,'SYNCED',1)",
            )
            close()
        }

        // Migrate to v5.
        val db = helper.runMigrationsAndValidate(dbName, 5, true, com.inc.app.di.RoomModule.MIGRATION_4_5)

        // Note preserved.
        db.query("SELECT title, content FROM notes WHERE id='n1'").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("Hello world", c.getString(0))
            assertEquals("body text", c.getString(1))
        }

        // FTS populated and queryable.
        db.query("SELECT notes.id FROM notes JOIN notes_fts ON notes.rowid = notes_fts.rowid WHERE notes_fts MATCH 'Hello'").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("n1", c.getString(0))
        }

        db.close()
    }
}
