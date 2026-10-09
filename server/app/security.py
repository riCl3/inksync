"""Password hashing and JWT issuing/verification.

- bcrypt cost is left at library default (12) — good for a notes app,
  and the hash is what gets stored; plaintext never leaves this module.
- Tokens are HS256 signed JWTs with `sub` = user id and a real expiry.
  Verification checks signature AND expiry on every request.
- `jwt_secret` comes from the environment; the app refuses to start in
  production with the development default (see main.py).
"""

from datetime import datetime, timedelta, timezone

import bcrypt
import jwt

from app.config import settings


class TokenError(Exception):
    """Invalid, malformed, or expired token."""


def hash_password(password: str) -> str:
    return bcrypt.hashpw(password.encode(), bcrypt.gensalt()).decode()


def verify_password(password: str, hashed: str) -> bool:
    try:
        return bcrypt.checkpw(password.encode(), hashed.encode())
    except ValueError:
        return False


def create_access_token(user_id: str) -> str:
    now = datetime.now(timezone.utc)
    payload = {
        "sub": user_id,
        "iat": now,
        "exp": now + timedelta(minutes=settings.access_token_expire_minutes),
    }
    return jwt.encode(payload, settings.jwt_secret, algorithm=settings.jwt_algorithm)


def decode_access_token(token: str) -> str:
    """Returns the user id or raises [TokenError]."""
    try:
        payload = jwt.decode(
            token, settings.jwt_secret, algorithms=[settings.jwt_algorithm]
        )
    except jwt.ExpiredSignatureError as exc:
        raise TokenError("token expired") from exc
    except jwt.InvalidTokenError as exc:
        raise TokenError("invalid token") from exc
    sub = payload.get("sub")
    if not isinstance(sub, str):
        raise TokenError("missing subject")
    return sub
