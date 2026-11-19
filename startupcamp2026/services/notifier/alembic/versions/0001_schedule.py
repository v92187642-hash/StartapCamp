"""Tables for the schedule built from plan.changed events

Revision ID: 0001
Revises:
"""
import sqlalchemy as sa
from alembic import op
from sqlalchemy.dialects import postgresql

revision = "0001"
down_revision = None
branch_labels = None
depends_on = None

SCHEMA = "notifier"


def upgrade() -> None:
    op.create_table(
        "plans",
        sa.Column("plan_id", postgresql.UUID(as_uuid=True), primary_key=True),
        sa.Column("version", sa.BigInteger, nullable=False),
        sa.Column("timezone", sa.Text, nullable=False),
        sa.Column("updated_at", sa.DateTime(timezone=True), nullable=False, server_default=sa.func.now()),
        schema=SCHEMA,
    )
    op.create_table(
        "schedules",
        sa.Column("entry_id", postgresql.UUID(as_uuid=True), primary_key=True),
        sa.Column("plan_id", postgresql.UUID(as_uuid=True), nullable=False),
        sa.Column("plan_version", sa.BigInteger, nullable=False),
        sa.Column("due_at", sa.DateTime(timezone=True), nullable=False),
        sa.Column("recipients", postgresql.ARRAY(postgresql.UUID(as_uuid=True)), nullable=False),
        sa.Column("state", sa.Text, nullable=False, server_default="pending"),
        sa.CheckConstraint("state in ('pending', 'alerted')", name="schedules_state_check"),
        schema=SCHEMA,
    )
    op.create_index("ix_schedules_plan_id", "schedules", ["plan_id"], schema=SCHEMA)
    # The M3 poller looks for pending rows whose due time has passed.
    op.create_index("ix_schedules_state_due_at", "schedules", ["state", "due_at"], schema=SCHEMA)
    op.create_table(
        "processed_events",
        sa.Column("event_id", postgresql.UUID(as_uuid=True), primary_key=True),
        sa.Column("processed_at", sa.DateTime(timezone=True), nullable=False, server_default=sa.func.now()),
        schema=SCHEMA,
    )


def downgrade() -> None:
    op.drop_table("processed_events", schema=SCHEMA)
    op.drop_table("schedules", schema=SCHEMA)
    op.drop_table("plans", schema=SCHEMA)
