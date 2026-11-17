package io.github.tochinicky.payouts.webhook;

import java.util.UUID;

public record PayoutEvent(String eventId, String type, String stripePayoutId, UUID payoutId, String failureCode) {
}
