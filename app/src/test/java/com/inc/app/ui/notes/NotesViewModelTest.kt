package com.inc.app.ui.notes

import com.inc.app.domain.model.Folder
import com.inc.app.domain.model.Note
import com.inc.app.domain.model.SyncState
import com.inc.app.domain.model.SyncStatus
import com.inc.app.domain.repository.NotesRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Verifies the aggregation logic of the notes home screen against a fake
 * repository — no Android framework needed (this is why ViewModels are plain
 * classes and repositories are interfaces).
 *
 * Two testing concepts in play:
 *  1. One [StandardTestDispatcher] shared between `Dispatchers.Main` (what
 *     `viewModelScope` uses) and `runTest`'s scheduler, so `advanceUntilIdle()`
 *     controls both.
 *  2. The ViewModel's StateFlow uses `WhileSubscribed`, so the test must
 *     actually *collect* it (via backgroundScope) or upstream work never runs.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class NotesViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `empty repository produces empty ui state`() = runTest(dispatcher.scheduler) {
        val repository = FakeNotesRepository()
        val viewModel = NotesViewModel(repository)
        collectState(viewModel.uiState)
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.isEmpty)
        assertEquals(false, viewModel.uiState.value.isLoading)
    }

    @Test
    fun `pinned notes are separated from regular notes`() = runTest(dispatcher.scheduler) {
        val repository = FakeNotesRepository()
        repository.seed(note("a", pinned = true), note("b"))
        val viewModel = NotesViewModel(repository)
        collectState(viewModel.uiState)
        advanceUntilIdle()

        assertEquals(listOf("a"), viewModel.uiState.value.pinned.map { it.id })
        assertEquals(listOf("b"), viewModel.uiState.value.notes.map { it.id })
    }

    @Test
    fun `any unsynced note aggregates to PENDING status`() = runTest(dispatcher.scheduler) {
        val repository = FakeNotesRepository()
        repository.seed(note("a", sync = SyncState.SYNCED))
        val viewModel = NotesViewModel(repository)
        collectState(viewModel.uiState)
        advanceUntilIdle()
        assertEquals(SyncStatus.SYNCED, viewModel.uiState.value.syncStatus)

        repository.seed(note("a", sync = SyncState.SYNCED), note("b", sync = SyncState.PENDING))
        advanceUntilIdle()
        assertEquals(SyncStatus.PENDING, viewModel.uiState.value.syncStatus)
    }

    @Test
    fun `setPinned moves a note into the pinned section`() = runTest(dispatcher.scheduler) {
        val repository = FakeNotesRepository()
        repository.seed(note("a"))
        val viewModel = NotesViewModel(repository)
        collectState(viewModel.uiState)
        advanceUntilIdle()

        viewModel.setPinned("a", pinned = true)
        advanceUntilIdle()

        assertEquals(listOf("a"), viewModel.uiState.value.pinned.map { it.id })
        assertTrue(viewModel.uiState.value.notes.isEmpty())
        // Pinning marks the row as changed → not yet synced.
        assertEquals(SyncStatus.PENDING, viewModel.uiState.value.syncStatus)
    }

    @Test
    fun `deleteNote removes the note`() = runTest(dispatcher.scheduler) {
        val repository = FakeNotesRepository()
        repository.seed(note("a"))
        val viewModel = NotesViewModel(repository)
        collectState(viewModel.uiState)
        advanceUntilIdle()

        viewModel.deleteNote("a")
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.isEmpty)
    }

    /**
     * Keeps the `WhileSubscribed(5_000)` upstream active for the duration of
     * the test; backgroundScope jobs are cancelled automatically at the end.
     */
    private fun TestScope.collectState(flow: StateFlow<*>) {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { flow.collect { } }
    }

    private fun note(
        id: String = "n1",
        pinned: Boolean = false,
        sync: SyncState = SyncState.PENDING,
    ) = Note(
        id = id,
        userId = "u1",
        title = "Title $id",
        content = "",
        folderId = null,
        isPinned = pinned,
        createdAt = 0L,
        updatedAt = 0L,
        version = 1,
        syncState = sync,
        deletedAt = null,
    )

    private class FakeNotesRepository : NotesRepository {
        private val state = MutableStateFlow<List<Note>>(emptyList())

        override fun observeNotes(): Flow<List<Note>> = state
        override fun observeFolders(): Flow<List<Folder>> = MutableStateFlow(emptyList())
        override suspend fun getNote(id: String): Note? = state.value.firstOrNull { it.id == id }

        override suspend fun createNote(title: String, content: String, folderId: String?): String {
            error("not used in these tests")
        }

        override suspend fun updateNote(id: String, title: String, content: String) = Unit

        override suspend fun setPinned(id: String, pinned: Boolean) {
            state.update { list ->
                list.map { if (it.id == id) it.copy(isPinned = pinned) else it }
            }
        }

        override suspend fun deleteNote(id: String) {
            state.update { list -> list.filterNot { it.id == id } }
        }

        fun seed(vararg notes: Note) {
            state.value = notes.toList()
        }
    }
}
