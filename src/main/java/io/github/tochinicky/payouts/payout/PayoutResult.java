package io.github.tochinicky.payouts.payout;

public record PayoutResult(Payout payout, boolean replayed) {
}
