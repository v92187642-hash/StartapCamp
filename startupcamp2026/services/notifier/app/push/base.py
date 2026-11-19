"""The one interface every push provider sits behind.

Many phones in Russia have no Google services, so Firebase alone is not
enough. To add a provider: one class with `send`, one line in registry.py.
"""

from dataclasses import dataclass, field
from enum import StrEnum
from typing import Protocol


@dataclass(frozen=True)
class PushMessage:
    title: str
    body: str
    # Small string values the app can read, for example which plan to open.
    data: dict[str, str] = field(default_factory=dict)


class PushResult(StrEnum):
    SENT = "sent"
    # Dry run: written to the log instead of being sent.
    LOGGED = "logged"
    # The provider says the token is dead: the app was removed. The device is forgotten.
    INVALID_TOKEN = "invalid_token"
    # A temporary failure. The alert is not repeated; the delivery row records the failure.
    FAILED = "failed"
    # The provider has no credentials in the environment.
    NOT_CONFIGURED = "not_configured"


class PushProvider(Protocol):
    def send(self, token: str, message: PushMessage) -> PushResult: ...
