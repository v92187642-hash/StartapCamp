# Deploy

How to put the product on a server. Nothing here has been done yet: no hosting
provider is chosen (see `docs/state.md`). The server settings in
`infra/docker-compose.prod.yml` were started once on a developer machine with
`DOMAIN=localhost` (2026-10-09): only the proxy was reachable, the site
answered over HTTPS with a local certificate, and a login code was delivered
to a test SMTP server without login or encryption. A real domain and
certificate, a real mail server (login and STARTTLS) and real push credentials
have never been tried.

## If outgoing mail is blocked

Many hosting providers block outgoing connections on the mail ports. Check
from inside the `core` container before relying on SMTP:

```
timeout 5 bash -c '</dev/tcp/gitlab.com/443' && echo open || echo blocked
timeout 5 bash -c '</dev/tcp/77.88.21.158/465' && echo open || echo blocked
timeout 5 bash -c '</dev/tcp/77.88.21.158/587' && echo open || echo blocked
```

The address is Yandex's mail server. If the first line says open and the
other two blocked, the provider blocks mail. Then either ask its support to
open 465 or 587, or send through a mail service's HTTP API on port 443 (not
built yet; see `docs/state.md`). Renting a mail server does not help: the
block is on connections leaving this server, wherever they go.

## Dokploy

Compose files in `infra/` do not publish host ports. In Dokploy, point the
domain at service `web`, container port 80.

- Bundled Postgres / Redis / RabbitMQ: `infra/docker-compose.yml`, optionally
  layered with `infra/docker-compose.prod.yml`.
- Those three already exist as other Dokploy services:
  `infra/docker-compose.external.yml`, plus their hostnames in the
  environment (`DATABASE_URL`, `REDIS_HOST`, `RABBITMQ_HOST`,
  `NOTIFIER_DATABASE_URL`, `RABBITMQ_URL`) and `OTMETKA_NETWORK` if they sit
  on another Docker network.

Enter every required secret in Dokploy's environment screen (see
`infra/.env.example`). There are no defaults.

## What is needed

| Thing | Why |
|---|---|
| A server located in Russia, with Docker | 152-ФЗ: personal data of Russian citizens is stored in Russia |
| A domain pointing at the server | HTTPS certificate; links in emails; the address the Android app calls |
| An SMTP account | Invitations to relatives are emails. Login does not need email |
| Firebase project and/or RuStore Push project | Alerts to relatives' phones |
| Ports 80 and 443 open | Everything else stays closed |

2 CPU cores and 4 GB of memory are enough to start: the Java service takes
about 500 MB, everything else far less.

## First start

1. Copy the repository to the server.
2. Create `infra/.env`. There are no defaults on a server; the start fails and
   names the variable if one is missing.

   ```
   DOMAIN=otmetka.example.ru
   POSTGRES_PASSWORD=<long random>
   CORE_DB_PASSWORD=<long random>
   NOTIFIER_DB_PASSWORD=<long random>
   RABBITMQ_PASSWORD=<long random>
   JWT_SECRET=<at least 32 random characters>
   INTERNAL_SECRET=<long random>
   MAIL_HOST=smtp.example.ru
   # 587 with STARTTLS is the default. For port 465 add MAIL_SSL=true and
   # MAIL_STARTTLS=false.
   MAIL_PORT=587
   MAIL_USER=<smtp login>
   MAIL_PASSWORD=<smtp password>
   MAIL_FROM=no-reply@otmetka.example.ru
   # At least one of the two push providers:
   RUSTORE_PROJECT_ID=
   RUSTORE_SERVICE_TOKEN=
   FCM_SERVICE_ACCOUNT_FILE=
   ```

   `openssl rand -base64 36` makes a good random value. The three database
   passwords take effect only when the data volume is first created.
3. Start:

   ```
   docker compose -f infra/docker-compose.yml -f infra/docker-compose.prod.yml up -d --build
   ```

4. Check: `https://<DOMAIN>/api/health` answers `{"status":"ok"}` and the site
   opens. Register an account, invite an address of your own and see that the
   email arrives.

For Firebase, the service-account file has to be inside the `notifier`
container: add a volume for it to `docker-compose.prod.yml` and put its path
inside the container into `FCM_SERVICE_ACCOUNT_FILE`.

## The Android app

A release build must call the server, not the developer machine:

```
./gradlew assembleRelease -PapiBaseUrl=https://<DOMAIN>/api/
```

Release signing is not set up; the team creates and keeps the signing key (a
lost key means the app can never be updated in the store).

## Updating

```
git pull
docker compose -f infra/docker-compose.yml -f infra/docker-compose.prod.yml up -d --build
```

Database migrations run by themselves when `core` and `notifier` start. A
migration that fails stops that service from starting; the old data is intact.
Make a backup first.

## Backups

- `infra/scripts/backup.sh` writes `infra/backups/otmetka-<time>.dump`.
- `infra/scripts/restore-test.sh <file>` restores it into a throwaway database
  and compares row counts with the running one. Verified locally on 2026-10-09.
- `infra/scripts/restore.sh <file> --yes` replaces the running database.

On a server: run `backup.sh` nightly from cron, copy the file to another
machine in Russia, keep two weeks of files, and run `restore-test.sh` on a
real file once a month. Only Postgres needs a backup.

## Before real users

- The privacy policy and the consent text are drafts: `docs/legal/`. A lawyer
  must review them (milestone M5).
- Register as a personal-data operator with Роскомнадзор if the lawyer
  confirms it is required.
- There is no monitoring. At least watch disk space and
  `docker compose ps`; the dead-letter queue `notifier.events.dlq` in the
  RabbitMQ management UI shows events that could not be processed.
- The RabbitMQ management UI and the databases are not published on the host.
