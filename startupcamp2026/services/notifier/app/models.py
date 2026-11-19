"""Tables of the schema "notifier". Change them only through an Alembic migration."""

import uuid
from datetime import datetime

from sqlalchemy import BigInteger, DateTime, MetaData, Text, func
from sqlalchemy.dialects.postgresql import ARRAY, UUID
from sqlalchemy.orm import DeclarativeBase, Mapped, mapped_column

SCHEMA = "notifier"


class Base(DeclarativeBase):
    metadata = MetaData(schema=SCHEMA)


class Plan(Base):
    """The newest snapshot version seen for each plan, so older ones can be ignored."""

    __tablename__ = "plans"

    plan_id: Mapped[uuid.UUID] = mapped_column(UUID(as_uuid=True), primary_key=True)
    version: Mapped[int] = mapped_column(BigInteger)
    timezone: Mapped[str] = mapped_column(Text)
    updated_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), server_default=func.now())


class Schedule(Base):
    """One row per upcoming entry. Polled from M3 on to find entries with no mark."""

    __tablename__ = "schedules"

    entry_id: Mapped[uuid.UUID] = mapped_column(UUID(as_uuid=True), primary_key=True)
    plan_id: Mapped[uuid.UUID] = mapped_column(UUID(as_uuid=True), index=True)
    plan_version: Mapped[int] = mapped_column(BigInteger)
    due_at: Mapped[datetime] = mapped_column(DateTime(timezone=True))
    # User ids of the relatives to alert.
    recipients: Mapped[list[uuid.UUID]] = mapped_column(ARRAY(UUID(as_uuid=True)))
    # pending: no alert sent yet
    # alerted: the relatives were told about a missing mark
    # skipped: too old to alert about (the service was down when it became due)
    state: Mapped[str] = mapped_column(Text, server_default="pending")


class EntryMark(Base):
    """The newest known mark state of an entry, from "entry.marked" events.

    Kept apart from Schedule because the two events arrive in any order: a
    mark can be known before the entry's schedule row exists.
    """

    __tablename__ = "entry_marks"

    entry_id: Mapped[uuid.UUID] = mapped_column(UUID(as_uuid=True), primary_key=True)
    # planned | marked
    status: Mapped[str] = mapped_column(Text)
    # occurredAt of the event this row came from; an older event does not overwrite it.
    occurred_at: Mapped[datetime] = mapped_column(DateTime(timezone=True))


class ProcessedEvent(Base):
    """Ids of handled events, so a message delivered twice is applied once."""

    __tablename__ = "processed_events"

    event_id: Mapped[uuid.UUID] = mapped_column(UUID(as_uuid=True), primary_key=True)
    processed_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), server_default=func.now())


class Device(Base):
    """A push token of a user's phone, from "device.registered" events."""

    __tablename__ = "devices"

    id: Mapped[uuid.UUID] = mapped_column(UUID(as_uuid=True), primary_key=True, default=uuid.uuid4)
    user_id: Mapped[uuid.UUID] = mapped_column(UUID(as_uuid=True), index=True)
    # fcm | rustore
    provider: Mapped[str] = mapped_column(Text)
    token: Mapped[str] = mapped_column(Text, unique=True)
    updated_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), server_default=func.now())


class Delivery(Base):
    """One attempt to push one alert to one device. Kept to answer "was the relative told?"."""

    __tablename__ = "deliveries"

    id: Mapped[uuid.UUID] = mapped_column(UUID(as_uuid=True), primary_key=True, default=uuid.uuid4)
    entry_id: Mapped[uuid.UUID] = mapped_column(UUID(as_uuid=True), index=True)
    # No foreign key: the device may be removed later (invalid token) and the record stays.
    device_id: Mapped[uuid.UUID] = mapped_column(UUID(as_uuid=True))
    sent_at: Mapped[datetime] = mapped_column(DateTime(timezone=True))
    # sent | logged | invalid_token | failed | not_configured
    result: Mapped[str] = mapped_column(Text)
