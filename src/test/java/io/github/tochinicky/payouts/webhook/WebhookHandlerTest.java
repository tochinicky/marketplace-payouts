package io.github.tochinicky.payouts.webhook;

import io.github.tochinicky.payouts.IntegrationTest;
import io.github.tochinicky.payouts.payout.PayoutStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class WebhookHandlerTest extends IntegrationTest {

    @Autowired
    WebhookHandler handler;

    @Test
    void paidMovesAProcessingPayoutToPaid() {
        UUID id = insertPayout(PayoutStatus.PROCESSING, "po_1");

        handler.handle(new PayoutEvent("evt_1", "payout.paid", "po_1", null, null));

        assertThat(statusOf(id)).isEqualTo(PayoutStatus.PAID);
    }

    @Test
    void aRedeliveredEventIsRecordedOnce() {
        insertPayout(PayoutStatus.PROCESSING, "po_1");
        PayoutEvent paid = new PayoutEvent("evt_1", "payout.paid", "po_1", null, null);

        handler.handle(paid);
        handler.handle(paid);

        assertThat(jdbc.sql("select count(*) from stripe_events").query(Integer.class).single()).isEqualTo(1);
    }

    @Test
    void aPaidPayoutCanStillFailLater() {
        UUID id = insertPayout(PayoutStatus.PROCESSING, "po_1");

        handler.handle(new PayoutEvent("evt_1", "payout.paid", "po_1", null, null));
        handler.handle(new PayoutEvent("evt_2", "payout.failed", "po_1", null, "account_closed"));

        assertThat(statusOf(id)).isEqualTo(PayoutStatus.FAILED);
        assertThat(column(id, "failure_code")).isEqualTo("account_closed");
    }

    @Test
    void aPaidEventArrivingAfterFailedDoesNotRevive() {
        UUID id = insertPayout(PayoutStatus.PROCESSING, "po_1");

        handler.handle(new PayoutEvent("evt_2", "payout.failed", "po_1", null, "account_closed"));
        handler.handle(new PayoutEvent("evt_1", "payout.paid", "po_1", null, null));

        assertThat(statusOf(id)).isEqualTo(PayoutStatus.FAILED);
    }

    @Test
    void anEventThatBeatsTheStripeResponseIsMatchedByMetadata() {
        UUID id = insertPayout(PayoutStatus.PROCESSING, null);

        handler.handle(new PayoutEvent("evt_1", "payout.paid", "po_9", id, null));

        assertThat(statusOf(id)).isEqualTo(PayoutStatus.PAID);
        assertThat(column(id, "stripe_payout_id")).isEqualTo("po_9");
    }

    @Test
    void canceledIsStoredAsFailed() {
        UUID id = insertPayout(PayoutStatus.PROCESSING, "po_1");

        handler.handle(new PayoutEvent("evt_1", "payout.canceled", "po_1", null, null));

        assertThat(statusOf(id)).isEqualTo(PayoutStatus.FAILED);
        assertThat(column(id, "failure_code")).isEqualTo("canceled");
    }

    @Test
    void anEventForAnUnknownPayoutIsRecordedAndIgnored() {
        handler.handle(new PayoutEvent("evt_1", "payout.paid", "po_unknown", null, null));

        assertThat(jdbc.sql("select count(*) from stripe_events").query(Integer.class).single()).isEqualTo(1);
    }
}
