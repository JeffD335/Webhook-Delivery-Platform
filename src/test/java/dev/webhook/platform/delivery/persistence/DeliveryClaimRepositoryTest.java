package dev.webhook.platform.delivery.persistence;

import dev.webhook.platform.delivery.claim.DeliveryClaimer;
import dev.webhook.platform.delivery.domain.DeliveryStatus;
import dev.webhook.platform.testsupport.ApiIntegrationTestSupport;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;

class DeliveryClaimRepositoryTest extends ApiIntegrationTestSupport {

    @Autowired
    private DeliveryRepository deliveryRepository;
    @Autowired
    private DeliveryClaimer deliveryClaimer;
    @BeforeEach
    void clearDatabase() {
        clearWebhookTables();
    }

    @Test

    void claimNextDueDelivery_claimsPendingDelivery() {
        UUID eventId = insertEvent("order.created", "{\"orderId\":\"ord_claim\"}");
        UUID endpointId = insertEndpoint("Receiver", "https://receiver.example/webhook");
        UUID deliveryId = insertDeliveryAt(
                eventId,
                endpointId,
                "PENDING",
                Instant.parse("2026-01-01T00:00:00Z"),
                null,
                null,
                null);

        Optional<DeliveryEntity> delivery = deliveryClaimer.claimNextDueDelivery(
                "worker-1",
                Instant.parse("2026-01-01T00:01:00Z"),
                Duration.ofMinutes(5));

        assertThat(delivery).isPresent();
        assertThat(delivery.get().getId()).isEqualTo(deliveryId);
        assertThat(delivery.get().getStatus()).isEqualTo(DeliveryStatus.IN_PROGRESS);
        assertThat(delivery.get().getClaimedBy()).isEqualTo("worker-1");
        assertThat(delivery.get().getClaimExpiresAt()).isEqualTo(Instant.parse("2026-01-01T00:06:00Z"));
    }

    @Test

    void claimNextDueDelivery_doesNotClaimActiveInProgressDelivery() {
        UUID eventId = insertEvent("order.created", "{\"orderId\":\"ord_active_claim\"}");
        UUID endpointId = insertEndpoint("Receiver", "https://receiver.example/webhook");
        UUID deliveryId = insertDeliveryAt(
                eventId,
                endpointId,
                "IN_PROGRESS",
                Instant.parse("2026-01-01T00:00:00Z"),
                null,
                "worker-1",
                Instant.parse("2026-01-01T00:05:00Z"));

        Optional<DeliveryEntity> delivery = deliveryClaimer.claimNextDueDelivery(
                "worker-2",
                Instant.parse("2026-01-01T00:01:00Z"),
                Duration.ofMinutes(5));

        assertThat(delivery).isEmpty();

        DeliveryEntity unchanged = deliveryRepository.findById(deliveryId).orElseThrow();
        assertThat(unchanged.getStatus()).isEqualTo(DeliveryStatus.IN_PROGRESS);
        assertThat(unchanged.getClaimedBy()).isEqualTo("worker-1");
        assertThat(unchanged.getClaimExpiresAt()).isEqualTo(Instant.parse("2026-01-01T00:05:00Z"));
    }

    @Test
    void expiredClaim_canBeReclaimed() {
        UUID eventId = insertEvent("order.created", "{\"orderId\":\"ord_expired_claim\"}");
        UUID endpointId = insertEndpoint("Receiver", "https://receiver.example/webhook");
        UUID deliveryId = insertDeliveryAt(
                eventId,
                endpointId,
                "IN_PROGRESS",
                Instant.parse("2026-01-01T00:00:00Z"),
                null,
                "worker-1",
                Instant.parse("2026-01-01T00:01:00Z"));

        Optional<DeliveryEntity> delivery = deliveryClaimer.claimNextDueDelivery(
                "worker-2",
                Instant.parse("2026-01-01T00:02:00Z"),
                Duration.ofMinutes(5));

        assertThat(delivery).isPresent();
        assertThat(delivery.get().getId()).isEqualTo(deliveryId);
        assertThat(delivery.get().getStatus()).isEqualTo(DeliveryStatus.IN_PROGRESS);
        assertThat(delivery.get().getClaimedBy()).isEqualTo("worker-2");
        assertThat(delivery.get().getClaimExpiresAt()).isEqualTo(Instant.parse("2026-01-01T00:07:00Z"));
    }

    private UUID insertDeliveryAt(
            UUID eventId,
            UUID endpointId,
            String status,
            Instant createdAt,
            Instant nextAttemptAt,
            String claimedBy,
            Instant claimExpiresAt) {

        UUID deliveryId = UUID.randomUUID();
        jdbcTemplate.update("""
                        INSERT INTO webhook_deliveries(
                            id,
                            event_id,
                            endpoint_id,
                            status,
                            created_at,
                            next_attempt_at,
                            claimed_by,
                            claim_expires_at,
                            updated_at
                        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                deliveryId,
                eventId,
                endpointId,
                status,
                createdAt,
                nextAttemptAt,
                claimedBy,
                claimExpiresAt,
                createdAt);
        return deliveryId;
    }
}
