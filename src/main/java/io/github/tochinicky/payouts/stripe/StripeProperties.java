package io.github.tochinicky.payouts.stripe;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("stripe")
public record StripeProperties(String apiKey, String apiBase, String webhookSecret) {
}
