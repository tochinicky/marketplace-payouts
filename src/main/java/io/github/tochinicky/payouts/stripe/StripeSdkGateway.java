package io.github.tochinicky.payouts.stripe;

import com.stripe.StripeClient;
import com.stripe.exception.InvalidRequestException;
import com.stripe.exception.StripeException;
import com.stripe.net.RequestOptions;
import com.stripe.param.PayoutCreateParams;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.UUID;

@Component
public class StripeSdkGateway implements StripeGateway {

    private final StripeClient client;

    public StripeSdkGateway(StripeProperties properties) {
        this.client = StripeClient.builder()
                .setApiKey(properties.apiKey())
                .setApiBase(properties.apiBase())
                .build();
    }

    @Override
    public String createPayout(UUID payoutId, String stripeAccountId, long amountMinor, String currency) {
        var params = PayoutCreateParams.builder()
                .setAmount(amountMinor)
                .setCurrency(currency.toLowerCase(Locale.ROOT))
                .putMetadata("payout_id", payoutId.toString())
                .build();
        var options = RequestOptions.builder()
                .setStripeAccount(stripeAccountId)
                .setIdempotencyKey("payout-" + payoutId)
                .build();
        try {
            return client.payouts().create(params, options).getId();
        } catch (InvalidRequestException e) {
            throw new StripePayoutRejected(e.getCode() != null ? e.getCode() : "invalid_request", e);
        } catch (StripeException e) {
            throw new StripeCallFailed(e);
        }
    }
}
