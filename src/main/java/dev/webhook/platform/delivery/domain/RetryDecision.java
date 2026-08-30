package dev.webhook.platform.delivery.domain;

import java.time.Instant;

public record RetryDecision(
        boolean shouldRetry,
        Instant nextAttemptAt) {
}
