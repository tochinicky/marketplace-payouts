package io.github.tochinicky.payouts;

import io.github.tochinicky.payouts.payout.Payout;
import io.github.tochinicky.payouts.payout.PayoutProcessor;
import io.github.tochinicky.payouts.payout.PayoutStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClient;

import java.time.Instant;

import static io.github.tochinicky.payouts.webhook.StripeWebhookVerifierTest.sign;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"payouts.worker.enabled=false", "stripe.webhook-secret=whsec_api_test"})
class PayoutApiTest extends IntegrationTest {

    @Value("${local.server.port}")
    int port;

    @Autowired
    PayoutProcessor processor;

    RestClient http;

    @BeforeEach
    void client() {
        http = RestClient.builder()
                .baseUrl("http://localhost:" + port)
                .defaultStatusHandler(status -> true, (request, response) -> {
                })
                .build();
    }

    @Test
    void aPayoutGoesFromRequestToPaid() {
        ResponseEntity<Payout> created = requestPayout("key-1", 5000);
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        Payout payout = created.getBody();
        assertThat(created.getHeaders().getLocation()).hasToString("/payouts/" + payout.id());

        processor.process(payout.id());
        Payout sent = get(payout);
        assertThat(sent.status()).isEqualTo(PayoutStatus.PROCESSING);

        String event = """
                {"id":"evt_1","object":"event","type":"payout.paid","api_version":"2025-05-28.basil",
                 "data":{"object":{"id":"%s","object":"payout","metadata":{"payout_id":"%s"}}}}"""
                .formatted(sent.stripePayoutId(), payout.id());
        ResponseEntity<Void> webhook = http.post().uri("/webhooks/stripe")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Stripe-Signature", sign(event, "whsec_api_test", Instant.now()))
                .body(event)
                .retrieve().toBodilessEntity();

        assertThat(webhook.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(get(payout).status()).isEqualTo(PayoutStatus.PAID);
    }

    @Test
    void aRetryIsReplayedAndAReusedKeyIsRejected() {
        Payout first = requestPayout("key-1", 5000).getBody();

        ResponseEntity<Payout> retry = requestPayout("key-1", 5000);
        assertThat(retry.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(retry.getHeaders().getFirst("Idempotent-Replayed")).isEqualTo("true");
        assertThat(retry.getBody().id()).isEqualTo(first.id());

        ResponseEntity<String> reused = http.post().uri("/payouts")
                .header("X-User-Id", "user-1")
                .header("Idempotency-Key", "key-1")
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"stripeAccountId\":\"acct_1\",\"amountMinor\":9000,\"currency\":\"EUR\"}")
                .retrieve().toEntity(String.class);
        assertThat(reused.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
        assertThat(reused.getBody()).contains("already used with a different request");
    }

    @Test
    void anInvalidRequestIsRejected() {
        ResponseEntity<Void> response = http.post().uri("/payouts")
                .header("X-User-Id", "user-1")
                .header("Idempotency-Key", "key-1")
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"stripeAccountId\":\"acct_1\",\"amountMinor\":0,\"currency\":\"eur\"}")
                .retrieve().toBodilessEntity();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void aWebhookWithABadSignatureIsRejected() {
        ResponseEntity<Void> response = http.post().uri("/webhooks/stripe")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Stripe-Signature", "t=1,v1=deadbeef")
                .body("{}")
                .retrieve().toBodilessEntity();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void anUnknownPayoutIs404() {
        ResponseEntity<Void> response = http.get().uri("/payouts/00000000-0000-0000-0000-000000000000")
                .retrieve().toBodilessEntity();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    private ResponseEntity<Payout> requestPayout(String key, long amountMinor) {
        return http.post().uri("/payouts")
                .header("X-User-Id", "user-1")
                .header("Idempotency-Key", key)
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"stripeAccountId\":\"acct_1\",\"amountMinor\":%d,\"currency\":\"EUR\"}".formatted(amountMinor))
                .retrieve().toEntity(Payout.class);
    }

    private Payout get(Payout payout) {
        return http.get().uri("/payouts/{id}", payout.id()).retrieve().body(Payout.class);
    }
}
