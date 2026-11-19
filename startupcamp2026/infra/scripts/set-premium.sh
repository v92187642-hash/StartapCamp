#!/bin/sh
# Switches the premium flag of an account in the local environment.
# There is no payment integration yet; this is how a test account becomes paying.
#
#   infra/scripts/set-premium.sh someone@example.com true
#   infra/scripts/set-premium.sh someone@example.com false
set -e
email="$1"
value="${2:-true}"
if [ -z "$email" ] || { [ "$value" != "true" ] && [ "$value" != "false" ]; }; then
    echo "usage: $0 <email> [true|false]" >&2
    exit 1
fi
cd "$(dirname "$0")/.."
docker compose exec -T postgres psql -U postgres -d otmetka -v email="$email" -v value="$value" <<'SQL'
UPDATE core.users SET premium = :'value'::boolean WHERE email = lower(:'email') RETURNING email, premium;
SQL
