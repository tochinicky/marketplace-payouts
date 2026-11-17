package io.github.tochinicky.payouts.stripe;

public class StripeCallFailed extends RuntimeException {

    public StripeCallFailed(Throwable cause) {
        super(cause.getMessage(), cause);
    }
}
