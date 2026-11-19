#!/bin/sh
# Writes a backup of the whole database to infra/backups/.
#
#   infra/scripts/backup.sh
#
# Postgres holds all permanent data. RabbitMQ and Redis need no backup: Redis
# keeps only failed-login counts, and a lost queue costs at most a few late alerts.
# On a server, run this from cron every night and copy the file elsewhere.
set -e
cd "$(dirname "$0")/.."
mkdir -p backups
file="backups/otmetka-$(date -u +%Y%m%d-%H%M%S).dump"
# Custom format: compressed, and pg_restore can restore single tables from it.
docker compose exec -T postgres pg_dump -U postgres -d otmetka --format=custom > "$file"
echo "$(pwd)/$file"
