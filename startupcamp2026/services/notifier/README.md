# notifier

Python 3.12, FastAPI, SQLAlchemy, Alembic, aio-pika. Owns alerts to relatives,
device tokens and reports, and the Postgres schema `notifier`. Consumes events
from RabbitMQ; `core` calls it over HTTP only for reports.

Owner: Python developers. Boundaries and endpoints: `docs/architecture.md`.

## Run it alone

Dependencies are managed with [uv](https://docs.astral.sh/uv/). Start the
infrastructure, apply the migrations, then start the service:

```
docker compose -f ../../infra/docker-compose.yml up -d postgres rabbitmq
uv sync
uv run alembic upgrade head
uv run uvicorn app.main:app --reload --port 8000
```

Check: http://localhost:8000/health answers `{"status":"ok"}`.

## Test

```
uv run pytest
```

Docker must be running: the tests start a real Postgres in a container and
apply the Alembic migrations to it. RabbitMQ is not needed; handlers are
tested as plain functions.

## Environment variables

| Variable | Default | Meaning |
|---|---|---|
| `DATABASE_URL` | none, required | SQLAlchemy URL; the role owns schema `notifier` |
| `RABBITMQ_URL` | none, required | AMQP URL |
| `CONSUMER_ENABLED` | `true` | `false` starts the HTTP server without the RabbitMQ consumer |
| `ALERTS_ENABLED` | `true` | `false` starts it without the alert poller |
| `ALERT_GRACE_MINUTES` | `30` | How long after an entry's time an alert is sent |
| `ALERT_POLL_SECONDS` | `30` | How often the schedule is checked |
| `ALERT_MAX_AGE_HOURS` | `12` | Entries overdue for longer are skipped, not alerted |
| `PUSH_DRY_RUN` | `true` | `true`: pushes are written to the log, nothing is sent |
| `FCM_SERVICE_ACCOUNT_FILE` | empty | Firebase service-account JSON file |
| `RUSTORE_PROJECT_ID`, `RUSTORE_SERVICE_TOKEN` | empty | RuStore Push credentials |
| `INTERNAL_SECRET` | none, required, min 16 characters | Shared with `core` for `/internal/reports` |

## Where things are

| File | What it does |
|---|---|
| `app/consumer.py` | Queue setup; `HANDLERS` maps a routing key to its handler |
| `app/handlers/` | One file per event: `plan_changed`, `entry_marked`, `device_registered`, `deletions` |
| `app/alerts.py` | The poller: finds overdue entries with no mark and pushes to relatives |
| `app/push/` | `base.py` is the interface; `fcm.py`, `rustore.py`, `log.py` implement it; `registry.py` picks one per device |
| `app/reports.py` | `POST /internal/reports`: lays out the PDF from the data `core` sends |
| `app/models.py`, `alembic/versions/` | Tables and their migrations |

The Firebase and RuStore providers were written from the providers'
documentation and have only been run against a fake HTTP server
(`tests/test_push_providers.py`). Try them with real credentials before
relying on them.

## The worked example: consuming `plan.changed`

Read it in this order; the other handlers have the same shape.

| Step | File |
|---|---|
| Contract | `api/asyncapi.yaml`, message `PlanChanged` |
| Message model | `app/events.py` |
| Tables | `app/models.py` |
| Migration | `alembic/versions/0001_schedule.py` |
| Handler | `app/handlers/plan_changed.py` |
| Queue setup and dispatch | `app/consumer.py` |
| Test | `tests/test_plan_changed.py` |

Things the example settles:

- A handler is a function of a database session and a parsed event. It does
  not commit; the consumer runs it in one transaction.
- Duplicates. The event id goes into `processed_events` first; a second
  delivery finds it there and is skipped.
- Reordering. `plans` keeps the newest snapshot version per plan; an older
  snapshot arriving late is skipped. `entry_marks` does the same by event time.
- Failures. A message that cannot be parsed or applied is rejected and
  RabbitMQ moves it to the queue `notifier.events.dlq`. Look at it in the
  management UI, http://localhost:15672.
- To consume another event: a model in `app/events.py`, a handler in
  `app/handlers/`, one line in `HANDLERS` in `app/consumer.py`.
- Migrations. `uv run alembic revision -m "what"` creates a file; fill in
  `upgrade` and `downgrade` by hand and pass `schema="notifier"` everywhere.

## Try it by hand

With everything running, open the management UI, exchange `otmetka.events`,
"Publish message", routing key `plan.changed`, and paste the example payload
from `api/asyncapi.yaml`. A row appears in `notifier.schedules`.

To watch an alert happen quickly, start the environment with a short grace
period, create a plan with an entry a few minutes ahead, invite a relative,
register a token for the relative with `POST /devices`, and do not mark:

```
ALERT_GRACE_MINUTES=1 ALERT_POLL_SECONDS=5 docker compose -f ../../infra/docker-compose.yml up
docker compose -f ../../infra/docker-compose.yml logs -f notifier | grep "Push"
```
