# Plan

Milestones are in order. Do not start one until the previous one meets its
"done when". Tick items as they are finished.

## Team and ownership

| Part | Owner |
|---|---|
| `services/core` | Java developers (3) |
| `apps/android` | Java developers (3) |
| `services/notifier` | Python developers (2) |
| Postgres, migrations review | Python developers (2) |
| `apps/web` | Claude (nobody on the team does frontend) |
| `api/openapi.yaml` | Shared; every change is reviewed by one Java and one Python developer |

## Scaffold depth

How much Claude builds. First set to level 2 (a skeleton and one worked
example per service) on 2026-10-09. Later the same day the project owner asked
Claude to build every stage it could, which replaced that limit; see
`docs/decisions.md`. The worked examples of level 2 are still marked in each
service's `README.md` as the place to start reading.

A ticked item below means built and checked as described in `docs/state.md`.
"Done when" lines are ticked only in `docs/state.md`, because several need a
real phone or an account that Claude does not have.

## M0 — Skeleton

Everything runs, nothing is useful yet.

- [x] `api/openapi.yaml` covering the M1 endpoints (see `docs/architecture.md`)
- [x] `api/asyncapi.yaml` covering `plan.changed` and `entry.marked`
- [x] `infra/docker-compose.yml`: Postgres, RabbitMQ, Redis, `core`, `notifier`, `web`
- [x] `infra/.env.example` with every variable the services read
- [x] `services/core`: Spring Boot project, Flyway, `GET /health`, one test
- [x] `services/core` worked example: `PUT /entries/{id}/mark` with migration and test, publishing `entry.marked`
- [x] `services/notifier`: FastAPI project, Alembic, `GET /health`, one test
- [x] `services/notifier` worked example: consume `plan.changed`, with migration and test
- [x] `apps/web`: Vite project, generated API client, one page
- [x] `apps/android`: Gradle project, generated API client, one screen
- [x] Each part's `README.md` says how to run and test it alone
- [ ] CI: build and test `core`, `notifier`, `web`, and apply Flyway/Alembic on an empty Postgres on every push (`.gitlab-ci.yml` for the owner's Runner; tick after its first green run)

Done when: a fresh clone starts with one compose command and every health
check answers.

## M1 — First vertical slice

One path end to end, on the web client.

- [x] Registration and login by email and password (failed-login counts in Redis); JWT issued by `core`. Replaced the one-time email code on 2026-10-09.
- [x] Consent record stored at first login
- [x] Create a plan with items that have generic labels and times
- [x] Day view: entries in three states (planned, marked, no mark)
- [x] Mark an entry; undo or correct a mark
- [x] Invite a relative by email; relative sees the same day view, read-only
- [x] `core` publishes `plan.changed` and `entry.marked`; `notifier` stores them, tolerating duplicates and reordering

Done when: two browser sessions, patient and relative, show the same day and a
mark made in one appears in the other after a refresh.

## M2 — Android app

- [x] Login, plan list, day view, mark and undo
- [x] Local alarms scheduled from the plan (reminders fire on the phone)
- [x] Works offline; marks sync when the network returns
- [x] Guidance screen for battery-optimisation settings on Xiaomi, Huawei, Samsung
- [x] Large-text layout for older users

Done when: a reminder fires on a real device with the app closed, and the mark
appears on the web. (Met on an emulator only; a real device is still needed.)

## M3 — Alerts and questions

- [ ] `notifier` sends a push to the relative when an entry has no mark after a grace period (server side built and run in dry-run mode; the Android app cannot receive a push until a Firebase or RuStore project exists)
- [x] Push provider behind one interface (Firebase and RuStore Push)
- [x] Questions for the doctor: add, list, mark as asked
- [x] A missed entry offers "add a question", never advice

Done when: a relative's phone receives an alert for a missed entry.

## M4 — Report and premium

- [x] PDF summary for a doctor's visit: marks by day, list of questions
- [x] Premium flag on the account; sharing and report gated behind it
- [ ] Payment integration (RuStore Pay or СБП; decide then). Until then `infra/scripts/set-premium.sh` sets the flag.

Done when: a paying test account can export a report and a free one cannot.

## M5 — Launch readiness

- [ ] Deploy on hosting located in Russia (prepared: `infra/docker-compose.prod.yml`, `docs/deploy.md`)
- [ ] Privacy policy and consent text reviewed by a lawyer (drafts and questions: `docs/legal/`)
- [x] Backups and a restore test (`infra/scripts/`)
- [ ] RuStore listing (draft texts and checklist: `docs/rustore-listing.md`)
- [x] Account deletion with all data (added: the consent text promises it)
- [ ] Pharmacy referral link experiment (non-prescription and booking only)

Done when: a stranger can install from RuStore and use the product.

## Deliberately not planned

Add these only when a milestone needs them.

- API gateway, Kubernetes
- Redis for caching or sessions (it holds failed-login counts only)
- Phone-number login (SMS costs money per message)
- Login through ВК ID or Госуслуги
- ML features
- iOS app
- Ad banner (lowest-priority revenue model; see `docs/product.md`)
