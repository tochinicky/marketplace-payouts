package io.github.tochinicky.payouts.payout;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;

public record PayoutRequest(
        @NotBlank String stripeAccountId,
        @Positive long amountMinor,
        @Pattern(regexp = "[A-Z]{3}") String currency) {
}
