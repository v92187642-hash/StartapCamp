# Decisions

Newest first. Each entry: what was decided, when, and why. Add an entry
whenever a choice is made that a future session might question. Do not reopen
a decision without a new reason.

## 2026-10-10 — CI Postgres is a sidecar, not Testcontainers on the host socket

The owner does not want CI to publish database ports on the runner host.
GitLab `services:` put Postgres (and Redis for `core`) on the job's private
network; tests set `OTMETKA_TEST_DATABASE=service` and skip Testcontainers.
Local `./gradlew test` / `pytest` still start Testcontainers. The runner
does not need the Docker socket or `host.docker.internal`. How to register
the Runner: `docs/ci.md`.

## 2026-10-10 — CI runs on a GitLab Runner on the owner's machine

The repository is on GitLab, so GitHub Actions never ran. The owner asked
for pipelines on every push, on their own hardware. Jobs are tagged
`otmetka`. Shared GitLab.com runners are not used, so a forgotten tag
cannot spend minutes on someone else's Docker. How to register the Runner:
`docs/ci.md`.

## 2026-10-10 — Secrets have no defaults; compose publishes no host ports

Asked for by the project owner after a security review of the site. Compose
no longer ships `JWT_SECRET` / `INTERNAL_SECRET` / database passwords, and
`core` / `notifier` refuse to start if they are missing or too short. Host
`ports:` were removed: other containers still reach each other by service
name; Dokploy (or another proxy) maps `web:80`. Postgres init is copied into
the image so the VPS is not bind-mounted. A second file,
`infra/docker-compose.external.yml`, talks to Postgres, Redis and RabbitMQ
that already exist, through `DATABASE_URL`, `REDIS_HOST`, `RABBITMQ_HOST`
(and the matching Python URLs). Tokens stay in the browser's localStorage
because Android still uses Bearer; CSP, invite-email binding, register
rate-limit, password-confirmed deletion and logout of refresh tokens close
the holes that did not need a new login method.

## 2026-10-09 — Login is email and password; the email code is removed

Asked for by the project owner ("a classic registration with login by email
and password"). This replaces "Email plus one-time code for login" below. The
code login could not work on the first server at all: the hosting provider
blocks outgoing mail. The owner chose to replace the code, not to keep both,
and to leave password reset for later. Costs accepted: an address is not
confirmed, so anyone can register with any address, including one that an
invitation was sent to; a forgotten password cannot be recovered; older users
have a password to remember. Email confirmation and reset both need working
mail and can be added then. Accounts made by the code login have no password
and cannot log in; on 2026-10-09 those were test data only.

## 2026-10-09 — Six test accounts, created by `SEED_TEST_ACCOUNTS`, never deleted

Asked for by the project owner, who chose a setting over "always", then said
the accounts will be used on the server for a long time and must not be
interfered with, and that all should be premium. A sixth was added the same day; a
server that already has five gets only the missing one at its next start. So the setting only
creates missing accounts; nothing deletes or resets one, and `false` leaves
existing ones alone. A first version deleted them at start when the setting
was `false`; that was removed the same day, because one restart with the
wrong value would have erased the owner's test data. The cost: their
passwords are written in the repository, so a server that has them has five
premium accounts anyone can enter. Before real users arrive, delete each from
its account page. The domain `otmetka.test` cannot be registered by users.

## 2026-10-09 — Events go through an outbox table

Asked for by the project owner after reading that an event was lost when
RabbitMQ was down at the moment of a change. The message is now written to
`core.outbox` in the same transaction as the change and sent until the broker
confirms it. This replaces "Events are published after commit, without an
outbox" below. Costs accepted: one more table and a timer in `core`, and a
message can arrive twice. The duplicate cannot be removed on the sending
side: the database and the broker are two systems, and after a crash between
"sent" and "recorded as sent" the only safe choice is to send again. It is
made harmless on the receiving side, where every consumer skips an `eventId`
it has already seen. Sending happens on a background thread, so a request
takes the same time whether the broker is up or down.

## 2026-10-09 — Claude builds every stage it can

The project owner, leaving for the night, asked Claude to "do everything you
can (every stage)". This replaces "Claude builds to scaffold level 2" below.
The cost that entry warned about is now real: the team did not write this
code and has to read it before it can own and explain it. Each part's
`README.md` gives a reading order.

## 2026-10-09 — Plan items are named by the server, not typed

«Назначение 1», «Назначение 2». The limit "no drug names stored" cannot be
kept by asking nicely in a free-text field. The plan title stays free text
with a hint; whether that is acceptable is a question for the lawyer. To let
users type labels later: accept `label` in `PlanItemCreate` and in
`plans/PlanItem`.

## 2026-10-09 — Entries are created when an item is added

One row per matching day from that moment to the end of the plan (the option
recommended in `docs/state.md` at the end of M0; the owner was away, so
Claude took it). The day view and the snapshot for `notifier` become plain
queries. The price: plans are capped at 366 days and 20 items, and changing
an item's time later means rewriting future entries (not built).

## 2026-10-09 — No entry for a moment that has already passed

A plan created at 15:00 with an item at 08:00 starts tomorrow. Otherwise
every new plan would open with a "no mark" and alert the relative at once.

## 2026-10-09 — `mark_history` table added

`entries` alone could not keep the promise "marks are never deleted": an undo
erased the time of the mark. The history is append-only.

## 2026-10-09 — A stranger gets 404, not 403

Asking about a plan one has no access to answers "not found", so its
existence is not revealed. 403 is kept for a relative trying to change
something.

## 2026-10-09 — Refresh tokens live in Postgres, hashed, single use

Redis was the alternative, but a Redis restart would log every user out, and
older users should rarely have to log in. 90 days. A refresh token is
replaced on every use; if a phone loses the answer to a refresh call, that
phone has to log in again.

## 2026-10-09 — The account is created before consent, with the email only

Consent is recorded per user, so a user row must exist to show the consent
screen to. Until consent, nothing else can be stored: every other endpoint
answers 403 `consent-required`. Question 5 for the lawyer.

## 2026-10-09 — Premium is checked on the caller's account

Simple and enough for the M4 "done when". It does not match the pitch, where
the relative pays: see the open decision in `docs/state.md`.

## 2026-10-09 — In-process events live in the module that raises them

`diary.EntryMarked`, `plans.PlanChanged` and so on. The `events` module
depends on all modules and none depends on it; before, `diary` imported from
`events` and `events` would have needed `diary`.

## 2026-10-09 — `notifier` keeps mark state apart from the schedule

`entry_marks` is filled from `entry.marked`, `schedules` from `plan.changed`.
The two arrive in any order; with one table, a mark that arrived before its
schedule row had nowhere to go.

## 2026-10-09 — One alert per entry, and none for old entries

No repeats and no escalation: an alert is a fact, not pressure. Entries
overdue by more than 12 hours are skipped, so an outage does not end with a
burst of alerts about yesterday.

## 2026-10-09 — Pushes are dry-run by default

`PUSH_DRY_RUN=true` unless the server settings say otherwise, so a developer
machine can never notify a real phone.

## 2026-10-09 — Account deletion added, with `plan.deleted` and `user.deleted`

Not in the plan. The consent text says data can be deleted, and 152-ФЗ gives
that right, so there has to be a way. Deleting an account is the only way to
delete a plan for now.

## 2026-10-09 — The Android app has no navigation library and no DI framework

Five screens and a handful of long-lived objects (`OtmetkaApp`). Fewer
concepts for developers new to Kotlin; add Navigation and Hilt when the
screen count makes the `when` in `MainActivity` uncomfortable.

## 2026-10-09 — Android alarms are exact, with `USE_EXACT_ALARM`

Reminders are the product's core promise. RuStore has no policy against the
permission; Google Play restricts it to alarm and calendar apps, which is an
issue only if the app is ever published there.

## 2026-10-09 — The report call uses HTTP/1.1

`core`'s default Java HTTP client offers an HTTP/2 upgrade, and uvicorn then
drops the request body. Found only when the two real services met; the unit
tests on both sides passed.

## 2026-10-09 — Events are published after commit, without an outbox (replaced, see "Events go through an outbox table")

`core` sends an event to RabbitMQ in an after-commit listener. If the broker
is unreachable at that moment, the database change stays and the event is
lost (it is logged). An outbox table would close that gap at the cost of a
table, a poller and more code for the team to understand. Accepted for now
because nothing acts on events before M3; revisit when alerts depend on them.

## 2026-10-09 — `notifier` keeps a `plans` table for snapshot versions

Not in the first data model. Without it the stored version of a plan lived
only on its `schedules` rows, so a plan with no upcoming entries had no
version and a late, older `plan.changed` could bring removed entries back.

## 2026-10-09 — A failed message goes to the dead-letter queue, with no retry

`notifier` rejects any message it cannot parse or apply. Requeueing would
redeliver a broken message forever. The cost: a message that failed only
because Postgres was briefly down must be moved back by hand.

## 2026-10-09 — One Postgres role per service

Locally each service logs in with a role that owns only its schema, so the
"no reading the other service's tables" rule fails loudly instead of relying
on review.

## 2026-10-09 — Access tokens are HS256 JWTs with a shared secret

One service issues and validates them, so a key pair adds nothing yet. If
`notifier` or another service ever has to validate user tokens, switch to an
asymmetric key so that only `core` can sign.

## 2026-10-09 — The web client reaches `core` through `/api` on its own origin

nginx in Docker and Vite in development forward `/api/*` to `core`. This
avoids CORS configuration in `core` and keeps the token on one origin.

## 2026-10-09 — Generated API clients are not committed

Web (`openapi-typescript` + `openapi-fetch`) and Android (OpenAPI Generator,
Kotlin, Retrofit) regenerate from `api/openapi.yaml` on every build, so a
client cannot drift from the contract. `core` is hand-written against the
contract; nothing verifies it automatically yet.

## 2026-10-09 — Tests run against a real Postgres in Docker

Both services use Testcontainers. The worked examples rely on Postgres
behaviour (row locks, `ON CONFLICT`, arrays, schemas) that an in-memory
substitute would not reproduce. Docker is therefore required to run tests.

## 2026-10-09 — Tool versions

Spring Boot 3.5, Gradle 8.14, Android Gradle Plugin 8.13 with Kotlin 2.2,
TypeScript 5. Newer majors exist (Gradle 9, AGP 9, TypeScript 7) but were
skipped: `openapi-typescript` 7 does not run on TypeScript 7, and the older
lines are the ones most tutorials the team will read are written for.

## 2026-10-09 — RabbitMQ and Redis are part of the stack from M0

Chosen by the project owner, for scaling and so that services share only a
message format. RabbitMQ carries events from `core` to `notifier`; Redis holds
login codes and rate limits. Each has one narrow job; Postgres stays the only
store of permanent data. This replaces the earlier "no broker" decision below.
Claude's advice was that neither is needed at the current scale; the owner
accepted the extra operating cost.

## 2026-10-09 — Backend stays split: Java `core`, Python `notifier`

An all-Python backend was considered, since it would balance the workload (the
Java developers also build the Android app). The project owner kept the split.
A later rewrite of `core` in Python remains possible because clients depend
only on `api/openapi.yaml` and `notifier` only on `api/asyncapi.yaml`; the
cost of that rewrite grows with every feature added to `core`.

## 2026-10-09 — Alert timing lives in Postgres, not in RabbitMQ

RabbitMQ cannot hold a message until a future time without extra plugins.
`notifier` keeps the schedule in its own table and polls it.

## 2026-10-09 — Claude builds to scaffold level 2 (replaced, see "Claude builds every stage it can")

A skeleton plus one worked example per service, and the web client in full.
The team should own and be able to explain its code, so Claude does not write
the whole first milestone. Details in `docs/plan.md`.

## 2026-10-09 — Android app is written in Kotlin

Chosen by the project owner, replacing an earlier choice of Java made the same
day. Jetpack Compose is Kotlin-only, Android documentation and samples are
Kotlin-first, and the three Java developers can move to it quickly because it
shares the JVM, libraries and build tool. `core` stays Java.

## 2026-10-09 — Two services, not many

`core` in Java and `notifier` in Python, matching the team's two language
groups (3 Java, 2 Python). The project owner wanted microservices so any block
can be rewritten in another language. That goal is met by a contract-first API,
a schema per service and module boundaries inside `core`; more services would
add deploy and debugging cost for a five-person team without adding that
ability.

## 2026-10-09 — Native Android first, web is the small secondary client

Nobody on the team does frontend, and three people can write Android. The web
client is kept small and is built by Claude.

## 2026-10-09 — Reminders fire on the phone, not from the server

Local alarms on the device are more reliable than server push. Missed
notifications were the top complaint in reviews of every competing app.
Server push is reserved for alerts to the relative.

## 2026-10-09 — One Postgres instance, one schema per service

Simple to run locally, and each schema can move to its own database later
because no service reads another's tables.

## 2026-10-09 — Email plus one-time code for login (replaced, see "Login is email and password")

No passwords to store or forget, which suits older users. SMS was rejected for
now because each message costs money. The login method sits behind an
interface.

## 2026-10-09 — No gateway or Kubernetes yet

Add heavier infrastructure only when a milestone needs it. (This entry
originally also ruled out a message broker; that part was replaced the same
day, see the RabbitMQ entry above.)

## 2026-10-09 — Each service owns its migrations

Flyway inside `services/core`, Alembic inside `services/notifier`, rather than
a shared `infra/migrations` folder. A shared folder would couple the two
services.

## 2026-10-09 — No drug names, dosages or diagnoses in the data model

Health data is a special category under Russian law 152-ФЗ, and software that
interprets medical data or calculates doses must be registered as a medical
device. Generic labels keep the product outside both. See `docs/product.md`.

## 2026-10-09 — User interviews are postponed

The pitch deck plans interviews before building. The project owner decided to
build first and return to interviews later.

## 2026-10-09 — Docs in English, product text in Russian

The project owner works in English; the users are Russian speakers.
