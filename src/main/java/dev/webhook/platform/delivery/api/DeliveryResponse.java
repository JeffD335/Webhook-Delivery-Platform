package dev.webhook.platform.delivery.api;

import dev.webhook.platform.delivery.domain.DeliveryStatus;
import dev.webhook.platform.delivery.persistence.DeliveryEntity;

import java.time.Instant;
import java.util.UUID;

public record DeliveryResponse(
        UUID id,
        UUID eventId,
        UUID endpointId,
        DeliveryStatus status,
        int attemptCount,
        Instant nextAttemptAt,
        Instant lastAttemptAt,
        String lastError,
        String claimedBy,
        Instant claimExpiresAt,
        Instant createdAt,
        Instant updatedAt) {

    public static DeliveryResponse from(DeliveryEntity delivery) {
        return new DeliveryResponse(
                delivery.getId(),
                delivery.getEventId(),
                delivery.getEndpointId(),
                delivery.getStatus(),
                delivery.getAttemptCount(),
                delivery.getNextAttemptAt(),
                delivery.getLastAttemptAt(),
                delivery.getLastError(),
                delivery.getClaimedBy(),
                delivery.getClaimExpiresAt(),
                delivery.getCreatedAt(),
                delivery.getUpdatedAt());
    }
}
