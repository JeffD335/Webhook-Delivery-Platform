package dev.webhook.platform.delivery.application;

/**
 * High-level result of one worker tick.
 */
public enum ProcessOutcome {
    NO_PENDING_DELIVERY,
    SUCCEEDED,
    FAILED
}
