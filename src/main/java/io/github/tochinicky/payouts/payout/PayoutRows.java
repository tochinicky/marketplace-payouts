package io.github.tochinicky.payouts.payout;

import org.springframework.jdbc.core.RowMapper;

import java.time.OffsetDateTime;
import java.util.UUID;

final class PayoutRows {

    static final String COLUMNS = """
            id, user_id, stripe_account_id, amount_minor, currency, status,
            stripe_payout_id, failure_code, created_at, updated_at""";

    static final RowMapper<Payout> MAPPER = (rs, rowNum) -> new Payout(
            rs.getObject("id", UUID.class),
            rs.getString("user_id"),
            rs.getString("stripe_account_id"),
            rs.getLong("amount_minor"),
            rs.getString("currency"),
            PayoutStatus.fromDb(rs.getString("status")),
            rs.getString("stripe_payout_id"),
            rs.getString("failure_code"),
            rs.getObject("created_at", OffsetDateTime.class),
            rs.getObject("updated_at", OffsetDateTime.class));

    private PayoutRows() {
    }
}
