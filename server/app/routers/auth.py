"""Registration and login.

Deliberately small: two endpoints, one token type. Security posture:
- Generic 401 on bad credentials (no "email not found" oracle).
- 409 on duplicate registration (needed so the UI can offer login instead).
- Rate limited per IP (deps.rate_limit_auth).
- Passwords only ever touched by bcrypt helpers.
"""

from fastapi import APIRouter, Depends, HTTPException, status

from app.deps import CurrentUser, DbSession, rate_limit_auth
from app.models import User
from app.schemas import LoginIn, RegisterIn, TokenOut, UserOut
from app.security import create_access_token, hash_password, verify_password

router = APIRouter(prefix="/auth", tags=["auth"])


@router.post(
    "/register",
    response_model=TokenOut,
    status_code=status.HTTP_201_CREATED,
    dependencies=[Depends(rate_limit_auth)],
)
def register(body: RegisterIn, db: DbSession) -> TokenOut:
    email = body.email.lower().strip()
    existing = db.query(User).filter(User.email == email).first()
    if existing is not None:
        raise HTTPException(status.HTTP_409_CONFLICT, "Email already registered")

    user = User(email=email, hashed_password=hash_password(body.password))
    db.add(user)
    db.commit()
    db.refresh(user)
    return TokenOut(access_token=create_access_token(user.id), user=UserOut.model_validate(user))


@router.post("/login", response_model=TokenOut, dependencies=[Depends(rate_limit_auth)])
def login(body: LoginIn, db: DbSession) -> TokenOut:
    email = body.email.lower().strip()
    user = db.query(User).filter(User.email == email).first()
    if user is None or not verify_password(body.password, user.hashed_password):
        # Same message for unknown email and wrong password: no user oracle.
        raise HTTPException(status.HTTP_401_UNAUTHORIZED, "Invalid email or password")
    return TokenOut(access_token=create_access_token(user.id), user=UserOut.model_validate(user))


@router.get("/me", response_model=UserOut)
def me(user: CurrentUser) -> UserOut:
    return UserOut.model_validate(user)
