"""Folders CRUD — same ownership + idempotency + versioning rules as notes."""

from fastapi import APIRouter, HTTPException, Response, status
from sqlalchemy.orm import Session

from app.deps import CurrentUser, DbSession
from app.models import Folder, utcnow
from app.schemas import FolderCreate, FolderOut, FolderUpdate

router = APIRouter(prefix="/folders", tags=["folders"])


def _owned_folder(db: Session, user_id: str, folder_id: str) -> Folder:
    folder = db.get(Folder, folder_id)
    if folder is None or folder.user_id != user_id or folder.deleted_at is not None:
        raise HTTPException(status.HTTP_404_NOT_FOUND, "Folder not found")
    return folder


@router.get("", response_model=list[FolderOut])
def list_folders(user: CurrentUser, db: DbSession) -> list[FolderOut]:
    rows = (
        db.query(Folder)
        .filter(Folder.user_id == user.id, Folder.deleted_at.is_(None))
        .order_by(Folder.name.asc())
        .all()
    )
    return [FolderOut.model_validate(r) for r in rows]


@router.post("", response_model=FolderOut)
def create_folder(
    body: FolderCreate,
    user: CurrentUser,
    db: DbSession,
    response: Response,
) -> FolderOut:
    existing = db.get(Folder, body.id)
    if existing is not None:
        if existing.user_id != user.id:
            raise HTTPException(status.HTTP_409_CONFLICT, "Id already in use")
        response.status_code = status.HTTP_200_OK
        return FolderOut.model_validate(existing)

    folder = Folder(id=body.id, user_id=user.id, name=body.name, version=1)
    db.add(folder)
    db.commit()
    db.refresh(folder)
    response.status_code = status.HTTP_201_CREATED
    return FolderOut.model_validate(folder)


@router.put("/{folder_id}", response_model=FolderOut)
def update_folder(
    folder_id: str,
    body: FolderUpdate,
    user: CurrentUser,
    db: DbSession,
) -> FolderOut:
    folder = _owned_folder(db, user.id, folder_id)
    if body.base_version is not None and body.base_version != folder.version:
        raise HTTPException(
            status_code=status.HTTP_409_CONFLICT,
            detail={
                "reason": "version_conflict",
                "current": FolderOut.model_validate(folder).model_dump(mode="json"),
            },
        )
    if body.name is not None:
        folder.name = body.name
    folder.version += 1
    folder.updated_at = utcnow()
    db.commit()
    db.refresh(folder)
    return FolderOut.model_validate(folder)


@router.delete("/{folder_id}", status_code=status.HTTP_204_NO_CONTENT)
def delete_folder(folder_id: str, user: CurrentUser, db: DbSession) -> Response:
    folder = _owned_folder(db, user.id, folder_id)
    folder.deleted_at = utcnow()
    folder.version += 1
    db.commit()
    return Response(status_code=status.HTTP_204_NO_CONTENT)
