"""Forget a plan or a user when core says they were deleted."""

import logging

from sqlalchemy import delete, select
from sqlalchemy.dialects.postgresql import insert
from sqlalchemy.orm import Session

from app.events import PlanDeleted, UserDeleted
from app.handlers.common import first_delivery
from app.models import Device, EntryMark, Plan, Schedule

log = logging.getLogger(__name__)

# Deletion is final. The plan row stays with the largest possible version, so a
# "plan.changed" for the same plan that arrives late is "not newer" and is ignored.
DELETED_VERSION = 2**63 - 1


def handle_plan_deleted(session: Session, event: PlanDeleted) -> bool:
    if not first_delivery(session, event.event_id):
        return False
    plan_id = event.data.plan_id
    entry_ids = select(Schedule.entry_id).where(Schedule.plan_id == plan_id)
    session.execute(delete(EntryMark).where(EntryMark.entry_id.in_(entry_ids)))
    session.execute(delete(Schedule).where(Schedule.plan_id == plan_id))
    tombstone = insert(Plan).values(plan_id=plan_id, version=DELETED_VERSION, timezone="UTC")
    session.execute(
        tombstone.on_conflict_do_update(index_elements=[Plan.plan_id], set_={"version": DELETED_VERSION})
    )
    log.info("plan.deleted %s applied: plan %s forgotten", event.event_id, plan_id)
    return True


def handle_user_deleted(session: Session, event: UserDeleted) -> bool:
    if not first_delivery(session, event.event_id):
        return False
    # The user's own plans arrive as separate plan.deleted events; here only the phones go.
    session.execute(delete(Device).where(Device.user_id == event.data.user_id))
    log.info("user.deleted %s applied: user %s forgotten", event.event_id, event.data.user_id)
    return True
