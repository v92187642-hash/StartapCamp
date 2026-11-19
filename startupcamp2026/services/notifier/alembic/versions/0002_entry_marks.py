"""Mark state of entries, from entry.marked events

Revision ID: 0002
Revises: 0001
"""
import sqlalchemy as sa
from alembic import op
from sqlalchemy.dialects import postgresql

revision = "0002"
down_revision = "0001"
branch_labels = None
depends_on = None

SCHEMA = "notifier"


def upgrade() -> None:
    op.create_table(
        "entry_marks",
        sa.Column("entry_id", postgresql.UUID(as_uuid=True), primary_key=True),
        sa.Column("status", sa.Text, nullable=False),
        sa.Column("occurred_at", sa.DateTime(timezone=True), nullable=False),
        sa.CheckConstraint("status in ('planned', 'marked')", name="entry_marks_status_check"),
        schema=SCHEMA,
    )


def downgrade() -> None:
    op.drop_table("entry_marks", schema=SCHEMA)
