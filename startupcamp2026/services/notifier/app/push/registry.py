import logging

from app.config import Settings
from app.push.base import PushProvider, PushResult
from app.push.log import LogProvider

log = logging.getLogger(__name__)


def build_providers(settings: Settings) -> dict[str, PushProvider]:
    """One provider per value of devices.provider.

    In dry-run mode (the default) nothing leaves the machine. Otherwise a
    provider without credentials logs and reports "not_configured".
    """
    if settings.push_dry_run:
        return {"fcm": LogProvider(), "rustore": LogProvider()}

    providers: dict[str, PushProvider] = {}
    if settings.fcm_service_account_file:
        from app.push.fcm import FcmProvider

        providers["fcm"] = FcmProvider(settings.fcm_service_account_file)
    else:
        log.warning("FCM_SERVICE_ACCOUNT_FILE is not set: pushes to Firebase tokens are not sent")
        providers["fcm"] = LogProvider(PushResult.NOT_CONFIGURED)

    if settings.rustore_project_id and settings.rustore_service_token:
        from app.push.rustore import RuStoreProvider

        providers["rustore"] = RuStoreProvider(settings.rustore_project_id, settings.rustore_service_token)
    else:
        log.warning("RUSTORE_PROJECT_ID / RUSTORE_SERVICE_TOKEN are not set: pushes to RuStore tokens are not sent")
        providers["rustore"] = LogProvider(PushResult.NOT_CONFIGURED)
    return providers
