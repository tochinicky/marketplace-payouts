package io.github.tochinicky.payouts.payout;

import io.github.tochinicky.payouts.stripe.StripeGateway;
import io.github.tochinicky.payouts.stripe.StripePayoutRejected;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class PayoutProcessor {

    private final JdbcClient jdbc;
    private final StripeGateway stripe;
    private final Duration claimTimeout;

    public PayoutProcessor(JdbcClient jdbc, StripeGateway stripe,
                           @Value("${payouts.claim-timeout:PT1M}") Duration claimTimeout) {
        this.jdbc = jdbc;
        this.stripe = stripe;
        this.claimTimeout = claimTimeout;
    }

    public List<UUID> due(int limit) {
        return jdbc.sql("""
                        select id from payouts
                        where status = 'requested'
                           or (status = 'processing' and stripe_payout_id is null
                               and updated_at < now() - make_interval(secs => :timeoutSeconds))
                        order by created_at
                        limit :limit""")
                .param("timeoutSeconds", claimTimeout.toSeconds())
                .param("limit", limit)
                .query(UUID.class)
                .list();
    }

    public void process(UUID payoutId) {
        Optional<Payout> claimed = claim(payoutId);
        if (claimed.isEmpty()) {
            return;
        }
        Payout payout = claimed.get();
        String stripePayoutId;
        try {
            stripePayoutId = stripe.createPayout(
                    payout.id(), payout.stripeAccountId(), payout.amountMinor(), payout.currency());
        } catch (StripePayoutRejected e) {
            markRejected(payout.id(), e.code());
            return;
        }
        jdbc.sql("""
                        update payouts set stripe_payout_id = :stripePayoutId, updated_at = now()
                        where id = :id and stripe_payout_id is null""")
                .param("stripePayoutId", stripePayoutId)
                .param("id", payout.id())
                .update();
    }

    private Optional<Payout> claim(UUID payoutId) {
        return jdbc.sql("""
                        update payouts set status = 'processing', updated_at = now()
                        where id = :id
                          and (status = 'requested'
                               or (status = 'processing' and stripe_payout_id is null
                                   and updated_at < now() - make_interval(secs => :timeoutSeconds)))
                        returning\s""" + PayoutRows.COLUMNS)
                .param("id", payoutId)
                .param("timeoutSeconds", claimTimeout.toSeconds())
                .query(PayoutRows.MAPPER)
                .optional();
    }

    private void markRejected(UUID payoutId, String code) {
        jdbc.sql("""
                        update payouts set status = 'failed', failure_code = :code, updated_at = now()
                        where id = :id and status = 'processing' and stripe_payout_id is null""")
                .param("code", code)
                .param("id", payoutId)
                .update();
    }
}
