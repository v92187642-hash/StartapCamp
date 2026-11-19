create table consents (
    id           uuid primary key,
    user_id      uuid        not null references users (id) on delete cascade,
    kind         text        not null,
    text_version text        not null,
    accepted_at  timestamptz not null,
    unique (user_id, kind, text_version)
);

-- Only a hash is kept: a leaked table does not let anyone log in.
create table refresh_tokens (
    id         uuid primary key,
    user_id    uuid        not null references users (id) on delete cascade,
    token_hash text        not null unique,
    expires_at timestamptz not null,
    created_at timestamptz not null default now()
);

create index refresh_tokens_user_id_idx on refresh_tokens (user_id);
