package io.github.tochinicky.payouts.stripe;

import java.util.UUID;

public interface StripeGateway {

    String createPayout(UUID payoutId, String stripeAccountId, long amountMinor, String currency);
}
