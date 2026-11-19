"""RuStore Push.

https://www.rustore.ru/help/sdk/push-notifications/send-push-notifications
The request and error format follow FCM's. Written from the documentation;
it has not been run against a real RuStore project yet (that needs a project
id and a service token from the RuStore console).
"""

import httpx

from app.push.base import PushMessage, PushResult
from app.push.fcm import post_message


class RuStoreProvider:
    def __init__(self, project_id: str, service_token: str, client: httpx.Client | None = None):
        self._url = f"https://vkpns.rustore.ru/v1/projects/{project_id}/messages:send"
        self._service_token = service_token
        self._client = client or httpx.Client(timeout=10)

    def send(self, token: str, message: PushMessage) -> PushResult:
        return post_message(self._client, self._url, self._service_token, token, message)
