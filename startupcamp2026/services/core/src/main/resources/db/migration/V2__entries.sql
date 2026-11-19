create table entries (
    id           uuid primary key,
    plan_item_id uuid        not null references plan_items (id) on delete cascade,
    scheduled_at timestamptz not null,
    -- "No mark" is not stored: it is "planned" with scheduled_at in the past.
    status       text        not null default 'planned' check (status in ('planned', 'marked')),
    marked_at    timestamptz,
    marked_by    uuid references users (id),
    -- Set when a mark was changed or withdrawn after it was made.
    corrected_at timestamptz,
    unique (plan_item_id, scheduled_at),
    check ((status = 'marked') = (marked_at is not null))
);

create index entries_scheduled_at_idx on entries (scheduled_at);
