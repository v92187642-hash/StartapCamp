# Отметка

A diary for following a doctor's prescriptions, shared between a patient and a
relative. Student project (ТИСБИ catalogue, topic 04) being built for an
accelerator pitch. «Отметка» is a working name.

## Start here

Read these in order before doing any work:

1. `docs/state.md` — what exists right now and what to do next
2. `docs/plan.md` — milestones and the definition of done for each
3. `docs/architecture.md` — services, boundaries, data model, API outline
4. `docs/decisions.md` — why things are the way they are; do not re-litigate
5. `docs/product.md` — product scope and the legal limits that shape the code

## Layout

```
api/                 OpenAPI and AsyncAPI contracts, the source of truth
services/core/       Java, Spring Boot: auth, plans, diary, sharing
services/notifier/   Python, FastAPI: alerts to relatives, reports
apps/android/        Kotlin: main client
apps/web/            React + Vite + TypeScript: small secondary client
infra/               docker-compose: Postgres, RabbitMQ, Redis and the services
docs/                everything a clean session needs
```

## Rules

- The contracts in `api/openapi.yaml` (public API) and `api/asyncapi.yaml`
  (events) change first; code follows them.
- `core` and `notifier` never read each other's database tables. They talk
  through RabbitMQ events; see `docs/architecture.md` for the one exception.
- Postgres holds all permanent data. Redis holds only short-lived data.
- No drug names, dosages or diagnoses anywhere: not in the data model, not in
  seed data, not in tests. Plan items carry generic labels («Назначение 1»).
- The product never gives medical advice. A missed entry produces a question
  for the doctor, never a suggestion.
- User-facing text is Russian. Code, comments and docs are English.
- Everything must start with one command: `docker compose -f infra/docker-compose.yml up`.

## Keeping docs alive

- At the end of every work session, update `docs/state.md`.
- When a milestone item is finished, tick it in `docs/plan.md`.
- When a choice is made that a future session might question, add an entry to
  `docs/decisions.md`.
- When a service boundary, table or endpoint changes, update
  `docs/architecture.md` in the same change.
