"""Test fixtures.

Each test gets a fresh in-memory SQLite database via FastAPI's dependency
override — the routers, auth, and SQLAlchemy models under test are exactly
the production ones; only the storage engine is swapped. (Postgres-specific
behavior is exercised against the real compose stack in manual/integration
verification, not in the unit suite.)
"""

import pytest
from sqlalchemy import create_engine
from sqlalchemy.orm import sessionmaker
from sqlalchemy.pool import StaticPool

from app import deps
from app.db import Base, get_db
from app.main import app


@pytest.fixture()
def client():
    engine = create_engine(
        "sqlite://",
        connect_args={"check_same_thread": False},
        poolclass=StaticPool,
    )
    TestingSession = sessionmaker(bind=engine, autoflush=False, expire_on_commit=False)
    Base.metadata.create_all(engine)

    def override_get_db():
        session = TestingSession()
        try:
            yield session
        finally:
            session.close()

    app.dependency_overrides[get_db] = override_get_db
    from fastapi.testclient import TestClient

    with TestClient(app) as test_client:
        yield test_client

    app.dependency_overrides.clear()
    engine.dispose()


@pytest.fixture(autouse=True)
def _reset_rate_limiter():
    """The auth rate limiter is process-global; isolate it per test."""
    deps._auth_attempts.clear()
    yield
    deps._auth_attempts.clear()
