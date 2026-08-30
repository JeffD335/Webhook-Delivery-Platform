package dev.webhook.platform.delivery.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DeliveryAttemptStatusTest {

    @Test
    void deliveryAttemptStatus_containsExpectedValues() {
        assertThat(DeliveryAttemptStatus.values())
                .containsExactly(
                        DeliveryAttemptStatus.STARTED,
                        DeliveryAttemptStatus.SUCCEEDED,
                        DeliveryAttemptStatus.FAILED);
    }
}
