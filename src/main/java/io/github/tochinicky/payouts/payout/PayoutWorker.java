package io.github.tochinicky.payouts.payout;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@ConditionalOnProperty(name = "payouts.worker.enabled", havingValue = "true", matchIfMissing = true)
public class PayoutWorker {

    private static final Logger log = LoggerFactory.getLogger(PayoutWorker.class);

    private final PayoutProcessor processor;

    public PayoutWorker(PayoutProcessor processor) {
        this.processor = processor;
    }

    @Scheduled(fixedDelayString = "${payouts.worker.delay:PT1S}")
    public void sendDuePayouts() {
        for (UUID id : processor.due(20)) {
            try {
                processor.process(id);
            } catch (RuntimeException e) {
                log.warn("payout {} not sent, will retry after the claim timeout: {}", id, e.getMessage());
            }
        }
    }
}
