"""Store the newest mark state of an entry from "entry.marked" events."""

import logging

from sqlalchemy.dialects.postgresql import insert
from sqlalchemy.orm import Session

from app.events import EntryMarked
from app.handlers.common import first_delivery
from app.models import EntryMark

log = logging.getLogger(__name__)


def handle_entry_marked(session: Session, event: EntryMarked) -> bool:
    """Apply the event. Returns False when it was skipped. The caller commits."""
    if not first_delivery(session, event.event_id):
        log.info("entry.marked %s skipped: duplicate", event.event_id)
        return False

    # Out-of-order delivery: a mark and its undo may swap places on the way.
    # The row keeps the time of the newest event and an older one cannot overwrite it.
    upsert = insert(EntryMark).values(
        entry_id=event.data.entry_id, status=event.data.status, occurred_at=event.occurred_at
    )
    is_newer = session.execute(
        upsert.on_conflict_do_update(
            index_elements=[EntryMark.entry_id],
            set_={"status": upsert.excluded.status, "occurred_at": upsert.excluded.occurred_at},
            where=EntryMark.occurred_at < upsert.excluded.occurred_at,
        ).returning(EntryMark.entry_id)
    ).first()
    if not is_newer:
        log.info("entry.marked %s skipped: a newer event is already stored", event.event_id)
        return False
    log.info("entry.marked %s applied: entry %s is %s", event.event_id, event.data.entry_id, event.data.status)
    return True
