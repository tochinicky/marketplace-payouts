package io.github.tochinicky.payouts.stripe;

public class StripePayoutRejected extends RuntimeException {

    private final String code;

    public StripePayoutRejected(String code, Throwable cause) {
        super(cause.getMessage(), cause);
        this.code = code;
    }

    public String code() {
        return code;
    }
}
