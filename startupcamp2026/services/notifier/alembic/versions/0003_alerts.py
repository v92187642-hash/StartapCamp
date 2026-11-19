"""Devices and deliveries for alerts; schedule state "skipped"

Revision ID: 0003
Revises: 0002
"""
import sqlalchemy as sa
from alembic import op
from sqlalchemy.dialects import postgresql

revision = "0003"
down_revision = "0002"
branch_labels = None
depends_on = None

SCHEMA = "notifier"


def upgrade() -> None:
    op.create_table(
        "devices",
        sa.Column("id", postgresql.UUID(as_uuid=True), primary_key=True),
        sa.Column("user_id", postgresql.UUID(as_uuid=True), nullable=False),
        sa.Column("provider", sa.Text, nullable=False),
        sa.Column("token", sa.Text, nullable=False, unique=True),
        sa.Column("updated_at", sa.DateTime(timezone=True), nullable=False, server_default=sa.func.now()),
        sa.CheckConstraint("provider in ('fcm', 'rustore')", name="devices_provider_check"),
        schema=SCHEMA,
    )
    op.create_index("ix_devices_user_id", "devices", ["user_id"], schema=SCHEMA)
    op.create_table(
        "deliveries",
        sa.Column("id", postgresql.UUID(as_uuid=True), primary_key=True),
        sa.Column("entry_id", postgresql.UUID(as_uuid=True), nullable=False),
        sa.Column("device_id", postgresql.UUID(as_uuid=True), nullable=False),
        sa.Column("sent_at", sa.DateTime(timezone=True), nullable=False),
        sa.Column("result", sa.Text, nullable=False),
        schema=SCHEMA,
    )
    op.create_index("ix_deliveries_entry_id", "deliveries", ["entry_id"], schema=SCHEMA)
    op.drop_constraint("schedules_state_check", "schedules", schema=SCHEMA)
    op.create_check_constraint(
        "schedules_state_check", "schedules", "state in ('pending', 'alerted', 'skipped')", schema=SCHEMA
    )


def downgrade() -> None:
    op.drop_constraint("schedules_state_check", "schedules", schema=SCHEMA)
    op.create_check_constraint("schedules_state_check", "schedules", "state in ('pending', 'alerted')", schema=SCHEMA)
    op.drop_table("deliveries", schema=SCHEMA)
    op.drop_table("devices", schema=SCHEMA)
