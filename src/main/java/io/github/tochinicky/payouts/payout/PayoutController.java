package io.github.tochinicky.payouts.payout;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping("/payouts")
@Validated
public class PayoutController {

    private final PayoutService payouts;

    public PayoutController(PayoutService payouts) {
        this.payouts = payouts;
    }

    @PostMapping
    public ResponseEntity<Payout> request(@RequestHeader("X-User-Id") @NotBlank String userId,
                                          @RequestHeader("Idempotency-Key") @NotBlank @Size(max = 255) String key,
                                          @Valid @RequestBody PayoutRequest body) {
        PayoutResult result = payouts.request(userId, key, body);
        if (result.replayed()) {
            return ResponseEntity.ok().header("Idempotent-Replayed", "true").body(result.payout());
        }
        return ResponseEntity.created(URI.create("/payouts/" + result.payout().id())).body(result.payout());
    }

    @GetMapping("/{id}")
    public ResponseEntity<Payout> get(@PathVariable UUID id) {
        return ResponseEntity.of(payouts.find(id));
    }

    @GetMapping
    public List<Payout> list(@RequestParam Optional<PayoutStatus> status,
                             @RequestParam(defaultValue = "50") @Min(1) @Max(200) int limit) {
        return payouts.list(status, limit);
    }

    @ExceptionHandler(IdempotencyKeyReused.class)
    public ProblemDetail keyReused(IdempotencyKeyReused e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_CONTENT, e.getMessage());
    }
}
