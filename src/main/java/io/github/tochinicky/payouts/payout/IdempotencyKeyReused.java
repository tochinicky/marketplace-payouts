package io.github.tochinicky.payouts.payout;

public class IdempotencyKeyReused extends RuntimeException {

    public IdempotencyKeyReused(String idempotencyKey) {
        super("Idempotency-Key " + idempotencyKey + " was already used with a different request");
    }
}
