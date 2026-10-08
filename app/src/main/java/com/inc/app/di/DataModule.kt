package com.inc.app.di

import com.inc.app.data.repository.RoomNotesRepository
import com.inc.app.domain.repository.NotesRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Bindings for the data layer.
 *
 * ViewModels depend on [NotesRepository]; this module decides which
 * implementation backs it. Milestone 2 swapped the in-memory stand-in for
 * Room by changing exactly this file — no ViewModel or screen was touched.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class DataModule {

    @Binds
    @Singleton
    abstract fun bindNotesRepository(impl: RoomNotesRepository): NotesRepository
}
