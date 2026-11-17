package io.github.tochinicky.payouts.webhook;

import com.stripe.exception.SignatureVerificationException;
import io.github.tochinicky.payouts.stripe.StripeProperties;
import org.junit.jupiter.api.Test;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HexFormat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class StripeWebhookVerifierTest {

    private static final String SECRET = "whsec_test";

    private final StripeWebhookVerifier verifier =
            new StripeWebhookVerifier(new StripeProperties("sk_test", "http://unused", SECRET));

    @Test
    void extractsAFailedPayoutEvent() throws Exception {
        String body = event("evt_1", "payout.failed", "po_1", "\"account_closed\"");

        PayoutEvent event = verifier.verify(body, sign(body, SECRET, Instant.now())).orElseThrow();

        assertThat(event).isEqualTo(new PayoutEvent("evt_1", "payout.failed", "po_1", null, "account_closed"));
    }

    @Test
    void ignoresEventsThatAreNotAboutPayouts() throws Exception {
        String body = """
                {"id":"evt_2","object":"event","type":"charge.succeeded","api_version":"2025-05-28.basil",
                 "data":{"object":{"id":"ch_1","object":"charge"}}}""";

        assertThat(verifier.verify(body, sign(body, SECRET, Instant.now()))).isEmpty();
    }

    @Test
    void rejectsAWrongSignature() {
        String body = event("evt_3", "payout.paid", "po_3", "null");

        assertThatThrownBy(() -> verifier.verify(body, sign(body, "whsec_other", Instant.now())))
                .isInstanceOf(SignatureVerificationException.class);
    }

    @Test
    void rejectsATamperedBody() {
        String body = event("evt_4", "payout.paid", "po_4", "null");
        String header = sign(body, SECRET, Instant.now());

        assertThatThrownBy(() -> verifier.verify(body.replace("po_4", "po_9"), header))
                .isInstanceOf(SignatureVerificationException.class);
    }

    @Test
    void rejectsAReplayOlderThanTheTolerance() {
        String body = event("evt_5", "payout.paid", "po_5", "null");

        assertThatThrownBy(() -> verifier.verify(body, sign(body, SECRET, Instant.now().minusSeconds(600))))
                .isInstanceOf(SignatureVerificationException.class);
    }

    @Test
    void rejectsAMissingSignature() {
        String body = event("evt_6", "payout.paid", "po_6", "null");

        assertThatThrownBy(() -> verifier.verify(body, null)).isInstanceOf(SignatureVerificationException.class);
    }

    @Test
    void readsOurPayoutIdFromMetadata() throws Exception {
        String body = """
                {"id":"evt_7","object":"event","type":"payout.paid","api_version":"2025-05-28.basil",
                 "data":{"object":{"id":"po_7","object":"payout",
                                   "metadata":{"payout_id":"5f0c7b1e-8a0e-4a39-9a51-0d2a3c9f4b11"}}}}""";

        PayoutEvent event = verifier.verify(body, sign(body, SECRET, Instant.now())).orElseThrow();

        assertThat(event.payoutId()).hasToString("5f0c7b1e-8a0e-4a39-9a51-0d2a3c9f4b11");
    }

    private static String event(String id, String type, String payoutId, String failureCodeJson) {
        return """
                {"id":"%s","object":"event","type":"%s","api_version":"2025-05-28.basil",
                 "data":{"object":{"id":"%s","object":"payout","failure_code":%s}}}"""
                .formatted(id, type, payoutId, failureCodeJson);
    }

    public static String sign(String body, String secret, Instant at) {
        try {
            long t = at.getEpochSecond();
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] digest = mac.doFinal((t + "." + body).getBytes(StandardCharsets.UTF_8));
            return "t=" + t + ",v1=" + HexFormat.of().formatHex(digest);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
