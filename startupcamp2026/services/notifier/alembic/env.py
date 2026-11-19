from logging.config import fileConfig

from alembic import context
from sqlalchemy import create_engine, text

from app.config import settings
from app.models import SCHEMA, Base

if context.config.config_file_name:
    fileConfig(context.config.config_file_name, disable_existing_loggers=False)


def run_migrations() -> None:
    engine = create_engine(settings.database_url)
    with engine.connect() as connection:
        # Alembic keeps its own version table inside the service's schema,
        # so the schema has to exist before the first migration. In the
        # compose environment it already does (infra/postgres/init-roles.sh)
        # and this role may not create schemas; in tests it is created here.
        exists = connection.scalar(
            text("SELECT 1 FROM pg_namespace WHERE nspname = :name"), {"name": SCHEMA}
        )
        if not exists:
            connection.execute(text(f"CREATE SCHEMA {SCHEMA}"))
        connection.commit()
        context.configure(
            connection=connection,
            target_metadata=Base.metadata,
            version_table_schema=SCHEMA,
            include_schemas=True,
        )
        with context.begin_transaction():
            context.run_migrations()


run_migrations()
