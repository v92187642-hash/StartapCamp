-- Append-only. entries holds the current state; this table holds how it came
-- about, so an undone or corrected mark stays visible to the doctor.
create table mark_history (
    id        uuid primary key,
    entry_id  uuid        not null references entries (id) on delete cascade,
    action    text        not null check (action in ('marked', 'corrected', 'undone')),
    -- The time of the mark this row is about; for "undone", the mark that was withdrawn.
    marked_at timestamptz not null,
    actor_id  uuid        not null references users (id),
    at        timestamptz not null
);

create index mark_history_entry_id_idx on mark_history (entry_id);
