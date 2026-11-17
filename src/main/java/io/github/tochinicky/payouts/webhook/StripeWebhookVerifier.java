package io.github.tochinicky.payouts.webhook;

import com.stripe.exception.EventDataObjectDeserializationException;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.model.Event;
import com.stripe.model.Payout;
import com.stripe.net.Webhook;
import io.github.tochinicky.payouts.stripe.StripeProperties;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

@Component
public class StripeWebhookVerifier {

    private final String secret;

    public StripeWebhookVerifier(StripeProperties properties) {
        this.secret = properties.webhookSecret();
    }

    public Optional<PayoutEvent> verify(String rawBody, String signatureHeader)
            throws SignatureVerificationException, EventDataObjectDeserializationException {
        if (signatureHeader == null || signatureHeader.isBlank()) {
            throw new SignatureVerificationException("Missing Stripe-Signature header", null);
        }
        Event event = Webhook.constructEvent(rawBody, signatureHeader, secret);
        if (!event.getType().startsWith("payout.")) {
            return Optional.empty();
        }
        Payout payout = (Payout) event.getDataObjectDeserializer().deserializeUnsafe();
        return Optional.of(new PayoutEvent(
                event.getId(), event.getType(), payout.getId(), payoutId(payout), payout.getFailureCode()));
    }

    private static UUID payoutId(Payout payout) {
        if (payout.getMetadata() == null) {
            return null;
        }
        String value = payout.getMetadata().get("payout_id");
        try {
            return value == null ? null : UUID.fromString(value);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
