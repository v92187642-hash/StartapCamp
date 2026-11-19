from pydantic import Field, model_validator
from pydantic_settings import BaseSettings


class Settings(BaseSettings):
    """Read from environment variables; the names are the field names in upper case."""

    database_url: str
    rabbitmq_url: str
    consumer_enabled: bool = True
    alerts_enabled: bool = True

    alert_grace_minutes: int = 30
    alert_poll_seconds: int = 30
    alert_max_age_hours: int = 12

    push_dry_run: bool = True
    fcm_service_account_file: str = ""
    rustore_project_id: str = ""
    rustore_service_token: str = ""

    internal_secret: str = Field(min_length=16)

    @model_validator(mode="after")
    def secret_must_be_set(self) -> "Settings":
        if not self.database_url or not self.rabbitmq_url:
            raise ValueError("DATABASE_URL and RABBITMQ_URL must be set")
        if not self.internal_secret.strip():
            raise ValueError("INTERNAL_SECRET must be set")
        return self


settings = Settings()
