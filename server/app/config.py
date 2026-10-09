"""Environment-driven settings.

Secrets come from the environment (see .env.example) — never from source
control. `jwt_secret` has a loud development default that fails fast in
production via the check in main.py.
"""

from functools import lru_cache

from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    model_config = SettingsConfigDict(env_file=".env", extra="ignore")

    database_url: str = "postgresql+psycopg://incsync:incsync@db:5432/incsync"
    jwt_secret: str = "dev-only-insecure-secret-change-me"
    jwt_algorithm: str = "HS256"
    # Single long-lived access token keeps auth small (documented limitation:
    # no refresh-token rotation yet — see README "Known limitations").
    access_token_expire_minutes: int = 60 * 24 * 7
    environment: str = "development"
    cors_origins: str = "*"


@lru_cache
def get_settings() -> Settings:
    return Settings()


settings = get_settings()
