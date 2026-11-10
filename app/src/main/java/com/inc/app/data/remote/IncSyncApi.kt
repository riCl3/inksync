package com.inc.app.data.remote

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path

interface IncSyncApi {

    // --- auth ---
    @POST("auth/register")
    suspend fun register(@Body body: RegisterIn): TokenOut

    @POST("auth/login")
    suspend fun login(@Body body: LoginIn): TokenOut

    @GET("auth/me")
    suspend fun me(): UserOut

    // --- notes ---
    @GET("notes")
    suspend fun listNotes(): List<NoteOut>

    @POST("notes")
    suspend fun createNote(@Body body: NoteCreate): Response<NoteOut>

    @GET("notes/{id}")
    suspend fun getNote(@Path("id") id: String): NoteOut

    @PUT("notes/{id}")
    suspend fun updateNote(@Path("id") id: String, @Body body: NoteUpdate): Response<NoteOut>

    @DELETE("notes/{id}")
    suspend fun deleteNote(@Path("id") id: String): Response<Unit>

    // --- folders ---
    @GET("folders")
    suspend fun listFolders(): List<FolderOut>

    @POST("folders")
    suspend fun createFolder(@Body body: FolderCreate): Response<FolderOut>

    @PUT("folders/{id}")
    suspend fun updateFolder(@Path("id") id: String, @Body body: FolderUpdate): Response<FolderOut>

    @DELETE("folders/{id}")
    suspend fun deleteFolder(@Path("id") id: String): Response<Unit>
}
