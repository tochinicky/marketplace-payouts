package io.github.tochinicky.payouts.webhook;

import com.stripe.exception.EventDataObjectDeserializationException;
import com.stripe.exception.SignatureVerificationException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class StripeWebhookController {

    private final StripeWebhookVerifier verifier;
    private final WebhookHandler handler;

    public StripeWebhookController(StripeWebhookVerifier verifier, WebhookHandler handler) {
        this.verifier = verifier;
        this.handler = handler;
    }

    @PostMapping("/webhooks/stripe")
    public ResponseEntity<Void> receive(@RequestBody String rawBody,
                                        @RequestHeader(value = "Stripe-Signature", required = false) String signature)
            throws EventDataObjectDeserializationException {
        try {
            verifier.verify(rawBody, signature).ifPresent(handler::handle);
        } catch (SignatureVerificationException e) {
            return ResponseEntity.badRequest().build();
        }
        return ResponseEntity.ok().build();
    }
}
