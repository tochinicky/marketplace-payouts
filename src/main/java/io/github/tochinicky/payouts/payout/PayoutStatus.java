package io.github.tochinicky.payouts.payout;

import java.util.Locale;

public enum PayoutStatus {
    REQUESTED, PROCESSING, PAID, FAILED;

    public String db() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static PayoutStatus fromDb(String value) {
        return valueOf(value.toUpperCase(Locale.ROOT));
    }
}
