package com.inc.app.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class RegisterIn(val email: String, val password: String)

@Serializable
data class LoginIn(val email: String, val password: String)

@Serializable
data class UserOut(
    val id: String,
    val email: String,
    @SerialName("created_at") val createdAt: String,
)

@Serializable
data class TokenOut(
    @SerialName("access_token") val accessToken: String,
    @SerialName("token_type") val tokenType: String = "bearer",
    val user: UserOut,
)

@Serializable
data class NoteCreate(
    val id: String,
    val title: String = "",
    val content: String = "",
    @SerialName("folder_id") val folderId: String? = null,
    @SerialName("is_pinned") val isPinned: Boolean = false,
)

@Serializable
data class NoteUpdate(
    val title: String? = null,
    val content: String? = null,
    @SerialName("folder_id") val folderId: String? = null,
    @SerialName("is_pinned") val isPinned: Boolean? = null,
    @SerialName("base_version") val baseVersion: Int? = null,
)

@Serializable
data class NoteOut(
    val id: String,
    @SerialName("user_id") val userId: String,
    val title: String,
    val content: String,
    @SerialName("folder_id") val folderId: String?,
    @SerialName("is_pinned") val isPinned: Boolean,
    val version: Int,
    @SerialName("created_at") val createdAt: String,
    @SerialName("updated_at") val updatedAt: String,
    @SerialName("deleted_at") val deletedAt: String?,
)

@Serializable
data class ConflictOut(
    val reason: String = "version_conflict",
    val current: NoteOut,
)

@Serializable
data class FolderCreate(
    val id: String,
    val name: String,
)

@Serializable
data class FolderUpdate(
    val name: String? = null,
    @SerialName("base_version") val baseVersion: Int? = null,
)

@Serializable
data class FolderOut(
    val id: String,
    @SerialName("user_id") val userId: String,
    val name: String,
    val version: Int,
    @SerialName("created_at") val createdAt: String,
    @SerialName("updated_at") val updatedAt: String,
    @SerialName("deleted_at") val deletedAt: String?,
)
