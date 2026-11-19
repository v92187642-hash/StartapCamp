create table shares (
    id            uuid primary key,
    plan_id       uuid        not null references plans (id) on delete cascade,
    invited_email text        not null,
    -- Set when the invitation is accepted.
    member_id     uuid references users (id) on delete cascade,
    role          text        not null default 'viewer' check (role in ('viewer')),
    status        text        not null default 'pending' check (status in ('pending', 'accepted')),
    -- Hash of the one-time token from the invitation link; null once used.
    token_hash    text unique,
    expires_at    timestamptz not null,
    created_at    timestamptz not null default now(),
    unique (plan_id, invited_email)
);

create index shares_member_id_idx on shares (member_id);
