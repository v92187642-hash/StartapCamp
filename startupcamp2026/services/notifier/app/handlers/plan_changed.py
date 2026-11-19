"""Worked example: store the schedule carried by a "plan.changed" event.

The handler is a plain function of a database session and a parsed event, so
it is tested without RabbitMQ. handlers/entry_marked.py has the same shape.
"""

import logging

from sqlalchemy import delete
from sqlalchemy.dialects.postgresql import insert
from sqlalchemy.orm import Session

from app.events import PlanChanged
from app.handlers.common import first_delivery
from app.models import Plan, Schedule

log = logging.getLogger(__name__)


def handle_plan_changed(session: Session, event: PlanChanged) -> bool:
    """Apply the event. Returns False when it was skipped.

    The caller commits. Everything below happens in one transaction, so a
    failure half-way leaves nothing behind and the message can be retried.
    """
    if not first_delivery(session, event.event_id):
        log.info("plan.changed %s skipped: duplicate", event.event_id)
        return False

    # Out-of-order delivery: keep the snapshot only if it is newer than the stored one.
    # The row lock taken by this upsert also makes two snapshots of one plan wait for each other.
    snapshot = event.data
    upsert = insert(Plan).values(
        plan_id=snapshot.plan_id, version=snapshot.version, timezone=snapshot.timezone
    )
    is_newer = session.execute(
        upsert.on_conflict_do_update(
            index_elements=[Plan.plan_id],
            set_={"version": upsert.excluded.version, "timezone": upsert.excluded.timezone},
            where=Plan.version < upsert.excluded.version,
        ).returning(Plan.plan_id)
    ).first()
    if not is_newer:
        log.info("plan.changed %s skipped: version %s is not newer", event.event_id, snapshot.version)
        return False

    # The snapshot is complete: pending rows it no longer lists are gone from the plan.
    # Rows already marked or alerted are history and stay.
    entry_ids = [entry.entry_id for entry in snapshot.entries]
    session.execute(
        delete(Schedule).where(
            Schedule.plan_id == snapshot.plan_id,
            Schedule.state == "pending",
            Schedule.entry_id.not_in(entry_ids),
        )
    )
    if snapshot.entries:
        rows = insert(Schedule).values(
            [
                {
                    "entry_id": entry.entry_id,
                    "plan_id": snapshot.plan_id,
                    "plan_version": snapshot.version,
                    "due_at": entry.due_at,
                    "recipients": snapshot.recipients,
                }
                for entry in snapshot.entries
            ]
        )
        # An entry that is already known keeps its state.
        session.execute(
            rows.on_conflict_do_update(
                index_elements=[Schedule.entry_id],
                set_={
                    "plan_version": rows.excluded.plan_version,
                    "due_at": rows.excluded.due_at,
                    "recipients": rows.excluded.recipients,
                },
            )
        )
    log.info("plan.changed %s applied: plan %s version %s", event.event_id, snapshot.plan_id, snapshot.version)
    return True
