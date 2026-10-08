package com.inc.app.di

import android.content.Context
import androidx.room.Room
import com.inc.app.data.local.FolderDao
import com.inc.app.data.local.IncSyncDatabase
import com.inc.app.data.local.NoteDao
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

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): IncSyncDatabase =
        Room.databaseBuilder(context, IncSyncDatabase::class.java, "incsync.db")
            .build()

    @Provides
    fun provideNoteDao(db: IncSyncDatabase): NoteDao = db.noteDao()

    @Provides
    fun provideFolderDao(db: IncSyncDatabase): FolderDao = db.folderDao()
}
