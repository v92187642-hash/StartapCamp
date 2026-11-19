from datetime import UTC, datetime, timedelta
from uuid import UUID, uuid4

from sqlalchemy import select
from sqlalchemy.orm import Session

from app.alerts import send_due_alerts
from app.events import DeviceRegistered
from app.handlers.device_registered import handle_device_registered
from app.models import Delivery, Device, EntryMark, Plan, Schedule
from app.push.base import PushMessage, PushResult

NOW = datetime(2026, 10, 9, 4, 0, tzinfo=UTC)  # 09:00 in Yekaterinburg
GRACE = timedelta(minutes=30)
MAX_AGE = timedelta(hours=12)


class FakeProvider:
    """Remembers what it was asked to send."""

    def __init__(self, result: PushResult = PushResult.SENT):
        self.result = result
        self.sent: list[tuple[str, PushMessage]] = []

    def send(self, token: str, message: PushMessage) -> PushResult:
        self.sent.append((token, message))
        return self.result


def register(session: Session, user_id: UUID, provider: str = "rustore", token: str | None = None) -> str:
    token = token or f"token-{uuid4()}"
    event = {
        "eventId": str(uuid4()), "type": "device.registered", "occurredAt": NOW.isoformat(),
        "data": {"userId": str(user_id), "provider": provider, "token": token},
    }
    handle_device_registered(session, DeviceRegistered.model_validate(event))
    session.commit()
    return token


def schedule(session: Session, due_at: datetime, recipients: list[UUID]) -> UUID:
    plan_id, entry_id = uuid4(), uuid4()
    session.add(Plan(plan_id=plan_id, version=1, timezone="Asia/Yekaterinburg"))
    session.add(Schedule(entry_id=entry_id, plan_id=plan_id, plan_version=1, due_at=due_at, recipients=recipients))
    session.commit()
    return entry_id


def run(session: Session, providers: dict) -> int:
    count = send_due_alerts(session, providers, NOW, GRACE, MAX_AGE)
    session.commit()
    session.expire_all()
    return count


def only(fake: FakeProvider) -> dict:
    return {"fcm": fake, "rustore": fake}


def test_overdue_entry_without_mark_alerts_the_relative_once(session):
    relative = uuid4()
    token = register(session, relative)
    entry = schedule(session, NOW - timedelta(minutes=31), [relative])
    fake = FakeProvider()

    assert run(session, only(fake)) == 1

    sent_token, message = fake.sent[0]
    assert sent_token == token
    assert message.title == "Отметка"
    # The time is shown in the plan's zone: 03:29 UTC is 08:29 in Yekaterinburg.
    assert message.body == "Нет отметки о выполнении назначения на 08:29"
    assert message.data["entryId"] == str(entry)
    assert session.get(Schedule, entry).state == "alerted"
    assert session.scalars(select(Delivery.result).where(Delivery.entry_id == entry)).all() == ["sent"]

    # The next pass finds nothing new: one alert per entry.
    assert run(session, only(fake)) == 0
    assert len(fake.sent) == 1


def test_entry_inside_the_grace_period_waits(session):
    relative = uuid4()
    register(session, relative)
    entry = schedule(session, NOW - timedelta(minutes=29), [relative])
    fake = FakeProvider()

    run(session, only(fake))

    assert fake.sent == []
    assert session.get(Schedule, entry).state == "pending"


def test_marked_entry_is_not_alerted(session):
    relative = uuid4()
    register(session, relative)
    entry = schedule(session, NOW - timedelta(hours=1), [relative])
    session.add(EntryMark(entry_id=entry, status="marked", occurred_at=NOW - timedelta(minutes=50)))
    session.commit()
    fake = FakeProvider()

    run(session, only(fake))

    assert fake.sent == []
    assert session.get(Schedule, entry).state == "pending"


def test_undone_mark_is_alerted(session):
    relative = uuid4()
    register(session, relative)
    entry = schedule(session, NOW - timedelta(hours=1), [relative])
    session.add(EntryMark(entry_id=entry, status="planned", occurred_at=NOW - timedelta(minutes=40)))
    session.commit()
    fake = FakeProvider()

    run(session, only(fake))

    assert len(fake.sent) == 1


def test_very_old_entry_is_skipped_without_alert(session):
    relative = uuid4()
    register(session, relative)
    entry = schedule(session, NOW - timedelta(hours=13), [relative])
    fake = FakeProvider()

    run(session, only(fake))

    assert fake.sent == []
    assert session.get(Schedule, entry).state == "skipped"


def test_each_device_gets_its_own_provider(session):
    relative = uuid4()
    firebase_token = register(session, relative, provider="fcm")
    rustore_token = register(session, relative, provider="rustore")
    schedule(session, NOW - timedelta(hours=1), [relative])
    firebase, rustore = FakeProvider(), FakeProvider()

    run(session, {"fcm": firebase, "rustore": rustore})

    assert [token for token, _ in firebase.sent] == [firebase_token]
    assert [token for token, _ in rustore.sent] == [rustore_token]


def test_dead_token_is_forgotten(session):
    relative = uuid4()
    token = register(session, relative)
    entry = schedule(session, NOW - timedelta(hours=1), [relative])

    run(session, only(FakeProvider(PushResult.INVALID_TOKEN)))

    assert session.scalars(select(Device).where(Device.token == token)).first() is None
    assert session.scalars(select(Delivery.result).where(Delivery.entry_id == entry)).all() == ["invalid_token"]
    assert session.get(Schedule, entry).state == "alerted"


def test_entry_with_no_recipients_or_devices_is_closed_quietly(session):
    entry = schedule(session, NOW - timedelta(hours=1), [])
    fake = FakeProvider()

    assert run(session, only(fake)) == 1

    assert fake.sent == []
    assert session.get(Schedule, entry).state == "alerted"


def test_token_registered_again_moves_to_the_new_user(session):
    first_user, second_user = uuid4(), uuid4()
    token = register(session, first_user)

    register(session, second_user, token=token)

    session.expire_all()
    devices = session.scalars(select(Device).where(Device.token == token)).all()
    assert [device.user_id for device in devices] == [second_user]
