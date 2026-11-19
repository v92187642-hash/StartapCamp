-- Events waiting to be sent to RabbitMQ. A row is written in the same
-- transaction as the change it announces, so the two succeed or fail together;
-- it is sent afterwards and sent again until the broker confirms it.
create table outbox (
    -- Also the eventId of the message: a retry is recognisable as a duplicate.
    id           uuid primary key,
    -- Order of sending.
    seq          bigserial   not null,
    type         text        not null,
    -- The message's "data" as JSON; for plan.changed only the plan id (see events/Outbox.java).
    data         text        not null,
    occurred_at  timestamptz not null,
    published_at timestamptz
);

create index outbox_unpublished_idx on outbox (seq) where published_at is null;
