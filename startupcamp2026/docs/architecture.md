# Architecture

Update this file in the same change as any boundary, table, endpoint or event
change.

## Overview

```
 apps/android (Kotlin)┐
                      ├─ HTTPS, JSON, JWT ─▶ services/core (Java, Spring Boot)
 apps/web (React)  ───┘                        │     │      │        │
                                               │     │      │ events │ report request
                                    schema     │     │login ▼        │ (the one HTTP call)
                                    "core"     │     │limits RabbitMQ │
                                               ▼     ▼      │        │
                                         PostgreSQL  Redis  │ events │
                                               ▲            ▼        ▼
                                    schema     │        services/notifier
                                    "notifier" └─────── (Python, FastAPI)
                                                                  │
                                                                  ▼
                                                           push providers
```

Two services, two clients. Clients talk only to `core`. `core` publishes events
to RabbitMQ; `notifier` consumes them. Postgres holds all permanent data, Redis
only short-lived data.

## Components

| Part | Stack | Port (local) | Responsibility |
|---|---|---|---|
| `services/core` | Java 21, Spring Boot 3, Gradle, Flyway, Spring AMQP | 8080 | Auth, consent, plans, diary entries, sharing, questions, premium gate, report data |
| `services/notifier` | Python 3.12, FastAPI, SQLAlchemy, Alembic, aio-pika, reportlab | 8000 | Alerts to relatives, device tokens, PDF rendering |
| `apps/web` | React, Vite, TypeScript; nginx in Docker | 80 in-container | Everything except reminders: login, plans, day view, sharing, questions, report, account |
| `apps/android` | Kotlin, Jetpack Compose, Room, WorkManager, min SDK 26 | — | Day view, marking, local reminders, offline marks |
| PostgreSQL 16 | — | 5432 in-container | Permanent data; one instance, one schema per service |
| RabbitMQ 4 | — | 5672 in-container | Events from `core` to `notifier` |
| Redis 7 | — | 6379 in-container | Failed-login counts; later live updates |

## Boundaries

These rules are what let any part be rewritten in another language.

1. `api/openapi.yaml` is the source of truth for the public API. Web and
   Android API clients are generated from it on every build, never
   hand-written and never committed.
2. `api/asyncapi.yaml` is the source of truth for events. A service may be
   rewritten freely as long as it publishes or consumes the same messages.
3. Each service owns one Postgres schema and its own migrations
   (`services/core` uses Flyway, `services/notifier` uses Alembic). No service
   reads or writes another service's schema. This is enforced: each service
   connects with its own Postgres role, which has no rights on the other
   schema (`infra/postgres/init-roles.sh`).
4. `core` and `notifier` communicate through RabbitMQ events. The one
   exception is report generation, which is a request with an immediate answer
   and uses an internal HTTP endpoint.
5. Only `core` uses Redis. Nothing permanent is stored there: losing Redis
   must cost no more than forgotten counts of failed logins.
6. Inside `core`, code is split into modules that depend on each other only
   through a module's public interface:

   ```
   ru.otmetka.core
     auth/      registration, passwords, tokens, consent, account, premium gate
     plans/     plans and plan items
     diary/     entries, marks, mark history
     sharing/   invitations and access checks
     questions/ questions for the doctor
     devices/   push-token registration (passes tokens on, stores nothing)
     reports/   collects report data, asks notifier to render it
     events/    turns in-process events into RabbitMQ messages, through the outbox
     common/    errors, time, mail
   ```

   A module's tables are used only by that module. Classes are package-private
   unless another module needs them. A module asks another one through a small
   public interface: `plans.PlanDirectory`, `diary.DiaryDirectory`,
   `questions.QuestionDirectory`, `sharing.AccessPolicy`, `auth.UserDirectory`,
   `auth.PremiumPolicy`. A module tells others that something happened by
   raising a plain event object in its own package (`plans.PlanChanged`,
   `diary.EntryMarked`, `auth.UserDeleting`, ...); nobody depends on `events`,
   which depends on everyone's public types. Foreign keys between modules'
   tables are kept for integrity and would be dropped when a module is lifted
   out into its own service.
7. The browser calls `core` under the path prefix `/api`, on the web client's
   own origin. nginx (Docker) or Vite (development) strips the prefix and
   forwards the request, so `core` needs no CORS configuration. Android calls
   `core` directly (`/api/` on the public domain in production).

## Key design choices

- Reminders fire on the phone. The Android app schedules local alarms from the
  plan. Server push is used only for alerts to the relative. Missed
  notifications were the top complaint about every competing app.
- The Android app keeps its own copy of plans and marks in Room (SQLite) so it
  works offline and syncs when the network returns. A mark carries the time
  of the tap, not the time it reached the server.
- "No mark" is not stored. An entry is `planned` or `marked`; it is shown as
  "no mark" when it is `planned` and its scheduled time has passed.
- Entries are created ahead: adding a plan item inserts one entry for every
  matching day from that moment to the end of the plan. Moments already past
  get no entry. A plan is at most 366 days and 20 items.
- Plan items have no user-typed label. The server names them «Назначение N»,
  so no drug name can be stored there. Free text exists in two places only:
  the plan title and questions for the doctor.
- Marks are never deleted. `entries` holds the current state; `mark_history`
  is an append-only record of every mark, correction and undo.
- All times are stored in UTC; each plan carries its own time zone, and every
  client shows times in the plan's zone.
- `notifier` decides when an alert is due. It keeps its own copy of the
  schedule, built from events, in a Postgres table that it polls. RabbitMQ
  carries events, not timers.
- Push providers sit behind one interface in `notifier`
  (`app/push/base.py`), because many phones in Russia have no Google
  services. Firebase and RuStore Push are implemented; by default nothing is
  sent and pushes are written to the log (`PUSH_DRY_RUN`).
- The product never advises. A missed entry offers "write a question for the
  doctor"; an alert states a fact («Нет отметки…»); the report lists marks.

## Events

The full contract lives in `api/asyncapi.yaml`; this is a summary.

One durable topic exchange, `otmetka.events`, declared by `core`. `notifier`
declares and binds one durable queue, `notifier.events`. A message it cannot
parse or apply is rejected and dead-lettered through the exchange
`otmetka.events.dlx` into the queue `notifier.events.dlq`; nothing retries it
automatically.

Every message is a JSON envelope: `eventId`, `type` (equal to the routing
key), `occurredAt`, `data`.

| Routing key | Published when | Payload | `notifier` does |
|---|---|---|---|
| `plan.changed` | A plan, its items or its shares change | Full snapshot: plan id, version, time zone, planned entries due later than 24 hours ago, recipients | Replaces the plan's pending schedule rows if the version is newer |
| `entry.marked` | An entry is marked, corrected or a mark is undone | Entry id, plan id, status, mark time | Stores the newest mark state per entry |
| `device.registered` | A client registers a push token | User id, provider, token | Stores or moves the token |
| `plan.deleted` | A plan is deleted (today: with its owner's account) | Plan id | Forgets the plan; later snapshots of it are ignored |
| `user.deleted` | An account is deleted | User id | Forgets the user's devices |

Rules for every event:

- A consumer must tolerate the same message arriving twice and messages
  arriving out of order. Duplicates: every handler first records the event id
  in `processed_events`. Order: `plan.changed` carries a version, and
  `entry.marked` is compared by `occurredAt`; the older one loses.
- `core` never loses an event and never announces a change that did not
  happen. A module raises a Spring application event inside its transaction;
  the `events` module writes the message into the table `outbox` in that same
  transaction. `events/OutboxPublisher` sends the rows in order, on a background
  thread right after the commit and again every few seconds, and marks a row as published only
  when RabbitMQ has confirmed it. So a message may arrive twice, with the same
  `eventId`, and may arrive late.
- `plan.changed` is stored in the outbox as a plan id only; the snapshot is
  built at the moment of sending, from committed data. Whenever it is sent, it
  describes the plan as it is.
- Event payloads carry ids and times only: no labels, no health data.

## Data model

Schema `core` (Flyway, `services/core/src/main/resources/db/migration`):

| Table | Columns | Notes |
|---|---|---|
| `users` | `id`, `email`, `display_name`, `password_hash`, `premium`, `created_at` | `email` is stored trimmed and lower-case; `password_hash` is BCrypt, and null for an account made before passwords existed, which cannot log in |
| `consents` | `id`, `user_id`, `kind`, `text_version`, `accepted_at` | Without a row, every endpoint but login, consent and `/me` answers 403 |
| `refresh_tokens` | `id`, `user_id`, `token_hash`, `expires_at`, `created_at` | Only the hash; a token works once and is replaced |
| `plans` | `id`, `owner_id`, `title`, `starts_on`, `ends_on`, `timezone`, `version`, `created_at` | `version` grows on every change to the plan, its items or its shares |
| `plan_items` | `id`, `plan_id`, `label`, `time_of_day`, `days_of_week`, `position` | `label` is generated; `days_of_week` holds ISO day numbers, 1 is Monday |
| `entries` | `id`, `plan_item_id`, `scheduled_at`, `status`, `marked_at`, `marked_by`, `corrected_at` | `status`: `planned` or `marked`; unique on (`plan_item_id`, `scheduled_at`) |
| `mark_history` | `id`, `entry_id`, `action`, `marked_at`, `actor_id`, `at` | Append-only; `action`: `marked`, `corrected`, `undone` |
| `shares` | `id`, `plan_id`, `invited_email`, `member_id`, `role`, `status`, `token_hash`, `expires_at`, `created_at` | `role`: `viewer`; the invitation token is stored hashed, works once, lasts 7 days |
| `questions` | `id`, `plan_id`, `author_id`, `text`, `created_at`, `asked_at` | Free text; never interpreted |
| `outbox` | `id`, `seq`, `type`, `data`, `occurred_at`, `published_at` | Events waiting to be sent; `id` is the message's `eventId`; published rows are deleted after a week |

Deleting a plan removes its items, entries, history, shares and questions by
cascade.

Schema `notifier` (Alembic, `services/notifier/alembic/versions`):

| Table | Columns | Notes |
|---|---|---|
| `plans` | `plan_id`, `version`, `timezone`, `updated_at` | Newest snapshot version seen per plan; a deleted plan keeps a row with the largest version |
| `schedules` | `entry_id`, `plan_id`, `plan_version`, `due_at`, `recipients`, `state` | Built from `plan.changed`; `state`: `pending`, `alerted`, `skipped` |
| `entry_marks` | `entry_id`, `status`, `occurred_at` | Built from `entry.marked`; separate from `schedules` because the two events arrive in any order |
| `devices` | `id`, `user_id`, `provider`, `token`, `updated_at` | `provider`: `fcm` or `rustore`; a token belongs to one user |
| `deliveries` | `id`, `entry_id`, `device_id`, `sent_at`, `result` | One row per push attempt |
| `processed_events` | `event_id`, `processed_at` | Lets the consumer skip duplicates |

Redis, used by `core`:

| Key | Value | Expiry |
|---|---|---|
| `login:fail:{email}` | Count of failed logins since the last successful one | 15 minutes |
| `register:ip:{ip}` | Count of registrations from that client address | 60 minutes |

## API outline

The full contract lives in `api/openapi.yaml`; this is a summary. Everything
listed is implemented.

Public, served by `core`:

| Method and path | Purpose |
|---|---|
| `GET /health` | Liveness check, no token |
| `POST /auth/register` | Create an account with an email and a password; answers with tokens |
| `POST /auth/login` | Exchange email and password for tokens |
| `POST /auth/refresh` | Exchange a refresh token for new tokens |
| `POST /auth/logout` | Revoke one refresh token |
| `POST /auth/logout-all` | Revoke every refresh token of the caller |
| `GET /me` | The caller's account |
| `POST /me/delete` | Delete the account; body is the current password |
| `POST /consents` | Record consent |
| `GET /plans`, `POST /plans` | List (own and shared) and create plans |
| `GET /plans/{id}` | Plan with its items and the caller's role |
| `POST /plans/{id}/items` | Add an item (owner) |
| `GET /plans/{id}/entries?date=&days=` | Day view; up to 31 days at once |
| `PUT /entries/{id}/mark` | Mark an entry, or correct the time of a mark (owner, idempotent) |
| `DELETE /entries/{id}/mark` | Undo a mark (owner, idempotent) |
| `GET /plans/{id}/shares`, `POST /plans/{id}/shares` | List and invite relatives (owner; inviting is premium) |
| `DELETE /shares/{id}` | Withdraw an invitation or access (owner) |
| `POST /shares/accept` | Accept an invitation (must be logged in as the invited email) |
| `GET /plans/{id}/questions`, `POST /plans/{id}/questions` | Questions for the doctor (owner and relatives) |
| `PUT /questions/{id}/asked`, `DELETE /questions/{id}/asked` | Mark as asked or not asked (owner) |
| `DELETE /questions/{id}` | Delete a question (author or owner) |
| `POST /devices` | Register a push token (published as `device.registered`) |
| `GET /plans/{id}/report?from=&to=` | PDF summary (premium; rendered by `notifier`) |

Errors are `application/problem+json` with a Russian `detail`. Two problem
types matter to clients: `consent-required` and `premium-required` (both 403).

Internal HTTP, served by `notifier`, called by `core`, authenticated with the
shared secret `INTERNAL_SECRET` in the header `X-Internal-Secret`:

| Method and path | Purpose |
|---|---|
| `POST /internal/reports` | Build a PDF from the data `core` sends; nothing is stored |

`core` makes this call over plain HTTP/1.1 on purpose: Java's newer HTTP
client offers an upgrade to HTTP/2 first, and uvicorn then loses the body.

## Authentication and access

- Login: email plus a password, in `auth.PasswordLogin`. An account is made
  by `POST /auth/register`; the address is not confirmed by email. A password
  is 8 characters to 72 bytes and is stored only as a BCrypt hash. More than
  ten registrations from one client IP in an hour are refused.
- An unknown email and a wrong password get the same answer. Ten failed
  logins for one email block further attempts for 15 minutes; the count is in
  Redis and a successful login clears it.
- Access JWTs carry issuer and audience `otmetka`. `POST /auth/logout` drops
  one refresh token; `POST /auth/logout-all` drops every refresh token of the
  caller. `POST /me/delete` needs the current password.
- An invitation can be accepted only by the account whose email is the
  invited address.
- There is no password reset and no password change yet.
- Test accounts, in `auth.TestAccounts`: with `SEED_TEST_ACCOUNTS=true`
  `core` creates `testaccount1@otmetka.test` to `testaccount6@otmetka.test`
  at start if they are missing (passwords `otmetka-test-1` to
  `otmetka-test-6`, consent recorded, all premium). It never deletes or
  changes an existing one. Addresses at `otmetka.test` cannot be registered.
  The local compose file sets `true`; the server one `false`.
- Proving who the user is lives in one class (`auth.PasswordLogin`); phone
  or external login would be a class next to it, and other modules would not
  change.
- Emails (invitations) go through `common.Mailer`: written to the log
  by default, sent over SMTP when `MAIL_ENABLED=true`.

## Alerts

`notifier` runs a poller (`app/alerts.py`) every `ALERT_POLL_SECONDS`. An
entry is alerted when its schedule row is `pending`, its time passed more than
`ALERT_GRACE_MINUTES` ago and `entry_marks` does not say it is marked. Each
recipient's devices get one push through the provider of the device; the row
becomes `alerted` in the same transaction that records the deliveries, so an
entry is alerted once. An entry overdue by more than `ALERT_MAX_AGE_HOURS` is
`skipped` without an alert. A token the provider reports dead is removed.

## Android app

- Screens (Compose, no navigation library): login, consent, plans, new plan,
  day view, plan settings (items, relatives), questions and report, account,
  battery guide. Large type by default. The app does everything the web
  client does except accepting an invitation link.
- `data/DiaryRepository`: screens read only Room; `refresh` loads plans and
  nine days of entries; `mark`, `correct` and `unmark` write to Room with a
  pending flag.
- Only the diary works offline. Creating a plan, items, sharing, questions,
  the report and the account screen call `core` directly and need a network
  (`data/SharingRepository`, `data/QuestionsRepository`).
- The report is downloaded first and then saved where the user chooses in
  the system's file dialog, so the app needs no storage permission.
- `data/SyncWorker` (WorkManager, needs network): sends pending marks, then
  refreshes, then sets the alarms again. Runs after every mark, when the app
  opens, and every six hours.
- `reminders/ReminderScheduler`: one exact alarm per upcoming unmarked entry
  of the user's own plans, at most 60. `ReminderReceiver` shows the
  notification and handles its «Отметить» action. `BootReceiver` sets alarms
  again after a restart or an app update.
- Not built: registering a push token and receiving alerts (needs provider
  accounts), and opening an invitation link; a relative accepts the
  invitation on the web and then sees the plan in the app.

## Local environment

One command starts everything (Postgres, RabbitMQ, Redis, `core`, `notifier`,
`web`):

```
docker compose -f infra/docker-compose.yml up
```

Configuration comes from environment variables listed in `infra/.env.example`.
Secrets have no defaults: copy that file to `infra/.env` and fill them, or
the stack will not start. In local development invitation links are written
to the `core` log instead of being emailed, and pushes to the `notifier` log.
Compose does not publish host ports; point the platform proxy at `web:80`.
When Postgres, Redis and RabbitMQ run as separate services, use
`infra/docker-compose.external.yml` and set `DATABASE_URL`, `REDIS_HOST`,
`RABBITMQ_HOST` (and `NOTIFIER_DATABASE_URL`, `RABBITMQ_URL` for notifier).

Server settings are a second file layered on top,
`infra/docker-compose.prod.yml`; see `docs/deploy.md`.

## Room left for later

| Need | How it fits |
|---|---|
| Rewrite a service in another language | Keep `api/openapi.yaml` and `api/asyncapi.yaml`; clients and the other service do not change |
| More services | Lift a `core` module out; its schema and interface are already separate. New consumers bind their own queue to `otmetka.events` |
| Live updates on the relative's screen | Redis publish/subscribe from `core` |
| ML features | New consumer or endpoints in `notifier`, or a third Python service |
| Other login methods | A class next to `auth.PasswordLogin`, called from `AuthService` |
| Another push provider | One class with `send` in `notifier/app/push`, one line in `registry.py` |
| Payment | Whatever sets `users.premium`; nothing else reads payment state |
| iOS | Another client generated from the same contract |
