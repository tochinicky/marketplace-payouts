package io.github.tochinicky.payouts.payout;

import io.github.tochinicky.payouts.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PayoutServiceTest extends IntegrationTest {

    private static final PayoutRequest REQUEST = new PayoutRequest("acct_1", 5000, "EUR");

    @Autowired
    PayoutService payouts;

    @Test
    void recordsANewRequest() {
        PayoutResult result = payouts.request("user-1", "key-1", REQUEST);

        assertThat(result.replayed()).isFalse();
        assertThat(result.payout().status()).isEqualTo(PayoutStatus.REQUESTED);
        assertThat(result.payout().amountMinor()).isEqualTo(5000);
        assertThat(payouts.find(result.payout().id())).contains(result.payout());
    }

    @Test
    void aRetryWithTheSameKeyAndRequestReturnsTheOriginalPayout() {
        PayoutResult first = payouts.request("user-1", "key-1", REQUEST);
        PayoutResult retry = payouts.request("user-1", "key-1", REQUEST);

        assertThat(retry.replayed()).isTrue();
        assertThat(retry.payout().id()).isEqualTo(first.payout().id());
        assertThat(rowCount()).isEqualTo(1);
    }

    @Test
    void theSameKeyWithADifferentRequestIsRejected() {
        payouts.request("user-1", "key-1", REQUEST);

        assertThatThrownBy(() -> payouts.request("user-1", "key-1", new PayoutRequest("acct_1", 9000, "EUR")))
                .isInstanceOf(IdempotencyKeyReused.class);
        assertThat(rowCount()).isEqualTo(1);
    }

    @Test
    void keysAreScopedPerUser() {
        PayoutResult first = payouts.request("user-1", "key-1", REQUEST);
        PayoutResult second = payouts.request("user-2", "key-1", REQUEST);

        assertThat(second.replayed()).isFalse();
        assertThat(second.payout().id()).isNotEqualTo(first.payout().id());
    }

    @Test
    void fiftyConcurrentRetriesCreateExactlyOnePayout() throws Exception {
        int callers = 50;
        CountDownLatch start = new CountDownLatch(1);
        List<Future<PayoutResult>> futures = new ArrayList<>();
        try (ExecutorService pool = Executors.newFixedThreadPool(callers)) {
            for (int i = 0; i < callers; i++) {
                futures.add(pool.submit(() -> {
                    start.await();
                    return payouts.request("user-1", "key-race", REQUEST);
                }));
            }
            start.countDown();
            List<PayoutResult> results = new ArrayList<>();
            for (Future<PayoutResult> f : futures) {
                results.add(f.get());
            }

            assertThat(rowCount()).isEqualTo(1);
            assertThat(results).filteredOn(r -> !r.replayed()).hasSize(1);
            assertThat(results).extracting(r -> r.payout().id()).containsOnly(results.getFirst().payout().id());
        }
    }

    @Test
    void listsNewestFirstAndFiltersByStatus() {
        UUID paid = insertPayout(PayoutStatus.PAID, "po_1");
        PayoutResult requested = payouts.request("user-1", "key-1", REQUEST);

        assertThat(payouts.list(Optional.empty(), 10)).extracting(Payout::id)
                .containsExactly(requested.payout().id(), paid);
        assertThat(payouts.list(Optional.of(PayoutStatus.PAID), 10)).extracting(Payout::id).containsExactly(paid);
    }

    private int rowCount() {
        return jdbc.sql("select count(*) from payouts").query(Integer.class).single();
    }
}
