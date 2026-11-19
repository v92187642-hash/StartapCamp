create table questions (
    id         uuid primary key,
    plan_id    uuid        not null references plans (id) on delete cascade,
    author_id  uuid        not null references users (id),
    -- Free text written by the user for their doctor. The product never interprets it.
    text       text        not null,
    created_at timestamptz not null,
    asked_at   timestamptz
);

create index questions_plan_id_idx on questions (plan_id);
