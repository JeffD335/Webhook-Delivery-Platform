package dev.webhook.platform.delivery.api;

import dev.webhook.platform.delivery.domain.DeliveryAttemptStatus;
import dev.webhook.platform.delivery.persistence.DeliveryAttemptEntity;

import java.time.Instant;
import java.util.UUID;

public record DeliveryAttemptResponse(
        UUID id,
        UUID deliveryId,
        Integer attemptNumber,
        DeliveryAttemptStatus status,
        Integer httpStatus,
        String errorMessage,
        Instant startedAt,
        Instant finishedAt
) {
        public static DeliveryAttemptResponse from(DeliveryAttemptEntity deliveryAttemptEntity) {
            return new DeliveryAttemptResponse(
                    deliveryAttemptEntity.getId(),
                    deliveryAttemptEntity.getDeliveryId(),
                    deliveryAttemptEntity.getAttemptNumber(),
                    deliveryAttemptEntity.getStatus(),
                    deliveryAttemptEntity.getHttpStatus(),
                    deliveryAttemptEntity.getErrorMessage(),
                    deliveryAttemptEntity.getStartedAt(),
                    deliveryAttemptEntity.getFinishedAt()
            );
        }
}
