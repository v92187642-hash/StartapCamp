from uuid import UUID

from sqlalchemy.dialects.postgresql import insert
from sqlalchemy.orm import Session

from app.models import ProcessedEvent


def first_delivery(session: Session, event_id: UUID) -> bool:
    """Record the event id. False means this event was already applied: skip it.

    RETURNING gives a row only when the insert really happened.
    """
    inserted = session.execute(
        insert(ProcessedEvent)
        .values(event_id=event_id)
        .on_conflict_do_nothing()
        .returning(ProcessedEvent.event_id)
    ).first()
    return inserted is not None
