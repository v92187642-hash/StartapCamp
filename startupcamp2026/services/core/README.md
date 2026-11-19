# core

Java 21, Spring Boot 3, Gradle, Flyway, Spring AMQP. Owns auth, plans, diary
entries, sharing and questions, and the Postgres schema `core`. Publishes
events to RabbitMQ and counts failed logins in Redis.

Owner: Java developers. Module layout and rules: `docs/architecture.md`.

## Run it alone

Start the infrastructure, then the service:

```
docker compose -f ../../infra/docker-compose.yml up -d postgres rabbitmq redis notifier
JWT_SECRET=$(openssl rand -base64 36) INTERNAL_SECRET=$(openssl rand -base64 24) \
  DATABASE_URL=jdbc:postgresql://localhost:5432/otmetka DATABASE_USER=core DATABASE_PASSWORD=... \
  RABBITMQ_HOST=localhost RABBITMQ_USER=otmetka RABBITMQ_PASSWORD=... \
  REDIS_HOST=localhost NOTIFIER_URL=http://localhost:8000 WEB_BASE_URL=http://localhost:5173 \
  ./gradlew bootRun
```

Check: http://localhost:8080/health answers `{"status":"ok"}`.

Any JDK from 17 up can start Gradle; it downloads JDK 21 for the build by
itself. In IntelliJ IDEA, open this folder and run `CoreApplication` with the
`JWT_SECRET` environment variable set.

## Test

```
./gradlew test
```

Docker must be running: the tests start a real Postgres and
a real Redis in containers and apply the Flyway migrations. RabbitMQ, email
and the PDF renderer are replaced by mocks, so tests check what would be sent.

## Environment variables

| Variable | Default | Meaning |
|---|---|---|
| `DATABASE_URL` | none, required | JDBC URL, e.g. `jdbc:postgresql://postgres:5432/otmetka` |
| `DATABASE_USER` | none, required | Role that owns schema `core` |
| `DATABASE_PASSWORD` | none, required | |
| `RABBITMQ_HOST`, `RABBITMQ_PORT` | none / `5672` | Hostname of RabbitMQ; port optional |
| `RABBITMQ_USER`, `RABBITMQ_PASSWORD` | none, required | |
| `REDIS_HOST`, `REDIS_PORT` | none / `6379` | Hostname of Redis |
| `REDIS_PASSWORD` | empty | Optional |
| `JWT_SECRET` | none, required | Signs access tokens (HS256); at least 32 characters |
| `INTERNAL_SECRET` | none, required | Shared with `notifier` for the report call; at least 16 characters |
| `NOTIFIER_URL` | none, required | Where `core` asks for PDF reports |
| `PREMIUM_ENFORCED` | `true` | `false` lets every account invite and export |
| `WEB_BASE_URL` | none, required | Used in invitation links |
| `SEED_TEST_ACCOUNTS` | `false` | `true`: six test accounts are created at start if missing (`auth/TestAccounts`). Existing ones are never touched |
| `MAIL_ENABLED` | `false` | `false`: emails are written to the log. `true`: sent over SMTP |
| `MAIL_HOST`, `MAIL_PORT`, `MAIL_USER`, `MAIL_PASSWORD`, `MAIL_FROM` | | SMTP server, when `MAIL_ENABLED=true` |
| `MAIL_SMTP_AUTH`, `MAIL_STARTTLS`, `MAIL_SSL` | `false` | Log in to the SMTP server; encrypt with STARTTLS (port 587) or from the start (port 465) |

## Logging in while developing

Register once, then log in; both answer with tokens:

```
curl -X POST localhost:8080/auth/register -H 'Content-Type: application/json' -d '{"email":"me@example.com","password":"at-least-8"}'
curl -X POST localhost:8080/auth/login -H 'Content-Type: application/json' -d '{"email":"me@example.com","password":"at-least-8"}'
```

With `SEED_TEST_ACCOUNTS=true` there are also
`testaccount1@otmetka.test` to `testaccount6@otmetka.test` with the passwords
`otmetka-test-1` to `otmetka-test-6`; consent is recorded and all are premium.

A new account must record consent (`POST /consents`) before any other
endpoint answers. A premium account is made with
`infra/scripts/set-premium.sh me@example.com true`.

## Where things are

| Module | Start reading at | What it does |
|---|---|---|
| `auth` | `AuthService`, `PasswordLogin`, `ConsentGate`, `PremiumPolicy` | Registration, passwords, tokens, consent, account deletion, the premium rule, test accounts |
| `plans` | `PlanService` | Plans and items; raises `PlanItemAdded` and `PlanChanged` |
| `diary` | `DiaryService`, `EntryGenerator` | Day view, mark, undo, history; creates entries when an item is added |
| `sharing` | `AccessPolicy`, `ShareService` | Who may see or change a plan; invitations |
| `questions` | `QuestionService` | Questions for the doctor |
| `devices` | `DeviceController` | Passes push tokens on to `notifier` |
| `reports` | `ReportService`, `NotifierReportRenderer` | Collects report data; the one HTTP call to `notifier` |
| `events` | `RabbitEventRelay`, `Outbox`, `OutboxTrigger`, `OutboxPublisher` | Every message that leaves `core`, and the table that keeps it until it is sent |
| `common` | `MailConfig`, the exception classes | Mail, errors, time |

Tests that tell the story of a milestone: `PatientAndRelativeTest` (M1),
`ReportTest` (M4), `DeleteAccountTest`, `OutboxTest` (RabbitMQ down).

## The worked example: marking an entry

`PUT /entries/{id}/mark` goes through every layer. Read it in this order, then
copy the shape for the next endpoint.

| Step | File |
|---|---|
| Contract | `api/openapi.yaml`, operation `markEntry` |
| Migration | `src/main/resources/db/migration/V2__entries.sql` |
| Entity and its rule | `diary/Entry.java` (`mark`) |
| Repository | `diary/EntryRepository.java` |
| Service: access check, transaction, event | `diary/DiaryService.java` |
| Controller | `diary/EntryController.java` |
| Access rule | `sharing/AccessPolicy.java` |
| Question to another module | `plans/PlanDirectory.java` |
| Event raised, stored, then sent | `diary/EntryMarked.java`, `events/RabbitEventRelay.java`, `events/Outbox.java`, `events/OutboxPublisher.java` |
| History of the mark | `diary/MarkHistory.java`, migration `V5__mark_history.sql` |
| Test | `src/test/java/ru/otmetka/core/diary/MarkEntryTest.java` |

Things the example settles:

- Modules. A class is package-private unless another module needs it. `diary`
  never touches the `plans` tables; it asks `PlanDirectory`.
- Errors. Throw `NotFoundException`, `ForbiddenException` or
  `BadRequestException` with a Russian message; the client gets
  `application/problem+json`.
- The caller. `CurrentUser.id(jwt)` gives the user id from the access token.
- Time. Inject `Clock`; do not call `Instant.now()`.
- Events. Raise a record from your own package (`diary.EntryMarked`) with
  `ApplicationEventPublisher` inside the transaction. `events/RabbitEventRelay`
  stores the message in the `outbox` table in that transaction, and
  `events/OutboxPublisher` sends it after the commit, again and again until
  RabbitMQ confirms it. Events carry ids and times, never labels.
- Migrations. A new file `V<next>__<what>.sql`; never edit one that is merged.
- Tests. Extend `IntegrationTest`; no drug names or diagnoses in test data.

## Rules that are easy to break

- No drug names, dosages or diagnoses: not in tables, not in test data.
- Never answer with advice. A missed entry is a fact; the user may write a
  question for the doctor.
- User-facing text (every `detail` in an error, every email) is Russian.
- A module never touches another module's tables or repositories.
- Events carry ids and times only.
