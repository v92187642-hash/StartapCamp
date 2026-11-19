#!/bin/sh
# Runs once, when the Postgres data volume is first created.
# One role and one schema per service: a service cannot read the other's
# tables even by mistake (docs/architecture.md, boundary 3).
set -e

psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" \
    -v core_password="$CORE_DB_PASSWORD" -v notifier_password="$NOTIFIER_DB_PASSWORD" <<'SQL'
CREATE ROLE core LOGIN PASSWORD :'core_password';
CREATE ROLE notifier LOGIN PASSWORD :'notifier_password';
CREATE SCHEMA core AUTHORIZATION core;
CREATE SCHEMA notifier AUTHORIZATION notifier;
REVOKE ALL ON SCHEMA public FROM PUBLIC;
SQL
