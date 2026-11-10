package com.inc.app.di

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.inc.app.data.local.FolderDao
import com.inc.app.data.local.IncSyncDatabase
import com.inc.app.data.local.NoteDao
import com.inc.app.data.local.NoteFtsDao
import com.inc.app.data.local.PendingOpDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Room wiring.
 *
 * The database is a heavyweight, expensive-to-open singleton — exactly one
 * instance may exist per process, or you get separate connection pools and
 * confusing "database is locked" behavior. `@Singleton` at the provider level
 * guarantees that.
 *
 * fallBackToDestructiveMigration is deliberately NOT set: silently wiping a
 * user's notes because a version number changed is the opposite of
 * "data integrity over convenience". Migrations are explicit and tested.
 */
@Module
@InstallIn(SingletonComponent::class)
object RoomModule {

    val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS pending_ops (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    opType TEXT NOT NULL,
                    noteId TEXT NOT NULL,
                    payload TEXT,
                    createdAt INTEGER NOT NULL,
                    attempts INTEGER NOT NULL DEFAULT 0,
                    lastError TEXT
                )
                """.trimIndent(),
            )
        }
    }

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): IncSyncDatabase =
        Room.databaseBuilder(context, IncSyncDatabase::class.java, "incsync.db")
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
            .build()

    @Provides
    fun provideNoteDao(db: IncSyncDatabase): NoteDao = db.noteDao()

    @Provides
    fun provideFolderDao(db: IncSyncDatabase): FolderDao = db.folderDao()

    @Provides
    fun providePendingOpDao(db: IncSyncDatabase): PendingOpDao = db.pendingOpDao()

    @Provides
    fun provideNoteFtsDao(db: IncSyncDatabase): NoteFtsDao = db.noteFtsDao()

    val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE notes ADD COLUMN syncedVersion INTEGER NOT NULL DEFAULT 0")
        }
    }

    val MIGRATION_3_4 = object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE folders ADD COLUMN version INTEGER NOT NULL DEFAULT 1")
            db.execSQL("ALTER TABLE folders ADD COLUMN syncState TEXT NOT NULL DEFAULT 'PENDING'")
            db.execSQL("ALTER TABLE folders ADD COLUMN syncedVersion INTEGER NOT NULL DEFAULT 0")
        }
    }

    val MIGRATION_4_5 = object : Migration(4, 5) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("CREATE VIRTUAL TABLE IF NOT EXISTS notes_fts USING fts4(content='notes', title, content)")
            db.execSQL("INSERT INTO notes_fts(notes_fts) VALUES('rebuild')")
            db.execSQL("CREATE TRIGGER IF NOT EXISTS notes_fts_insert AFTER INSERT ON notes BEGIN INSERT INTO notes_fts(docid, title, content) VALUES (new.rowid, new.title, new.content); END")
            db.execSQL("CREATE TRIGGER IF NOT EXISTS notes_fts_delete AFTER DELETE ON notes BEGIN INSERT INTO notes_fts(notes_fts, docid, title, content) VALUES ('delete', old.rowid, old.title, old.content); END")
            db.execSQL("CREATE TRIGGER IF NOT EXISTS notes_fts_update AFTER UPDATE ON notes BEGIN INSERT INTO notes_fts(notes_fts, docid, title, content) VALUES ('delete', old.rowid, old.title, old.content); INSERT INTO notes_fts(docid, title, content) VALUES (new.rowid, new.title, new.content); END")
        }
    }
}
