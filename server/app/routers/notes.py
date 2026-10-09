"""Notes CRUD — the sync contract lives here.

Rules encoded in this router:
1. **Ownership**: every SELECT/UPDATE/DELETE filters by `user_id`.
   (IDOR defense at the query level, not by hiding ids in a UI.)
2. **Idempotent create**: POST with an id that already exists *for this user*
   returns 200 + existing row instead of failing or duplicating — a retried
   upload (Milestone 7) is therefore always safe.
   An id owned by someone else → 409 (cannot hijack a primary key).
3. **Optimistic concurrency**: PUT sends `base_version`; if it doesn't match
   the server's `version`, respond 409 with the *current server state* —
   never silently overwrite. The client turns that into a conflict UI (M8).
   `base_version = None` means "force" (the Android client never sends it).
4. Deletion is last-writer-wins (DELETE succeeds regardless of version):
   losing an edit to a deletion is acceptable; resurrecting deleted data is
   not. The row is soft-deleted (`deleted_at`) so the tombstone can be
   synchronized in later milestones.
"""

from fastapi import APIRouter, HTTPException, Response, status
from sqlalchemy.orm import Session

from app.deps import CurrentUser, DbSession
from app.models import Note, utcnow
from app.schemas import ConflictOut, NoteCreate, NoteOut, NoteUpdate

router = APIRouter(prefix="/notes", tags=["notes"])


def _owned_note(db: Session, user_id: str, note_id: str) -> Note:
    note = db.get(Note, note_id)
    # Not-found and not-yours are the *same* 404: whether another user's id
    # exists must not be observable (prevents probing/enumeration).
    # Soft-deleted rows (tombstones) are also "not found" for normal CRUD —
    # they are only reachable through the future sync endpoints.
    if note is None or note.user_id != user_id or note.deleted_at is not None:
        raise HTTPException(status.HTTP_404_NOT_FOUND, "Note not found")
    return note


@router.get("", response_model=list[NoteOut])
def list_notes(user: CurrentUser, db: DbSession) -> list[NoteOut]:
    rows = (
        db.query(Note)
        .filter(Note.user_id == user.id, Note.deleted_at.is_(None))
        .order_by(Note.updated_at.desc())
        .all()
    )
    return [NoteOut.model_validate(r) for r in rows]


@router.post("", response_model=NoteOut)
def create_note(
    body: NoteCreate,
    user: CurrentUser,
    db: DbSession,
    response: Response,
) -> NoteOut:
    existing = db.get(Note, body.id)
    if existing is not None:
        if existing.user_id != user.id:
            raise HTTPException(status.HTTP_409_CONFLICT, "Id already in use")
        # Replay of an already-applied operation: report success (200 instead
        # of 201) with the stored row — retries cannot create duplicates.
        response.status_code = status.HTTP_200_OK
        return NoteOut.model_validate(existing)

    note = Note(
        id=body.id,
        user_id=user.id,
        title=body.title,
        content=body.content,
        folder_id=body.folder_id,
        is_pinned=body.is_pinned,
        version=1,
    )
    db.add(note)
    db.commit()
    db.refresh(note)
    response.status_code = status.HTTP_201_CREATED
    return NoteOut.model_validate(note)


@router.get("/{note_id}", response_model=NoteOut)
def get_note(note_id: str, user: CurrentUser, db: DbSession) -> NoteOut:
    note = _owned_note(db, user.id, note_id)
    return NoteOut.model_validate(note)


@router.put("/{note_id}", response_model=NoteOut)
def update_note(
    note_id: str,
    body: NoteUpdate,
    user: CurrentUser,
    db: DbSession,
) -> NoteOut:
    note = _owned_note(db, user.id, note_id)

    if body.base_version is not None and body.base_version != note.version:
        # Conflict: return what the server has so the client can resolve it.
        raise HTTPException(
            status_code=status.HTTP_409_CONFLICT,
            detail=ConflictOut(
                reason="version_conflict",
                current=NoteOut.model_validate(note),
            ).model_dump(mode="json"),
        )

    if body.title is not None:
        note.title = body.title
    if body.content is not None:
        note.content = body.content
    if body.folder_id is not None:
        note.folder_id = body.folder_id
    if body.is_pinned is not None:
        note.is_pinned = body.is_pinned
    note.version += 1
    note.updated_at = utcnow()

    db.commit()
    db.refresh(note)
    return NoteOut.model_validate(note)


@router.delete("/{note_id}", status_code=status.HTTP_204_NO_CONTENT)
def delete_note(note_id: str, user: CurrentUser, db: DbSession) -> Response:
    note = _owned_note(db, user.id, note_id)
    # Soft delete: the tombstone is what multi-device sync will replay later.
    note.deleted_at = utcnow()
    note.version += 1
    db.commit()
    return Response(status_code=status.HTTP_204_NO_CONTENT)
