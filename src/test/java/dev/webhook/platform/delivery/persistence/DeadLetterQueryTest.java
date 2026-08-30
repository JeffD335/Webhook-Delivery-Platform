package dev.webhook.platform.delivery.persistence;

import dev.webhook.platform.delivery.domain.DeliveryStatus;
import dev.webhook.platform.testsupport.ApiIntegrationTestSupport;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;

public class DeadLetterQueryTest extends ApiIntegrationTestSupport {
    @Autowired
    private DeliveryRepository deliveryRepository;

    @BeforeEach
    void clearDatabase() {
        clearWebhookTables();
    }

    @Test
    void findFailedDeliveries_returnsOnlyFailedDeliveriesNewestFirst() {
        UUID eventId = insertEvent("order.created", "{\"orderId\":\"ord_dlq\"}");
        UUID failedOldEndpoint = insertEndpoint("Failed old", "https://old.example/webhook");
        UUID pendingEndpoint = insertEndpoint("Pending", "https://pending.example/webhook");
        UUID failedNewEndpoint = insertEndpoint("Failed new", "https://new.example/webhook");
        UUID newFailed = insertDeliveryWithUpdatedAt(eventId, failedNewEndpoint, "FAILED", Instant.parse("2026-01-01T00:00:01Z"));
        UUID oldFailed = insertDeliveryWithUpdatedAt(eventId, failedOldEndpoint, "FAILED", Instant.parse("2026-01-01T00:00:00Z"));
        UUID pendingFailed = insertDeliveryWithUpdatedAt(eventId, pendingEndpoint, "PENDING", Instant.parse("2026-01-01T00:00:03Z"));

        List<DeliveryEntity> deadLetters = deliveryRepository.findByStatusOrderByUpdatedAtDesc(DeliveryStatus.FAILED);

        assertThat(deadLetters).extracting(DeliveryEntity::getId).containsExactly(newFailed, oldFailed);

    }
}
