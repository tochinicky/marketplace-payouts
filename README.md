# marketplace-payouts

A payout service for a marketplace in Java 21 and Spring Boot 4: hosts request payouts, a worker sends them to Stripe, and Stripe webhooks move them to paid or failed. A payout is never sent twice, whether the duplicate comes from a client retry, two workers racing, a crash between the Stripe call and the database write, or Stripe delivering the same webhook again.

## How duplicates are stopped

| Duplicate | Guard |
|---|---|
| A client retries `POST /payouts` | `Idempotency-Key` stored under `unique (user_id, idempotency_key)`; the insert is `ON CONFLICT DO NOTHING`, so a retry returns the original payout with `Idempotent-Replayed: true`. The same key with a different body gets `422` |
| 50 retries arrive at the same moment | Postgres serialises the inserts on the unique index: one row, one `201`, 49 replays (tested) |
| Two workers pick up the same payout | The worker claims a row with `UPDATE … SET status = 'processing' WHERE status = 'requested' RETURNING …`; only one update matches, so Stripe is called once (tested with 10 workers) |
| A worker crashes after calling Stripe | A claim with no Stripe payout id is retried after `payouts.claim-timeout` (1 minute) with the same Stripe idempotency key, `payout-{id}`, so Stripe returns the payout it already created |
| Stripe redelivers a webhook | Each event id is inserted into `stripe_events` with `ON CONFLICT DO NOTHING` in the same transaction as the status change; a repeat changes nothing |
| Webhooks arrive out of order | `paid` only applies to a processing payout; `failed` applies to processing or paid (Stripe can fail a payout after paying it); a late `paid` never revives a failed payout |
| A webhook beats the Stripe API response | The payout carries `metadata.payout_id`, so the event finds the row before its Stripe id is stored |

## Payout states

```
requested ──claim──▶ processing ──payout.paid──▶ paid
                         │                         │
                         └──payout.failed/canceled─┴──▶ failed
```

A rejected Stripe call (invalid request, insufficient balance) moves the payout straight to `failed` with Stripe's error code. A transient error (network, rate limit) leaves it claimed until the timeout, then it is retried.

## API

| Route | |
|---|---|
| `POST /payouts` `{stripeAccountId, amountMinor, currency}` with `X-User-Id` and `Idempotency-Key` headers | request a payout: `201` new, `200` replay, `422` key reused with a different body, `400` invalid |
| `GET /payouts/{id}` | one payout |
| `GET /payouts?status=PAID&limit=50` | support view, newest first |
| `POST /webhooks/stripe` | Stripe events, verified against `Stripe-Signature` (HMAC-SHA256, 5-minute tolerance); `400` on a bad signature |

`X-User-Id` stands in for the authenticated user.

## Run it

```sh
docker compose up -d          # Postgres 17 and stripe-mock
./gradlew bootRun
curl -s -X POST localhost:8080/payouts \
  -H 'X-User-Id: host-1' -H 'Idempotency-Key: 7d4e' -H 'Content-Type: application/json' \
  -d '{"stripeAccountId":"acct_1","amountMinor":5000,"currency":"EUR"}'
```

stripe-mock answers every payout with the same fixture id, so the Stripe id column is indexed rather than unique.

## Tests

```sh
./gradlew test
```

Testcontainers starts Postgres and stripe-mock, so Docker must be running. 32 tests cover the duplicate cases above, the state machine, signature verification and the HTTP API.

## Stack

Java 21, Spring Boot 4 (Web MVC, JDBC, Validation, Actuator), PostgreSQL 17, Flyway, stripe-java, JUnit 5, AssertJ, Mockito, Testcontainers.
