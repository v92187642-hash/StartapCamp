import json
from datetime import UTC, datetime, timedelta
from uuid import UUID, uuid4

from sqlalchemy import select
from sqlalchemy.orm import Session

from app.consumer import process
from app.events import PlanChanged
from app.handlers.plan_changed import handle_plan_changed
from app.models import Plan, Schedule

TOMORROW = datetime.now(UTC).replace(microsecond=0) + timedelta(days=1)


def plan_changed(plan_id: UUID, version: int, entry_ids: list[UUID], recipients: list[UUID] | None = None) -> dict:
    """A message as `core` sends it (camelCase JSON), for a fictional plan."""
    return {
        "eventId": str(uuid4()),
        "type": "plan.changed",
        "occurredAt": datetime.now(UTC).isoformat(),
        "data": {
            "planId": str(plan_id),
            "version": version,
            "timezone": "Asia/Yekaterinburg",
            "entries": [{"entryId": str(entry_id), "dueAt": TOMORROW.isoformat()} for entry_id in entry_ids],
            "recipients": [str(r) for r in (recipients or [])],
        },
    }


def apply(session: Session, message: dict) -> bool:
    applied = handle_plan_changed(session, PlanChanged.model_validate(message))
    session.commit()
    return applied


def stored_entries(session: Session, plan_id: UUID) -> dict[UUID, Schedule]:
    session.expire_all()
    rows = session.scalars(select(Schedule).where(Schedule.plan_id == plan_id)).all()
    return {row.entry_id: row for row in rows}


def test_stores_the_schedule(session):
    plan_id, entry_a, entry_b, relative = uuid4(), uuid4(), uuid4(), uuid4()

    assert apply(session, plan_changed(plan_id, 1, [entry_a, entry_b], [relative]))

    stored = stored_entries(session, plan_id)
    assert set(stored) == {entry_a, entry_b}
    assert stored[entry_a].due_at == TOMORROW
    assert stored[entry_a].recipients == [relative]
    assert stored[entry_a].state == "pending"
    assert stored[entry_a].plan_version == 1
    assert session.get(Plan, plan_id).version == 1


def test_same_message_twice_is_applied_once(session):
    plan_id, entry = uuid4(), uuid4()
    message = plan_changed(plan_id, 1, [entry])

    assert apply(session, message)
    assert not apply(session, message)

    assert set(stored_entries(session, plan_id)) == {entry}


def test_newer_snapshot_replaces_pending_entries(session):
    plan_id, old_entry, kept_entry, new_entry = uuid4(), uuid4(), uuid4(), uuid4()
    apply(session, plan_changed(plan_id, 1, [old_entry, kept_entry]))

    assert apply(session, plan_changed(plan_id, 2, [kept_entry, new_entry]))

    stored = stored_entries(session, plan_id)
    assert set(stored) == {kept_entry, new_entry}
    assert stored[kept_entry].plan_version == 2


def test_older_snapshot_arriving_late_is_ignored(session):
    plan_id, old_entry, new_entry = uuid4(), uuid4(), uuid4()
    apply(session, plan_changed(plan_id, 2, [new_entry]))

    assert not apply(session, plan_changed(plan_id, 1, [old_entry]))

    assert set(stored_entries(session, plan_id)) == {new_entry}
    assert session.get(Plan, plan_id).version == 2


def test_snapshot_without_entries_clears_the_schedule(session):
    plan_id, entry = uuid4(), uuid4()
    apply(session, plan_changed(plan_id, 1, [entry]))

    assert apply(session, plan_changed(plan_id, 2, []))
    assert not apply(session, plan_changed(plan_id, 1, [entry]))

    assert stored_entries(session, plan_id) == {}


def test_entries_no_longer_pending_are_kept(session):
    plan_id, alerted_entry = uuid4(), uuid4()
    apply(session, plan_changed(plan_id, 1, [alerted_entry]))
    session.get(Schedule, alerted_entry).state = "alerted"
    session.commit()

    apply(session, plan_changed(plan_id, 2, []))

    assert stored_entries(session, plan_id)[alerted_entry].state == "alerted"


def test_raw_message_from_the_queue(session):
    """The path the consumer takes: bytes in, rows committed."""
    plan_id, entry = uuid4(), uuid4()

    process("plan.changed", json.dumps(plan_changed(plan_id, 1, [entry])).encode())

    assert set(stored_entries(session, plan_id)) == {entry}


def test_message_exactly_as_core_serialises_it(session):
    """Copied from a real message: nanosecond times, extra fields are tolerated."""
    body = b'''{"eventId":"6f1c0f0e-8f0b-4a53-9a0e-0d8f5b9d5a11","type":"plan.changed",
        "occurredAt":"2026-10-08T22:51:27.539860381Z","data":{"planId":"0b0f5a52-7d0e-4c59-8c9c-0c7f3a3f1b01",
        "version":3,"timezone":"Asia/Yekaterinburg","entries":[{"entryId":"4c6f7b1a-2f0f-4a40-9e3b-7b7f0e2d9c02",
        "dueAt":"2026-10-10T03:00:00Z"}],"recipients":["9a1d2c3b-4e5f-4a6b-8c7d-0e1f2a3b4c03"]}}'''

    process("plan.changed", body)

    assert len(stored_entries(session, UUID("0b0f5a52-7d0e-4c59-8c9c-0c7f3a3f1b01"))) == 1
