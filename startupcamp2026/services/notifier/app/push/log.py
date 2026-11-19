import logging

from app.push.base import PushMessage, PushResult

log = logging.getLogger(__name__)


class LogProvider:
    """Writes the push to the log. Used in dry-run mode and for providers without credentials."""

    def __init__(self, result: PushResult = PushResult.LOGGED):
        self.result = result

    def send(self, token: str, message: PushMessage) -> PushResult:
        log.info("Push (%s) to token ...%s: %s | %s | %s", self.result, token[-6:], message.title, message.body, message.data)
        return self.result
