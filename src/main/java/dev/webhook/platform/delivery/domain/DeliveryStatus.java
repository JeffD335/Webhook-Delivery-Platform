package dev.webhook.platform.delivery.domain;

/**
 * Delivery lifecycle states.
 */
public enum DeliveryStatus {

    PENDING,
    IN_PROGRESS,
    RETRY_SCHEDULED,
    SUCCEEDED,
    FAILED
}
