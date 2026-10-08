package com.inc.app.di

import com.inc.app.data.repository.InMemoryNotesRepository
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
 * implementation backs it. Milestone 2 replaces the in-memory binding with
 * Room — the only line that changes is `@Binds` below.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class DataModule {

    @Binds
    @Singleton
    abstract fun bindNotesRepository(impl: InMemoryNotesRepository): NotesRepository
}
