#!/bin/sh
# Empty Postgres, the same init as compose, then Flyway and Alembic twice.
# Fails if a migration does not apply, is not idempotent, or a service role
# can read the other schema.
#
# In GitLab CI, Postgres is a sidecar (PGHOST=postgres). Nothing is published
# on the runner host. Locally a Docker network is used, still with no host
# ports. Needs: psql, flyway, uv (CI installs them).
#
#   infra/scripts/check-migrations.sh
set -eu

root="$(cd "$(dirname "$0")/../.." && pwd)"
core_pw="${CORE_DB_PASSWORD:-ci-core-password}"
notifier_pw="${NOTIFIER_DB_PASSWORD:-ci-notifier-password}"
postgres_pw="${POSTGRES_PASSWORD:-ci-postgres-password}"
postgres_user="${POSTGRES_USER:-postgres}"
postgres_db="${POSTGRES_DB:-otmetka}"
pg="otmetka-migrate-$$"
fw="otmetka-flyway-$$"
net="otmetka-migrate-$$"
sidecar=0
if [ -n "${PGHOST:-}" ]; then
    sidecar=1
fi

cleanup() {
    if [ "$sidecar" -eq 0 ]; then
        docker rm -f "$fw" >/dev/null 2>&1 || true
        docker rm -f "$pg" >/dev/null 2>&1 || true
        docker network rm "$net" >/dev/null 2>&1 || true
    fi
}
trap cleanup EXIT

fail() { echo "FAIL: $*" >&2; exit 1; }

sql() {
    role=$1
    password=$2
    query=$3
    if [ "$sidecar" -eq 1 ]; then
        PGPASSWORD="$password" psql -h "$PGHOST" -U "$role" -d "$postgres_db" -v ON_ERROR_STOP=1 -At -c "$query"
    else
        docker exec -e PGPASSWORD="$password" "$pg" \
            psql -v ON_ERROR_STOP=1 -U "$role" -d "$postgres_db" -At -c "$query"
    fi
}

flyway_migrate() {
    if [ "$sidecar" -eq 1 ]; then
        command -v flyway >/dev/null || fail "flyway is required when PGHOST is set"
        flyway \
            -url="jdbc:postgresql://${PGHOST}:5432/${postgres_db}" \
            -user=core \
            -password="$core_pw" \
            -schemas=core \
            -defaultSchema=core \
            -locations="filesystem:$root/services/core/src/main/resources/db/migration" \
            -connectRetries=10 \
            migrate
    else
        docker create --name "$fw" --network "$net" \
            -e FLYWAY_URL=jdbc:postgresql://postgres:5432/"$postgres_db" \
            -e FLYWAY_USER=core \
            -e FLYWAY_PASSWORD="$core_pw" \
            -e FLYWAY_SCHEMAS=core \
            -e FLYWAY_DEFAULT_SCHEMA=core \
            flyway/flyway:10-alpine migrate >/dev/null
        docker cp "$root/services/core/src/main/resources/db/migration/." "$fw":/flyway/sql/
        docker start -a "$fw"
        docker start -a "$fw"
    fi
}

if [ "$sidecar" -eq 1 ]; then
    echo "Using sidecar Postgres at $PGHOST (no host ports)..."
    export PGUSER="${PGUSER:-$postgres_user}"
    export PGPASSWORD="${PGPASSWORD:-$postgres_pw}"
    export PGDATABASE="$postgres_db"
    i=0
    while [ "$i" -lt 60 ]; do
        if psql -h "$PGHOST" -U "$postgres_user" -d "$postgres_db" -At -c "SELECT 1" >/dev/null 2>&1; then
            break
        fi
        i=$((i + 1))
        sleep 1
    done
    CORE_DB_PASSWORD="$core_pw" NOTIFIER_DB_PASSWORD="$notifier_pw" \
        POSTGRES_USER="$postgres_user" POSTGRES_DB="$postgres_db" \
        sh "$root/infra/postgres/init-roles.sh"
else
    echo "Building the compose Postgres image (init-roles.sh in the image)..."
    docker network create "$net" >/dev/null
    docker build -t otmetka-postgres-ci "$root/infra/postgres" >/dev/null
    docker run -d --name "$pg" --network "$net" --network-alias postgres \
        -e POSTGRES_DB="$postgres_db" \
        -e POSTGRES_USER="$postgres_user" \
        -e POSTGRES_PASSWORD="$postgres_pw" \
        -e CORE_DB_PASSWORD="$core_pw" \
        -e NOTIFIER_DB_PASSWORD="$notifier_pw" \
        otmetka-postgres-ci >/dev/null
fi

echo "Waiting for roles from init-roles.sh..."
i=0
while [ "$i" -lt 60 ]; do
    if sql "$postgres_user" "$postgres_pw" "SELECT 1 FROM pg_roles WHERE rolname = 'core'" 2>/dev/null | grep -qx 1; then
        break
    fi
    i=$((i + 1))
    sleep 1
done
sql "$postgres_user" "$postgres_pw" "SELECT 1 FROM pg_roles WHERE rolname = 'core'" | grep -qx 1 \
    || fail "role core was not created (init-roles.sh did not run)"
sql "$postgres_user" "$postgres_pw" "SELECT 1 FROM pg_roles WHERE rolname = 'notifier'" | grep -qx 1 \
    || fail "role notifier was not created"
sql "$postgres_user" "$postgres_pw" "SELECT pg_get_userbyid(nspowner) FROM pg_namespace WHERE nspname = 'core'" \
    | grep -qx core || fail "schema core is not owned by role core"
sql "$postgres_user" "$postgres_pw" "SELECT pg_get_userbyid(nspowner) FROM pg_namespace WHERE nspname = 'notifier'" \
    | grep -qx notifier || fail "schema notifier is not owned by role notifier"

echo "Flyway migrate as role core (twice)..."
if [ "$sidecar" -eq 1 ]; then
    flyway_migrate
    flyway_migrate
else
    flyway_migrate
fi

flyway_count="$(sql core "$core_pw" "SELECT count(*) FROM core.flyway_schema_history WHERE success")"
[ "$flyway_count" = "8" ] || fail "expected 8 successful Flyway versions, got $flyway_count"

command -v uv >/dev/null || fail "uv is required to run Alembic (install, then uv sync in services/notifier)"

echo "Alembic upgrade head as role notifier (twice)..."
# Sidecar: the job is already on the GitLab network. Locally: the host can
# reach the container's address on the Docker bridge without publishing a port.
if [ "$sidecar" -eq 1 ]; then
    alembic_host="$PGHOST"
else
    alembic_host="$(docker inspect -f "{{(index .NetworkSettings.Networks \"$net\").IPAddress}}" "$pg")"
    [ -n "$alembic_host" ] || fail "Postgres has no address on $net"
fi
export DATABASE_URL="postgresql+psycopg://notifier:${notifier_pw}@${alembic_host}:5432/${postgres_db}"
export RABBITMQ_URL="amqp://guest:guest@127.0.0.1:5672/"
export INTERNAL_SECRET="ci-internal-secret"
(
    cd "$root/services/notifier"
    uv run alembic upgrade head
    uv run alembic upgrade head
)

alembic_head="$(sql notifier "$notifier_pw" "SELECT version_num FROM notifier.alembic_version")"
[ "$alembic_head" = "0003" ] || fail "expected Alembic head 0003, got $alembic_head"

expect_tables() {
    schema="$1"
    expected="$2"
    actual="$(sql "$postgres_user" "$postgres_pw" \
        "SELECT string_agg(tablename, ',' ORDER BY tablename) FROM pg_tables WHERE schemaname = '$schema' AND tablename NOT IN ('flyway_schema_history', 'alembic_version')")"
    [ "$actual" = "$expected" ] || fail "tables in $schema: expected $expected, got $actual"
}

expect_tables core "consents,entries,mark_history,outbox,plan_items,plans,questions,refresh_tokens,shares,users"
expect_tables notifier "deliveries,devices,entry_marks,plans,processed_events,schedules"

if sql core "$core_pw" "SELECT 1 FROM notifier.plans LIMIT 1" >/dev/null 2>&1; then
    fail "role core can read notifier.plans"
fi
if sql notifier "$notifier_pw" "SELECT 1 FROM core.users LIMIT 1" >/dev/null 2>&1; then
    fail "role notifier can read core.users"
fi

echo "OK: init-roles, Flyway V1–V8, Alembic 0001–0003, second apply is a no-op, roles cannot cross schemas"
