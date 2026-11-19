-- Runs in schema "core" (see spring.flyway.default-schema).
-- Only the tables the M0 worked example needs. consents, shares and questions
-- arrive with their features; see docs/architecture.md for their columns.

create table users (
    id           uuid primary key,
    email        text        not null unique,
    display_name text,
    premium      boolean     not null default false,
    created_at   timestamptz not null default now()
);

create table plans (
    id         uuid primary key,
    owner_id   uuid        not null references users (id),
    title      text        not null,
    starts_on  date        not null,
    ends_on    date        not null,
    timezone   text        not null,
    version    bigint      not null default 1,
    created_at timestamptz not null default now(),
    check (ends_on >= starts_on)
);

create index plans_owner_id_idx on plans (owner_id);

create table plan_items (
    id           uuid primary key,
    plan_id      uuid       not null references plans (id) on delete cascade,
    -- Generic text such as «Назначение 1». Never a drug name, dosage or diagnosis.
    label        text       not null,
    time_of_day  time       not null,
    -- ISO day numbers: 1 is Monday, 7 is Sunday.
    days_of_week smallint[] not null,
    position     integer    not null
);

create index plan_items_plan_id_idx on plan_items (plan_id);
