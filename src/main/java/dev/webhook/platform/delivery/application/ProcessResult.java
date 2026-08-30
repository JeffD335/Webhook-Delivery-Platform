package dev.webhook.platform.delivery.application;

import java.util.UUID;

/**
 * Return value from processing one pending Delivery.
 */
public record ProcessResult(ProcessOutcome outcome, UUID deliveryId) {

    public static ProcessResult noPendingDelivery() {
        return new ProcessResult(ProcessOutcome.NO_PENDING_DELIVERY, null);
    }

    public static ProcessResult succeeded(UUID deliveryId) {
        return new ProcessResult(ProcessOutcome.SUCCEEDED, deliveryId);
    }

    public static ProcessResult failed(UUID deliveryId) {
        return new ProcessResult(ProcessOutcome.FAILED, deliveryId);
    }
}
