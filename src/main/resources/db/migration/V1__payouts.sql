create table payouts (
    id                uuid primary key,
    user_id           text        not null,
    stripe_account_id text        not null,
    amount_minor      bigint      not null check (amount_minor > 0),
    currency          char(3)     not null,
    idempotency_key   text        not null,
    request_hash      text        not null,
    status            text        not null check (status in ('requested', 'processing', 'paid', 'failed')),
    stripe_payout_id  text,
    failure_code      text,
    created_at        timestamptz not null default now(),
    updated_at        timestamptz not null default now(),
    unique (user_id, idempotency_key)
);

create index payouts_status_created_idx on payouts (status, created_at);
create index payouts_stripe_payout_id_idx on payouts (stripe_payout_id);

create table stripe_events (
    event_id    text primary key,
    type        text        not null,
    received_at timestamptz not null default now()
);
