"""FastAPI application.

Small on purpose: routers + CORS + a health check. Business rules live in
routers/models; auth rules live in deps/security; configuration in config.
"""

from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware

from app.config import settings
from app.routers import auth, folders, notes


def create_app() -> FastAPI:
    if settings.environment == "production" and settings.jwt_secret.startswith(
        "dev-only-insecure"
    ):
        # Fail fast rather than minting tokens everyone can forge.
        raise RuntimeError("JWT_SECRET must be set in production")

    application = FastAPI(
        title="inksyc API",
        version="0.1.0",
        # Keep the interactive docs available outside production only.
        docs_url=None if settings.environment == "production" else "/docs",
        redoc_url=None,
    )

    origins = [o.strip() for o in settings.cors_origins.split(",") if o.strip()]
    application.add_middleware(
        CORSMiddleware,
        allow_origins=origins,
        allow_credentials=False,
        allow_methods=["*"],
        allow_headers=["*"],
    )

    application.include_router(auth.router)
    application.include_router(notes.router)
    application.include_router(folders.router)

    @application.get("/health", tags=["ops"])
    def health() -> dict[str, str]:
        return {"status": "ok"}

    return application


app = create_app()
