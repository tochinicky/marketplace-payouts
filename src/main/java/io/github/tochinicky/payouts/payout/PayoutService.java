package io.github.tochinicky.payouts.payout;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class PayoutService {

    private final JdbcClient jdbc;

    public PayoutService(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public PayoutResult request(String userId, String idempotencyKey, PayoutRequest request) {
        String requestHash = hash(request);
        int inserted = jdbc.sql("""
                        insert into payouts (id, user_id, stripe_account_id, amount_minor, currency,
                                             idempotency_key, request_hash, status)
                        values (:id, :userId, :stripeAccountId, :amountMinor, :currency,
                                :idempotencyKey, :requestHash, 'requested')
                        on conflict (user_id, idempotency_key) do nothing""")
                .param("id", UUID.randomUUID())
                .param("userId", userId)
                .param("stripeAccountId", request.stripeAccountId())
                .param("amountMinor", request.amountMinor())
                .param("currency", request.currency())
                .param("idempotencyKey", idempotencyKey)
                .param("requestHash", requestHash)
                .update();

        var stored = jdbc.sql("select " + PayoutRows.COLUMNS + ", request_hash from payouts"
                        + " where user_id = :userId and idempotency_key = :idempotencyKey")
                .param("userId", userId)
                .param("idempotencyKey", idempotencyKey)
                .query((rs, rowNum) -> new Stored(PayoutRows.MAPPER.mapRow(rs, rowNum), rs.getString("request_hash")))
                .single();

        if (inserted == 1) {
            return new PayoutResult(stored.payout(), false);
        }
        if (!stored.requestHash().equals(requestHash)) {
            throw new IdempotencyKeyReused(idempotencyKey);
        }
        return new PayoutResult(stored.payout(), true);
    }

    public Optional<Payout> find(UUID id) {
        return jdbc.sql("select " + PayoutRows.COLUMNS + " from payouts where id = :id")
                .param("id", id)
                .query(PayoutRows.MAPPER)
                .optional();
    }

    public List<Payout> list(Optional<PayoutStatus> status, int limit) {
        String where = status.isPresent() ? " where status = :status" : "";
        var statement = jdbc.sql("select " + PayoutRows.COLUMNS + " from payouts" + where
                        + " order by created_at desc, id limit :limit")
                .param("limit", limit);
        if (status.isPresent()) {
            statement = statement.param("status", status.get().db());
        }
        return statement.query(PayoutRows.MAPPER).list();
    }

    private static String hash(PayoutRequest request) {
        String canonical = request.stripeAccountId() + "|" + request.amountMinor() + "|" + request.currency();
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(canonical.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private record Stored(Payout payout, String requestHash) {
    }
}
