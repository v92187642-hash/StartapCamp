"""Firebase Cloud Messaging, HTTP v1 API.

https://firebase.google.com/docs/reference/fcm/rest/v1/projects.messages/send
Written from the documentation; it has not been run against a real Firebase
project yet (that needs the project's service-account file).
"""

import logging

import google.auth.transport.requests
import httpx
from google.oauth2 import service_account

from app.push.base import PushMessage, PushResult

log = logging.getLogger(__name__)

SCOPE = "https://www.googleapis.com/auth/firebase.messaging"


class FcmProvider:
    def __init__(self, service_account_file: str, client: httpx.Client | None = None):
        self._credentials = service_account.Credentials.from_service_account_file(service_account_file, scopes=[SCOPE])
        self._url = f"https://fcm.googleapis.com/v1/projects/{self._credentials.project_id}/messages:send"
        self._client = client or httpx.Client(timeout=10)

    def _access_token(self) -> str:
        # Google's short-lived access token; refreshed a little before it expires.
        if not self._credentials.valid:
            self._credentials.refresh(google.auth.transport.requests.Request())
        return self._credentials.token

    def send(self, token: str, message: PushMessage) -> PushResult:
        return post_message(self._client, self._url, self._access_token(), token, message)


def post_message(client: httpx.Client, url: str, bearer: str, token: str, message: PushMessage) -> PushResult:
    """Send one message. FCM and RuStore Push share this request and error format."""
    body = {
        "message": {
            "token": token,
            "notification": {"title": message.title, "body": message.body},
            "data": message.data,
        }
    }
    try:
        response = client.post(url, json=body, headers={"Authorization": f"Bearer {bearer}"})
    except httpx.HTTPError as error:
        log.warning("Push request failed: %s", error)
        return PushResult.FAILED
    if response.is_success:
        return PushResult.SENT
    # 404 UNREGISTERED / NOT_FOUND: the app was removed. 400 INVALID_ARGUMENT: not a token at all.
    if response.status_code in (400, 404):
        return PushResult.INVALID_TOKEN
    log.warning("Push refused: %s %s", response.status_code, response.text[:200])
    return PushResult.FAILED
