package io.github.tochinicky.payouts;

import io.github.tochinicky.payouts.payout.PayoutStatus;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.util.UUID;

@SpringBootTest(properties = "payouts.worker.enabled=false")
@Import(TestcontainersConfiguration.class)
public abstract class IntegrationTest {

    @Autowired
    protected JdbcClient jdbc;

    @BeforeEach
    void cleanDatabase() {
        jdbc.sql("truncate payouts, stripe_events").update();
    }

    protected UUID insertPayout(PayoutStatus status, String stripePayoutId) {
        UUID id = UUID.randomUUID();
        jdbc.sql("""
                        insert into payouts (id, user_id, stripe_account_id, amount_minor, currency,
                                             idempotency_key, request_hash, status, stripe_payout_id)
                        values (:id, 'user-1', 'acct_1', 5000, 'EUR', :key, 'hash', :status, :stripePayoutId)""")
                .param("id", id)
                .param("key", "key-" + id)
                .param("status", status.db())
                .param("stripePayoutId", stripePayoutId)
                .update();
        return id;
    }

    protected PayoutStatus statusOf(UUID id) {
        return PayoutStatus.fromDb(jdbc.sql("select status from payouts where id = :id")
                .param("id", id).query(String.class).single());
    }

    protected String column(UUID id, String column) {
        return jdbc.sql("select " + column + " from payouts where id = :id")
                .param("id", id).query(String.class).single();
    }
}
