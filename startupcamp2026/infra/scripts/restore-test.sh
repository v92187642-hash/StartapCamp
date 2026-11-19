#!/bin/sh
# Proves that a backup can be restored: loads it into a throwaway Postgres and
# compares the number of rows in every table with the running database.
#
#   infra/scripts/restore-test.sh infra/backups/otmetka-20261009-010203.dump
#
# A backup that was never restored is not a backup. Run this after changing
# the backup procedure and from time to time on a real backup file.
set -e
file="$1"
if [ ! -f "$file" ]; then
    echo "usage: $0 <backup file>" >&2
    exit 1
fi
file="$(cd "$(dirname "$file")" && pwd)/$(basename "$file")"
cd "$(dirname "$0")/.."

name="otmetka-restore-test-$$"
cleanup() { docker rm -f "$name" > /dev/null 2>&1 || true; }
trap cleanup EXIT

docker run -d --name "$name" -e POSTGRES_PASSWORD=test -e POSTGRES_DB=otmetka postgres:16-alpine > /dev/null
until docker exec "$name" pg_isready -h 127.0.0.1 -U postgres -d otmetka > /dev/null 2>&1; do sleep 1; done
# The dump names the roles that own the schemas.
docker exec "$name" psql -q -U postgres -d otmetka -c "CREATE ROLE core; CREATE ROLE notifier;"
docker exec -i "$name" pg_restore -U postgres -d otmetka --exit-on-error < "$file"

# One line per table: schema.table=rows
counts='SELECT string_agg(format($f$SELECT %L || $s$=$s$ || count(*) FROM %I.%I$f$, schemaname || $s$.$s$ || tablename, schemaname, tablename), $s$ UNION ALL $s$ ORDER BY schemaname, tablename) FROM pg_tables WHERE schemaname IN ($s$core$s$, $s$notifier$s$)'
count_rows() { "$@" psql -U postgres -d otmetka -At -c "$("$@" psql -U postgres -d otmetka -At -c "$counts")" | sort; }

restored="$(count_rows docker exec "$name")"
live="$(count_rows docker compose exec -T postgres)"

echo "$restored"
tables="$(echo "$restored" | wc -l)"
if [ "$restored" = "$live" ]; then
    echo "OK: $tables tables restored, row counts equal the running database"
else
    echo "Restored copy differs from the running database (expected if data changed since the backup):"
    echo "--- running database"
    echo "$live"
    exit 2
fi
