"""Pydantic request/response models — the API contract.

Notable contract choices driven by client requirements:
- `NoteCreate.id` is required: the client generates the UUID offline, and
  the id doubles as an idempotency key (replaying the same POST cannot
  create duplicates).
- `NoteUpdate.base_version` implements optimistic concurrency: updates carry
  the version they were based on; a mismatch returns 409 + current server
  state so the client can detect and resolve conflicts (Milestone 8).
"""

from datetime import datetime

from pydantic import BaseModel, ConfigDict, EmailStr, Field


class RegisterIn(BaseModel):
    email: EmailStr
    password: str = Field(min_length=8, max_length=128)


class LoginIn(BaseModel):
    email: EmailStr
    password: str = Field(min_length=1, max_length=128)


class UserOut(BaseModel):
    model_config = ConfigDict(from_attributes=True)

    id: str
    email: EmailStr
    created_at: datetime


class TokenOut(BaseModel):
    access_token: str
    token_type: str = "bearer"
    user: UserOut


class NoteCreate(BaseModel):
    id: str = Field(min_length=36, max_length=36)
    title: str = Field(default="", max_length=10_000)
    content: str = Field(default="", max_length=500_000)
    folder_id: str | None = None
    is_pinned: bool = False


class NoteUpdate(BaseModel):
    title: str | None = Field(default=None, max_length=10_000)
    content: str | None = Field(default=None, max_length=500_000)
    folder_id: str | None = None
    is_pinned: bool | None = None
    base_version: int | None = None


class NoteOut(BaseModel):
    model_config = ConfigDict(from_attributes=True)

    id: str
    user_id: str
    title: str
    content: str
    folder_id: str | None
    is_pinned: bool
    version: int
    created_at: datetime
    updated_at: datetime
    deleted_at: datetime | None


class ConflictOut(BaseModel):
    """409 body: what the server has, so the client can show a resolution UI."""

    reason: str = "version_conflict"
    current: NoteOut


class FolderCreate(BaseModel):
    id: str = Field(min_length=36, max_length=36)
    name: str = Field(min_length=1, max_length=200)


class FolderUpdate(BaseModel):
    name: str | None = Field(default=None, max_length=200)
    base_version: int | None = None


class FolderOut(BaseModel):
    model_config = ConfigDict(from_attributes=True)

    id: str
    user_id: str
    name: str
    version: int
    created_at: datetime
    updated_at: datetime
    deleted_at: datetime | None


class ErrorOut(BaseModel):
    detail: str
