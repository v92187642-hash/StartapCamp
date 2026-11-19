from datetime import UTC, datetime, timedelta
from uuid import UUID, uuid4

from sqlalchemy.orm import Session

from app.consumer import process
from app.events import EntryMarked
from app.handlers.entry_marked import handle_entry_marked
from app.models import EntryMark

NOW = datetime.now(UTC).replace(microsecond=0)


def entry_marked(entry_id: UUID, status: str, occurred_at: datetime) -> dict:
    """A message as `core` sends it (camelCase JSON)."""
    data = {"entryId": str(entry_id), "planId": str(uuid4()), "status": status}
    if status == "marked":
        data["markedAt"] = occurred_at.isoformat()
    return {"eventId": str(uuid4()), "type": "entry.marked", "occurredAt": occurred_at.isoformat(), "data": data}


def apply(session: Session, message: dict) -> bool:
    applied = handle_entry_marked(session, EntryMarked.model_validate(message))
    session.commit()
    return applied


def stored_status(session: Session, entry_id: UUID) -> str:
    session.expire_all()
    return session.get(EntryMark, entry_id).status


def test_mark_is_stored_before_any_schedule_exists(session):
    entry = uuid4()

    assert apply(session, entry_marked(entry, "marked", NOW))

    assert stored_status(session, entry) == "marked"


def test_undo_after_mark(session):
    entry = uuid4()
    apply(session, entry_marked(entry, "marked", NOW))

    assert apply(session, entry_marked(entry, "planned", NOW + timedelta(seconds=5)))

    assert stored_status(session, entry) == "planned"


def test_older_event_arriving_late_is_ignored(session):
    entry = uuid4()
    apply(session, entry_marked(entry, "planned", NOW + timedelta(seconds=5)))

    assert not apply(session, entry_marked(entry, "marked", NOW))

    assert stored_status(session, entry) == "planned"


def test_same_message_twice_is_applied_once(session):
    entry = uuid4()
    message = entry_marked(entry, "marked", NOW)

    assert apply(session, message)
    assert not apply(session, message)


def test_message_exactly_as_core_serialises_it(session):
    body = b'''{"eventId":"3318c9d6-2503-4c5b-bd33-4e22ecbe8e91","type":"entry.marked",
        "occurredAt":"2026-10-08T22:51:27.539860381Z","data":{"entryId":"44444444-4444-4444-8444-444444444444",
        "planId":"22222222-2222-4222-8222-222222222222","status":"marked","markedAt":"2026-10-08T22:51:27.538488Z"}}'''

    process("entry.marked", body)

    assert stored_status(session, UUID("44444444-4444-4444-8444-444444444444")) == "marked"
