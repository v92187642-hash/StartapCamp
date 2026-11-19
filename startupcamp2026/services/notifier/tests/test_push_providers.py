import json

import httpx

from app.config import Settings
from app.push.base import PushMessage, PushResult
from app.push.log import LogProvider
from app.push.registry import build_providers
from app.push.rustore import RuStoreProvider

MESSAGE = PushMessage(title="Отметка", body="Нет отметки", data={"type": "missed_entry"})


def rustore(handler) -> RuStoreProvider:
    return RuStoreProvider("project-1", "service-token", client=httpx.Client(transport=httpx.MockTransport(handler)))


def test_rustore_request_has_the_documented_shape():
    seen = {}

    def handler(request: httpx.Request) -> httpx.Response:
        seen["url"] = str(request.url)
        seen["authorization"] = request.headers["Authorization"]
        seen["body"] = json.loads(request.content)
        return httpx.Response(200, json={})

    assert rustore(handler).send("device-token", MESSAGE) is PushResult.SENT

    assert seen["url"] == "https://vkpns.rustore.ru/v1/projects/project-1/messages:send"
    assert seen["authorization"] == "Bearer service-token"
    assert seen["body"] == {
        "message": {
            "token": "device-token",
            "notification": {"title": "Отметка", "body": "Нет отметки"},
            "data": {"type": "missed_entry"},
        }
    }


def test_removed_app_is_reported_as_invalid_token():
    assert rustore(lambda request: httpx.Response(404, json={"error": {"status": "NOT_FOUND"}})).send("t", MESSAGE) \
        is PushResult.INVALID_TOKEN


def test_server_error_and_network_error_are_failures():
    def broken(request: httpx.Request) -> httpx.Response:
        raise httpx.ConnectError("no route")

    assert rustore(lambda request: httpx.Response(503)).send("t", MESSAGE) is PushResult.FAILED
    assert rustore(broken).send("t", MESSAGE) is PushResult.FAILED


def test_dry_run_sends_nothing_and_missing_credentials_are_reported():
    dry = build_providers(Settings(push_dry_run=True))
    assert dry["fcm"].send("t", MESSAGE) is PushResult.LOGGED
    assert dry["rustore"].send("t", MESSAGE) is PushResult.LOGGED

    live = build_providers(Settings(push_dry_run=False, fcm_service_account_file="", rustore_project_id=""))
    assert isinstance(live["fcm"], LogProvider)
    assert live["fcm"].send("t", MESSAGE) is PushResult.NOT_CONFIGURED
    assert live["rustore"].send("t", MESSAGE) is PushResult.NOT_CONFIGURED
