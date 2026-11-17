package io.github.tochinicky.payouts.payout;

import java.time.OffsetDateTime;
import java.util.UUID;

public record Payout(
        UUID id,
        String userId,
        String stripeAccountId,
        long amountMinor,
        String currency,
        PayoutStatus status,
        String stripePayoutId,
        String failureCode,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt) {
}
