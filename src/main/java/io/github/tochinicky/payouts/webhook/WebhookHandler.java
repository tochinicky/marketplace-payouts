package io.github.tochinicky.payouts.webhook;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WebhookHandler {

    private final JdbcClient jdbc;

    public WebhookHandler(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional
    public void handle(PayoutEvent event) {
        int recorded = jdbc.sql("""
                        insert into stripe_events (event_id, type) values (:eventId, :type)
                        on conflict (event_id) do nothing""")
                .param("eventId", event.eventId())
                .param("type", event.type())
                .update();
        if (recorded == 0) {
            return;
        }
        switch (event.type()) {
            case "payout.paid" -> markPaid(event);
            case "payout.failed" -> markFailed(event, event.failureCode());
            case "payout.canceled" -> markFailed(event, "canceled");
            default -> {
            }
        }
    }

    private void markPaid(PayoutEvent event) {
        jdbc.sql("""
                        update payouts
                        set status = 'paid', stripe_payout_id = coalesce(stripe_payout_id, :stripePayoutId),
                            updated_at = now()
                        where (id = cast(:payoutId as uuid) or stripe_payout_id = :stripePayoutId)
                          and status = 'processing'""")
                .param("payoutId", event.payoutId())
                .param("stripePayoutId", event.stripePayoutId())
                .update();
    }

    private void markFailed(PayoutEvent event, String failureCode) {
        jdbc.sql("""
                        update payouts
                        set status = 'failed', failure_code = :failureCode,
                            stripe_payout_id = coalesce(stripe_payout_id, :stripePayoutId), updated_at = now()
                        where (id = cast(:payoutId as uuid) or stripe_payout_id = :stripePayoutId)
                          and status in ('processing', 'paid')""")
                .param("failureCode", failureCode)
                .param("payoutId", event.payoutId())
                .param("stripePayoutId", event.stripePayoutId())
                .update();
    }
}
