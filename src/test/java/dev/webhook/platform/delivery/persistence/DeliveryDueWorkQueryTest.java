package dev.webhook.platform.delivery.persistence;

import dev.webhook.platform.delivery.domain.DeliveryStatus;
import dev.webhook.platform.testsupport.ApiIntegrationTestSupport;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;

class DeliveryDueWorkQueryTest extends ApiIntegrationTestSupport {

    @Autowired
    private DeliveryRepository deliveryRepository;

    @BeforeEach
    void clearDatabase() {
        clearWebhookTables();
    }

    @Test
    void findDueDelivery_returnsPendingDelivery() {
        UUID eventId = insertEvent("order.created", "{\"orderId\":\"ord_1\"}");
        UUID endpointId = insertEndpoint("Receiver", "https://receiver.example/webhook");
        UUID deliveryId = insertDeliveryAt(
                eventId,
                endpointId,
                "PENDING",
                Instant.parse("2026-01-01T00:00:00Z"),
                null);

        Optional<DeliveryEntity> result = deliveryRepository
                .findFirstDueDelivery(Instant.parse("2026-01-01T00:01:00Z"));

        assertThat(result).isPresent();
        assertThat(result.get().getId()).isEqualTo(deliveryId);
    }

    @Test
    void findDueDelivery_returnsScheduledRetryWhenDue() {
        UUID eventId = insertEvent("order.created", "{\"orderId\":\"ord_2\"}");
        UUID endpointId = insertEndpoint("Receiver", "https://receiver.example/webhook");
        UUID deliveryId = insertDeliveryAt(
                eventId,
                endpointId,
                "RETRY_SCHEDULED",
                Instant.parse("2026-01-01T00:00:00Z"),
                Instant.parse("2026-01-01T00:00:10Z"));

        Optional<DeliveryEntity> result = deliveryRepository
                .findFirstDueDelivery(Instant.parse("2026-01-01T00:00:10Z"));

        assertThat(result).isPresent();
        assertThat(result.get().getId()).isEqualTo(deliveryId);
        assertThat(result.get().getStatus()).isEqualTo(DeliveryStatus.RETRY_SCHEDULED);
    }

    @Test
    void findDueDelivery_ignoresScheduledRetryWhenNotDue() {
        UUID eventId = insertEvent("order.created", "{\"orderId\":\"ord_3\"}");
        UUID endpointId = insertEndpoint("Receiver", "https://receiver.example/webhook");
        insertDeliveryAt(
                eventId,
                endpointId,
                "RETRY_SCHEDULED",
                Instant.parse("2026-01-01T00:00:00Z"),
                Instant.parse("2026-01-01T00:00:30Z"));

        Optional<DeliveryEntity> result = deliveryRepository
                .findFirstDueDelivery(Instant.parse("2026-01-01T00:00:29Z"));

        assertThat(result).isEmpty();
    }

    @Test
    void findDueDelivery_ignoresTerminalDeliveries() {
        UUID eventId = insertEvent("order.created", "{\"orderId\":\"ord_4\"}");
        UUID endpointA = insertEndpoint("A", "https://a.example/webhook");
        UUID endpointB = insertEndpoint("B", "https://b.example/webhook");
        insertDeliveryAt(
                eventId,
                endpointA,
                "SUCCEEDED",
                Instant.parse("2026-01-01T00:00:00Z"),
                null);
        insertDeliveryAt(
                eventId,
                endpointB,
                "FAILED",
                Instant.parse("2026-01-01T00:00:01Z"),
                null);

        Optional<DeliveryEntity> result = deliveryRepository
                .findFirstDueDelivery(Instant.parse("2026-01-01T00:01:00Z"));

        assertThat(result).isEmpty();
    }

    private UUID insertDeliveryAt(
            UUID eventId,
            UUID endpointId,
            String status,
            Instant createdAt,
            Instant nextAttemptAt) {
        UUID deliveryId = UUID.randomUUID();
        jdbcTemplate.update("""
                        INSERT INTO webhook_deliveries(
                            id, event_id, endpoint_id, status, created_at, next_attempt_at, updated_at
                        ) VALUES (?, ?, ?, ?, ?, ?, ?)
                        """,
                deliveryId,
                eventId,
                endpointId,
                status,
                createdAt,
                nextAttemptAt,
                createdAt);
        return deliveryId;
    }
}
