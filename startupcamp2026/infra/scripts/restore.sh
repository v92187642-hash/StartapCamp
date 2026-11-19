#!/bin/sh
# Replaces the database of the running environment with a backup.
# Everything written after the backup was made is lost.
#
#   infra/scripts/restore.sh infra/backups/otmetka-20261009-010203.dump --yes
set -e
file="$1"
if [ ! -f "$file" ] || [ "$2" != "--yes" ]; then
    echo "usage: $0 <backup file> --yes" >&2
    echo "This overwrites the current database." >&2
    exit 1
fi
file="$(cd "$(dirname "$file")" && pwd)/$(basename "$file")"
cd "$(dirname "$0")/.."

# The services hold connections and would write while the data is replaced.
docker compose stop core notifier
docker compose exec -T postgres pg_restore -U postgres -d otmetka --clean --if-exists --exit-on-error < "$file"
docker compose start core notifier
echo "Restored from $file"
