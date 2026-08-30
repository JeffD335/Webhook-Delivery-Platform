package dev.webhook.platform.delivery.persistence;

import dev.webhook.platform.delivery.domain.DeliveryStatus;
import dev.webhook.platform.endpoint.persistence.EndpointEntity;
import dev.webhook.platform.endpoint.persistence.EndpointRepository;
import dev.webhook.platform.event.persistence.EventEntity;
import dev.webhook.platform.event.persistence.EventRepository;
import dev.webhook.platform.testsupport.ApiIntegrationTestSupport;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Lab03 database tests for the queries the worker depends on.
 */
class DeliveryWorkerPersistenceTest extends ApiIntegrationTestSupport {

    @Autowired
    private DeliveryRepository deliveryRepository;

    @Autowired
    private EventRepository eventRepository;

    @Autowired
    private EndpointRepository endpointRepository;

    @BeforeEach
    void clearDatabase() {
        clearWebhookTables();
    }

    @Test
    void findNextPendingDelivery_returnsOldestPendingDelivery() {
        UUID eventId = insertEvent("order.created", "{\"orderId\":\"ord_1\"}", Instant.parse("2026-01-01T00:00:00Z"));
        UUID endpointA = insertEndpoint("A", "https://a.example/webhook", Instant.parse("2026-01-01T00:00:00Z"));
        UUID endpointB = insertEndpoint("B", "https://b.example/webhook", Instant.parse("2026-01-01T00:00:00Z"));
        UUID olderDelivery = insertDelivery(
                eventId,
                endpointA,
                "PENDING",
                Instant.parse("2026-01-01T00:00:01Z"));
        insertDelivery(
                eventId,
                endpointB,
                "PENDING",
                Instant.parse("2026-01-01T00:00:02Z"));

        Optional<DeliveryEntity> result = deliveryRepository
                .findFirstByStatusOrderByCreatedAtAsc(DeliveryStatus.PENDING);

        assertThat(result).isPresent();
        assertThat(result.get().getId()).isEqualTo(olderDelivery);
    }

    @Test
    void findNextPendingDelivery_ignoresNonPendingDeliveries() {
        UUID eventId = insertEvent("order.created", "{\"orderId\":\"ord_2\"}", Instant.parse("2026-01-01T00:00:00Z"));
        UUID endpointA = insertEndpoint("A", "https://a.example/webhook", Instant.parse("2026-01-01T00:00:00Z"));
        UUID endpointB = insertEndpoint("B", "https://b.example/webhook", Instant.parse("2026-01-01T00:00:00Z"));
        UUID endpointC = insertEndpoint("C", "https://c.example/webhook", Instant.parse("2026-01-01T00:00:00Z"));
        insertDelivery(
                eventId,
                endpointA,
                "SUCCEEDED",
                Instant.parse("2026-01-01T00:00:01Z"));
        insertDelivery(
                eventId,
                endpointB,
                "FAILED",
                Instant.parse("2026-01-01T00:00:02Z"));
        UUID pendingDelivery = insertDelivery(
                eventId,
                endpointC,
                "PENDING",
                Instant.parse("2026-01-01T00:00:03Z"));

        Optional<DeliveryEntity> result = deliveryRepository
                .findFirstByStatusOrderByCreatedAtAsc(DeliveryStatus.PENDING);

        assertThat(result).isPresent();
        assertThat(result.get().getId()).isEqualTo(pendingDelivery);
    }

    @Test
    void updateDeliveryStatus_changesOnlyTheSelectedDelivery() {
        UUID eventId = insertEvent("order.created", "{\"orderId\":\"ord_3\"}", Instant.parse("2026-01-01T00:00:00Z"));
        UUID endpointA = insertEndpoint("A", "https://a.example/webhook", Instant.parse("2026-01-01T00:00:00Z"));
        UUID endpointB = insertEndpoint("B", "https://b.example/webhook", Instant.parse("2026-01-01T00:00:00Z"));
        UUID selectedDelivery = insertDelivery(
                eventId,
                endpointA,
                "PENDING",
                Instant.parse("2026-01-01T00:00:01Z"));
        UUID untouchedDelivery = insertDelivery(
                eventId,
                endpointB,
                "PENDING",
                Instant.parse("2026-01-01T00:00:02Z"));

        DeliveryEntity delivery = deliveryRepository.findById(selectedDelivery).orElseThrow();
        delivery.markSucceeded();
        deliveryRepository.saveAndFlush(delivery);

        assertThat(deliveryStatus(selectedDelivery)).isEqualTo("SUCCEEDED");
        assertThat(deliveryStatus(untouchedDelivery)).isEqualTo("PENDING");
    }

    @Test
    void workerQuery_canLoadEndpointUrlAndEventPayloadForDelivery() {
        String payload = "{\"orderId\":\"ord_4\"}";
        String endpointUrl = "https://receiver.example/webhook";
        UUID eventId = insertEvent("order.created", payload, Instant.parse("2026-01-01T00:00:00Z"));
        UUID endpointId = insertEndpoint("Receiver", endpointUrl, Instant.parse("2026-01-01T00:00:00Z"));
        UUID deliveryId = insertDelivery(
                eventId,
                endpointId,
                "PENDING",
                Instant.parse("2026-01-01T00:00:01Z"));

        DeliveryEntity delivery = deliveryRepository.findById(deliveryId).orElseThrow();
        EventEntity event = eventRepository.findById(delivery.getEventId()).orElseThrow();
        EndpointEntity endpoint = endpointRepository.findById(delivery.getEndpointId()).orElseThrow();

        assertThat(delivery.getId()).isEqualTo(deliveryId);
        assertThat(event.getPayload()).isEqualTo(payload);
        assertThat(endpoint.getUrl()).isEqualTo(endpointUrl);
    }

    protected UUID insertEvent(String type, String payloadJson, Instant createdAt) {
        UUID eventId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO webhook_events(id, event_type, payload, created_at) VALUES (?, ?, ?, ?)",
                eventId,
                type,
                payloadJson,
                createdAt);
        return eventId;
    }

    protected UUID insertEndpoint(String name, String url, Instant createdAt) {
        UUID endpointId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO webhook_endpoints(id, name, url, created_at) VALUES (?, ?, ?, ?)",
                endpointId,
                name,
                url,
                createdAt);
        return endpointId;
    }

    protected UUID insertDelivery(UUID eventId, UUID endpointId, String status, Instant createdAt) {
        UUID deliveryId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO webhook_deliveries(id, event_id, endpoint_id, status, created_at) VALUES (?, ?, ?, ?, ?)",
                deliveryId,
                eventId,
                endpointId,
                status,
                createdAt);
        return deliveryId;
    }

    private String deliveryStatus(UUID deliveryId) {
        return jdbcTemplate.queryForObject(
                "SELECT status FROM webhook_deliveries WHERE id = ?",
                String.class,
                deliveryId);
    }
}
