"""Store a push token from a "device.registered" event."""

import logging

from sqlalchemy import func
from sqlalchemy.dialects.postgresql import insert
from sqlalchemy.orm import Session

from app.events import DeviceRegistered
from app.handlers.common import first_delivery
from app.models import Device

log = logging.getLogger(__name__)


def handle_device_registered(session: Session, event: DeviceRegistered) -> bool:
    """Apply the event. Returns False when it was skipped. The caller commits."""
    if not first_delivery(session, event.event_id):
        log.info("device.registered %s skipped: duplicate", event.event_id)
        return False

    # A token belongs to one user at a time: a phone handed to another person,
    # or a second account on the same phone, moves the token.
    upsert = insert(Device).values(
        user_id=event.data.user_id, provider=event.data.provider, token=event.data.token
    )
    session.execute(
        upsert.on_conflict_do_update(
            index_elements=[Device.token],
            set_={"user_id": upsert.excluded.user_id, "provider": upsert.excluded.provider, "updated_at": func.now()},
        )
    )
    # The token itself is a secret of the phone: it is never logged.
    log.info("device.registered %s applied: user %s, %s", event.event_id, event.data.user_id, event.data.provider)
    return True
