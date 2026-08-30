package dev.webhook.platform.delivery.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DeliveryStatusTest {

    @Test
    void deliveryStatus_containsExpectedValues() {
        assertThat(DeliveryStatus.values())
                .containsExactlyInAnyOrder(
                        DeliveryStatus.PENDING,
                        DeliveryStatus.IN_PROGRESS,
                        DeliveryStatus.RETRY_SCHEDULED,
                        DeliveryStatus.SUCCEEDED,
                        DeliveryStatus.FAILED
                );
    }
}
