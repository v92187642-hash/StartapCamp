"""Alerts to relatives about entries with no mark.

notifier decides when an alert is due. It polls its own schedules table:
RabbitMQ carries events, not timers.
"""

import asyncio
import logging
from datetime import UTC, datetime, timedelta
from zoneinfo import ZoneInfo

from sqlalchemy import exists, select
from sqlalchemy.orm import Session

from app.config import settings
from app.db import SessionLocal
from app.models import Delivery, Device, EntryMark, Plan, Schedule
from app.push.base import PushMessage, PushProvider, PushResult

log = logging.getLogger(__name__)

BATCH = 100


def send_due_alerts(
    session: Session,
    providers: dict[str, PushProvider],
    now: datetime,
    grace: timedelta,
    max_age: timedelta,
) -> int:
    """Alert about every entry that is overdue by `grace` and has no mark.

    Returns the number of entries alerted. The caller commits. Each entry is
    alerted once: its row moves from "pending" to "alerted" in the same
    transaction that records the deliveries.
    """
    marked = exists().where(EntryMark.entry_id == Schedule.entry_id, EntryMark.status == "marked")
    due = session.scalars(
        select(Schedule)
        .where(Schedule.state == "pending", Schedule.due_at <= now - grace, ~marked)
        .order_by(Schedule.due_at)
        .limit(BATCH)
        # A second copy of the service skips the rows this one is working on.
        .with_for_update(skip_locked=True)
    ).all()

    alerted = 0
    for entry in due:
        if entry.due_at < now - max_age:
            entry.state = "skipped"
            continue
        plan = session.get(Plan, entry.plan_id)
        zone = ZoneInfo(plan.timezone) if plan else UTC
        message = PushMessage(
            title="Отметка",
            # A fact, never advice. No label either: events carry ids and times only.
            body=f"Нет отметки о выполнении назначения на {entry.due_at.astimezone(zone):%H:%M}",
            data={"type": "missed_entry", "planId": str(entry.plan_id), "entryId": str(entry.entry_id)},
        )
        devices = session.scalars(select(Device).where(Device.user_id.in_(entry.recipients))).all()
        for device in devices:
            result = providers[device.provider].send(device.token, message)
            session.add(Delivery(entry_id=entry.entry_id, device_id=device.id, sent_at=now, result=str(result)))
            if result is PushResult.INVALID_TOKEN:
                session.delete(device)
        entry.state = "alerted"
        alerted += 1
        log.info("Alerted about entry %s: %s recipients, %s devices", entry.entry_id, len(entry.recipients), len(devices))
    return alerted


def run_once(providers: dict[str, PushProvider]) -> int:
    """One pass of the poller, in its own transaction."""
    with SessionLocal.begin() as session:
        return send_due_alerts(
            session,
            providers,
            now=datetime.now(UTC),
            grace=timedelta(minutes=settings.alert_grace_minutes),
            max_age=timedelta(hours=settings.alert_max_age_hours),
        )


async def poll_forever(providers: dict[str, PushProvider]) -> None:
    log.info(
        "Alert poller started: every %ss, grace %s min", settings.alert_poll_seconds, settings.alert_grace_minutes
    )
    while True:
        try:
            # Blocking database and HTTP calls run in a thread, off the event loop.
            await asyncio.to_thread(run_once, providers)
        except Exception:
            log.exception("Alert pass failed; the next one will try again")
        await asyncio.sleep(settings.alert_poll_seconds)
