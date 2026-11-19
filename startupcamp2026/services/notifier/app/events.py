"""Messages from RabbitMQ, as described in api/asyncapi.yaml."""

from datetime import datetime
from typing import Literal
from uuid import UUID

from pydantic import BaseModel, ConfigDict
from pydantic.alias_generators import to_camel


class Event(BaseModel):
    # JSON uses camelCase (eventId), Python uses snake_case (event_id).
    model_config = ConfigDict(alias_generator=to_camel, populate_by_name=True)


class ScheduledEntry(Event):
    entry_id: UUID
    due_at: datetime


class PlanSnapshot(Event):
    plan_id: UUID
    version: int
    timezone: str
    entries: list[ScheduledEntry]
    recipients: list[UUID]


class PlanChanged(Event):
    event_id: UUID
    type: Literal["plan.changed"]
    occurred_at: datetime
    data: PlanSnapshot


class EntryMarkedData(Event):
    entry_id: UUID
    plan_id: UUID
    status: Literal["planned", "marked"]
    marked_at: datetime | None = None


class EntryMarked(Event):
    event_id: UUID
    type: Literal["entry.marked"]
    occurred_at: datetime
    data: EntryMarkedData


class DeviceRegisteredData(Event):
    user_id: UUID
    provider: Literal["fcm", "rustore"]
    token: str


class DeviceRegistered(Event):
    event_id: UUID
    type: Literal["device.registered"]
    occurred_at: datetime
    data: DeviceRegisteredData


class PlanDeletedData(Event):
    plan_id: UUID


class PlanDeleted(Event):
    event_id: UUID
    type: Literal["plan.deleted"]
    occurred_at: datetime
    data: PlanDeletedData


class UserDeletedData(Event):
    user_id: UUID


class UserDeleted(Event):
    event_id: UUID
    type: Literal["user.deleted"]
    occurred_at: datetime
    data: UserDeletedData
