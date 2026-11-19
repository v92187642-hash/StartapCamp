# infra

The local environment: Postgres, RabbitMQ, Redis, `core`, `notifier` and `web`.

## Run

Copy the example env, fill every required secret, then start from the
repository root:

```
cp infra/.env.example infra/.env
# set POSTGRES_PASSWORD, CORE_DB_PASSWORD, NOTIFIER_DB_PASSWORD,
# RABBITMQ_PASSWORD, JWT_SECRET, INTERNAL_SECRET (openssl rand -base64 36)
docker compose -f infra/docker-compose.yml up --build
```

Compose does not publish ports on the host. Containers reach each other by
service name (`core:8080`, `web:80`, `postgres:5432`, …). Point Dokploy or
another proxy at **`web`, container port 80**.

Named volumes (`postgres-data`, `rabbitmq-data`, `redis-data`) are Docker
volumes (`driver: local`), not bind mounts of a path on the VPS. In some UIs
an empty volume mapping is shown as `postgres-data: null` — that still means
a named volume, not a host folder.

The first start downloads images and builds the services, which takes several
minutes. It is ready when all six containers are healthy:

```
docker compose -f infra/docker-compose.yml ps
```

Stop with Ctrl+C. To also delete the data (database and queues):

```
docker compose -f infra/docker-compose.yml down -v
```

## Logging in

Register in the web client with any email and a password. Test accounts are
**not** created unless `SEED_TEST_ACCOUNTS=true` in `.env`:
`testaccount1@otmetka.test` … `testaccount6@otmetka.test`,
passwords `otmetka-test-1` … `otmetka-test-6`, all premium. Do not enable that
on a reachable server.

Invitation links are not emailed locally. They are in the `core` log:

```
docker compose -f infra/docker-compose.yml logs core | grep -A2 "Email to"
```

Alerts are not pushed to phones locally either; they are in the `notifier`
log (`grep "Push"`).

## Scripts

| Script | What it does |
|---|---|
| `scripts/set-premium.sh <email> [true\|false]` | Makes an account paying; there is no payment yet |
| `scripts/backup.sh` | Writes the database to `infra/backups/` (via `docker compose exec`) |
| `scripts/restore-test.sh <file>` | Restores a backup into a throwaway database and compares row counts |
| `scripts/restore.sh <file> --yes` | Replaces the running database with a backup |

## On a server

`docker-compose.prod.yml` is layered over `docker-compose.yml`: real email
and push, restart policies. Still no host ports. See `docs/deploy.md`.

When Postgres, Redis and RabbitMQ are provided by the platform, use
`infra/docker-compose.external.yml` and set `DATABASE_URL`, `REDIS_HOST`,
`RABBITMQ_HOST`, `NOTIFIER_DATABASE_URL` and `RABBITMQ_URL`. Join their
Docker network with `OTMETKA_NETWORK` and `OTMETKA_NETWORK_EXTERNAL=true`.
That Postgres must already have the roles from `postgres/init-roles.sh`.

## Only the infrastructure

When you run a service from your IDE, start only what it needs. Those
processes then have to reach Postgres/Redis/Rabbit by a hostname that
resolves from the host (or you temporarily publish a port yourself):

```
docker compose -f infra/docker-compose.yml up postgres rabbitmq redis
```

## Configuration

Every variable is listed in `.env.example`. There are no default secrets.

## Postgres roles

`postgres/init-roles.sh` is copied into the Postgres image and runs once,
when the data volume is created. It makes one role and one schema per
service.

## Troubleshooting

- Image downloads fail with a network error: Docker Hub is not always
  reachable. Run the command again, or configure a registry mirror in Docker.
- A changed database password has no effect: passwords are applied only when
  the volume is created. Run `down -v` and start again.
- "JWT_SECRET must be set": `infra/.env` is missing or a required variable is
  empty.
