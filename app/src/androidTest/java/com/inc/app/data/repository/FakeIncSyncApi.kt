package com.inc.app.data.repository

import com.inc.app.data.remote.FolderCreate
import com.inc.app.data.remote.FolderOut
import com.inc.app.data.remote.FolderUpdate
import com.inc.app.data.remote.IncSyncApi
import com.inc.app.data.remote.LoginIn
import com.inc.app.data.remote.NoteCreate
import com.inc.app.data.remote.NoteOut
import com.inc.app.data.remote.NoteUpdate
import com.inc.app.data.remote.RegisterIn
import com.inc.app.data.remote.TokenOut
import com.inc.app.data.remote.UserOut
import retrofit2.Response

object FakeIncSyncApi : IncSyncApi {
    override suspend fun register(body: RegisterIn): TokenOut = throw NotImplementedError()
    override suspend fun login(body: LoginIn): TokenOut = throw NotImplementedError()
    override suspend fun me(): UserOut = throw NotImplementedError()
    override suspend fun listNotes(): List<NoteOut> = emptyList()
    override suspend fun createNote(body: NoteCreate): Response<NoteOut> = throw NotImplementedError()
    override suspend fun getNote(id: String): NoteOut = throw NotImplementedError()
    override suspend fun updateNote(id: String, body: NoteUpdate): Response<NoteOut> = throw NotImplementedError()
    override suspend fun deleteNote(id: String): Response<Unit> = throw NotImplementedError()
    override suspend fun listFolders(): List<FolderOut> = emptyList()
    override suspend fun createFolder(body: com.inc.app.data.remote.FolderCreate): Response<FolderOut> = throw NotImplementedError()
    override suspend fun updateFolder(id: String, body: com.inc.app.data.remote.FolderUpdate): Response<FolderOut> = throw NotImplementedError()
    override suspend fun deleteFolder(id: String): Response<Unit> = throw NotImplementedError()
}
