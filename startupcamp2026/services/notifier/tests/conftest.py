"""Postgres for the test run, then Alembic. Locally Testcontainers; in GitLab
CI a sidecar on the job network (OTMETKA_TEST_DATABASE=service)."""

import os

import pytest

# Before any test imports app.config and reads the environment.
_postgres = None
if os.environ.get("OTMETKA_TEST_DATABASE") != "service":
    from testcontainers.community.postgres import PostgresContainer

    _postgres = PostgresContainer("postgres:16-alpine", driver="psycopg")
    _postgres.start()
    os.environ["DATABASE_URL"] = _postgres.get_connection_url()

os.environ["CONSUMER_ENABLED"] = "false"
os.environ["ALERTS_ENABLED"] = "false"
# Fixed here so that a value in the developer's shell cannot change the outcome.
os.environ["INTERNAL_SECRET"] = "test-internal-secret"
os.environ["RABBITMQ_URL"] = "amqp://guest:guest@127.0.0.1:5672/"
os.environ["PUSH_DRY_RUN"] = "true"


@pytest.fixture(scope="session", autouse=True)
def database():
    from alembic import command
    from alembic.config import Config

    command.upgrade(Config("alembic.ini"), "head")
    yield
    if _postgres is not None:
        _postgres.stop()


@pytest.fixture
def session(database):
    from app.db import SessionLocal

    with SessionLocal() as session:
        yield session
