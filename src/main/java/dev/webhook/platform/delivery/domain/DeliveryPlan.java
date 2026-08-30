package dev.webhook.platform.delivery.domain;

import java.util.UUID;

/**
 * Pure planning result used before creating Delivery entities.
 */
public record DeliveryPlan(
        UUID endpointId,
        DeliveryStatus status) {
}
