from datetime import UTC, datetime, timedelta
from uuid import uuid4

from sqlalchemy import select

from app.events import PlanChanged, PlanDeleted, UserDeleted
from app.handlers.deletions import handle_plan_deleted, handle_user_deleted
from app.handlers.plan_changed import handle_plan_changed
from app.models import Device, EntryMark, Schedule

NOW = datetime.now(UTC).replace(microsecond=0)


def envelope(kind: str, data: dict) -> dict:
    return {"eventId": str(uuid4()), "type": kind, "occurredAt": NOW.isoformat(), "data": data}


def snapshot(plan_id, version, entry_id) -> PlanChanged:
    return PlanChanged.model_validate(envelope("plan.changed", {
        "planId": str(plan_id), "version": version, "timezone": "Asia/Yekaterinburg",
        "entries": [{"entryId": str(entry_id), "dueAt": (NOW + timedelta(hours=1)).isoformat()}], "recipients": [],
    }))


def test_deleted_plan_is_forgotten_and_cannot_come_back(session):
    plan_id, entry_id = uuid4(), uuid4()
    handle_plan_changed(session, snapshot(plan_id, 1, entry_id))
    session.add(EntryMark(entry_id=entry_id, status="marked", occurred_at=NOW))
    session.commit()

    assert handle_plan_deleted(session, PlanDeleted.model_validate(envelope("plan.deleted", {"planId": str(plan_id)})))
    session.commit()
    # A snapshot that was on its way when the plan was deleted arrives afterwards.
    assert not handle_plan_changed(session, snapshot(plan_id, 2, entry_id))
    session.commit()

    assert session.scalars(select(Schedule).where(Schedule.plan_id == plan_id)).all() == []
    assert session.get(EntryMark, entry_id) is None


def test_deletion_that_arrives_before_the_plan_was_ever_seen(session):
    plan_id, entry_id = uuid4(), uuid4()

    handle_plan_deleted(session, PlanDeleted.model_validate(envelope("plan.deleted", {"planId": str(plan_id)})))
    session.commit()

    assert not handle_plan_changed(session, snapshot(plan_id, 1, entry_id))


def test_deleted_user_loses_their_devices(session):
    user, other = uuid4(), uuid4()
    session.add(Device(user_id=user, provider="rustore", token=f"t-{uuid4()}"))
    session.add(Device(user_id=other, provider="fcm", token=f"t-{uuid4()}"))
    session.commit()

    handle_user_deleted(session, UserDeleted.model_validate(envelope("user.deleted", {"userId": str(user)})))
    session.commit()

    assert session.scalars(select(Device).where(Device.user_id == user)).all() == []
    assert len(session.scalars(select(Device).where(Device.user_id == other)).all()) == 1
