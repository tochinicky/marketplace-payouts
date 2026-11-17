package io.github.tochinicky.payouts.payout;

import io.github.tochinicky.payouts.IntegrationTest;
import io.github.tochinicky.payouts.stripe.StripeCallFailed;
import io.github.tochinicky.payouts.stripe.StripeGateway;
import io.github.tochinicky.payouts.stripe.StripePayoutRejected;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class PayoutProcessorTest extends IntegrationTest {

    @Autowired
    PayoutProcessor processor;

    @MockitoSpyBean
    StripeGateway stripe;

    @Test
    void sendsARequestedPayoutToStripeAndStoresItsId() {
        UUID id = insertPayout(PayoutStatus.REQUESTED, null);

        processor.process(id);

        assertThat(statusOf(id)).isEqualTo(PayoutStatus.PROCESSING);
        assertThat(column(id, "stripe_payout_id")).startsWith("po_");
        verify(stripe).createPayout(id, "acct_1", 5000, "EUR");
    }

    @Test
    void aPayoutAlreadyBeingSentIsNotSentAgain() {
        UUID id = insertPayout(PayoutStatus.REQUESTED, null);

        processor.process(id);
        processor.process(id);

        verify(stripe, times(1)).createPayout(eq(id), anyString(), anyLong(), anyString());
    }

    @Test
    void tenWorkersRacingOnOnePayoutCallStripeOnce() throws Exception {
        UUID id = insertPayout(PayoutStatus.REQUESTED, null);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<?>> futures = new ArrayList<>();
        try (ExecutorService pool = Executors.newFixedThreadPool(10)) {
            for (int i = 0; i < 10; i++) {
                futures.add(pool.submit(() -> {
                    start.await();
                    processor.process(id);
                    return null;
                }));
            }
            start.countDown();
            for (Future<?> f : futures) {
                f.get();
            }
        }

        verify(stripe, times(1)).createPayout(eq(id), anyString(), anyLong(), anyString());
    }

    @Test
    void aRejectedPayoutIsMarkedFailedWithStripesCode() {
        UUID id = insertPayout(PayoutStatus.REQUESTED, null);
        doThrow(new StripePayoutRejected("balance_insufficient", new RuntimeException("insufficient funds")))
                .when(stripe).createPayout(eq(id), anyString(), anyLong(), anyString());

        processor.process(id);

        assertThat(statusOf(id)).isEqualTo(PayoutStatus.FAILED);
        assertThat(column(id, "failure_code")).isEqualTo("balance_insufficient");
    }

    @Test
    void aTransientFailureIsRetriedOnlyAfterTheClaimTimesOut() {
        UUID id = insertPayout(PayoutStatus.REQUESTED, null);
        doThrow(new StripeCallFailed(new RuntimeException("connection reset")))
                .when(stripe).createPayout(eq(id), anyString(), anyLong(), anyString());

        assertThatThrownBy(() -> processor.process(id)).isInstanceOf(StripeCallFailed.class);
        assertThat(statusOf(id)).isEqualTo(PayoutStatus.PROCESSING);
        assertThat(processor.due(10)).doesNotContain(id);

        ageClaim(id);
        assertThat(processor.due(10)).contains(id);
    }

    @Test
    void aClaimLeftByACrashedWorkerIsResentWithTheSameIdempotencyKey() {
        UUID id = insertPayout(PayoutStatus.PROCESSING, null);
        ageClaim(id);

        processor.process(id);

        verify(stripe).createPayout(id, "acct_1", 5000, "EUR");
        assertThat(column(id, "stripe_payout_id")).startsWith("po_");
    }

    @Test
    void aFreshClaimHeldByAnotherWorkerIsLeftAlone() {
        UUID id = insertPayout(PayoutStatus.PROCESSING, null);

        processor.process(id);

        verify(stripe, never()).createPayout(any(), anyString(), anyLong(), anyString());
    }

    private void ageClaim(UUID id) {
        jdbc.sql("update payouts set updated_at = now() - interval '2 minutes' where id = :id").param("id", id).update();
    }
}
