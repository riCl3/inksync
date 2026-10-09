"""Shared FastAPI dependencies: authenticated user + auth rate limiting."""

import time
from collections import defaultdict, deque
from typing import Annotated

from fastapi import Depends, HTTPException, Request, status
from fastapi.security import HTTPAuthorizationCredentials, HTTPBearer
from sqlalchemy.orm import Session

from app.db import get_db
from app.models import User
from app.security import TokenError, decode_access_token

bearer_scheme = HTTPBearer(auto_error=False)


def get_current_user(
    credentials: Annotated[HTTPAuthorizationCredentials | None, Depends(bearer_scheme)],
    db: Annotated[Session, Depends(get_db)],
) -> User:
    """One dependency = ownership enforcement for every protected route.

    No token / bad token / expired token → 401. Every router then filters by
    `user.id`, which is what actually prevents IDOR: knowing another user's
    note id is useless because their rows are never selected.
    """
    if credentials is None:
        raise HTTPException(status.HTTP_401_UNAUTHORIZED, "Not authenticated")
    try:
        user_id = decode_access_token(credentials.credentials)
    except TokenError as exc:
        detail = "Session expired" if str(exc) == "token expired" else "Invalid session"
        raise HTTPException(status.HTTP_401_UNAUTHORIZED, detail) from exc

    user = db.get(User, user_id)
    if user is None:
        raise HTTPException(status.HTTP_401_UNAUTHORIZED, "Account no longer exists")
    return user


CurrentUser = Annotated[User, Depends(get_current_user)]
DbSession = Annotated[Session, Depends(get_db)]


# --- Auth endpoint rate limiting -----------------------------------------
# Deliberately simple and in-process: a sliding window per client IP.
# It is not distributed (multiple API replicas would need Redis) — a
# documented limitation, but it stops password guessing against one instance
# without adding any infrastructure dependency.


_auth_attempts: dict[str, deque[float]] = defaultdict(deque)
AUTH_LIMIT = 10
AUTH_WINDOW_SECONDS = 60.0


def rate_limit_auth(request: Request) -> None:
    now = time.monotonic()
    key = request.client.host if request.client else "unknown"
    window = _auth_attempts[key]
    while window and now - window[0] > AUTH_WINDOW_SECONDS:
        window.popleft()
    if len(window) >= AUTH_LIMIT:
        raise HTTPException(
            status.HTTP_429_TOO_MANY_REQUESTS,
            "Too many attempts, slow down",
        )
    window.append(now)
