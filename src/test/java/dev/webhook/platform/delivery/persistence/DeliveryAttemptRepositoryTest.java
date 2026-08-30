package dev.webhook.platform.delivery.persistence;

import dev.webhook.platform.delivery.domain.DeliveryAttemptStatus;
import dev.webhook.platform.testsupport.ApiIntegrationTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;

class DeliveryAttemptRepositoryTest extends ApiIntegrationTestSupport {
    @Autowired
    private DeliveryAttemptRepository deliveryAttemptRepository;
    @BeforeEach
    void clearDatabase() {clearWebhookTables();}
    @Test
    void saveAttempt_persistsAttemptHistory() {
        UUID eventId = insertEvent("order.created", "{\"orderId\":\"ord_1\"}");
        UUID endpointId = insertEndpoint("Receiver", "https://receiver.example/webhook");
        UUID deliveryId = insertDelivery(eventId, endpointId, "PENDING");
        UUID attemptId = UUID.randomUUID();
        Instant startedAt = Instant.parse("2026-01-01T00:00:00Z");

        DeliveryAttemptEntity attempt = new DeliveryAttemptEntity(
                attemptId,
                deliveryId,
                1,
                DeliveryAttemptStatus.STARTED,
                null,
                null,
                startedAt
        );

        deliveryAttemptRepository.saveAndFlush(attempt);

        DeliveryAttemptEntity saved = deliveryAttemptRepository
                .findById(attemptId)
                .orElseThrow();

        assertThat(saved.getId()).isEqualTo(attemptId);
        assertThat(saved.getDeliveryId()).isEqualTo(deliveryId);
        assertThat(saved.getAttemptNumber()).isEqualTo(1);
        assertThat(saved.getStatus()).isEqualTo(DeliveryAttemptStatus.STARTED);
        assertThat(saved.getHttpStatus()).isNull();
        assertThat(saved.getErrorMessage()).isNull();
        assertThat(saved.getStartedAt()).isEqualTo(startedAt);
        assertThat(saved.getFinishedAt()).isNull();
    }

    @Test
    void findByDeliveryId_returnsAttemptsForOneDelivery() {
        UUID eventId = insertEvent("order.created", "{\"orderId\":\"ord_2\"}");
        UUID endpointA = insertEndpoint("A", "https://a.example/webhook");
        UUID endpointB = insertEndpoint("B", "https://b.example/webhook");
        UUID deliveryA = insertDelivery(eventId, endpointA, "PENDING");
        UUID deliveryB = insertDelivery(eventId, endpointB, "PENDING");

        DeliveryAttemptEntity secondAttempt = new DeliveryAttemptEntity(
                UUID.randomUUID(),
                deliveryA,
                2,
                DeliveryAttemptStatus.FAILED,
                503,
                "service unavailable",
                Instant.parse("2026-01-01T00:00:20Z")
        );
        DeliveryAttemptEntity firstAttempt = new DeliveryAttemptEntity(
                UUID.randomUUID(),
                deliveryA,
                1,
                DeliveryAttemptStatus.FAILED,
                null,
                "timeout",
                Instant.parse("2026-01-01T00:00:00Z")
        );
        DeliveryAttemptEntity otherDeliveryAttempt = new DeliveryAttemptEntity(
                UUID.randomUUID(),
                deliveryB,
                1,
                DeliveryAttemptStatus.SUCCEEDED,
                200,
                null,
                Instant.parse("2026-01-01T00:00:10Z")
        );
        deliveryAttemptRepository.saveAllAndFlush(List.of(
                secondAttempt,
                firstAttempt,
                otherDeliveryAttempt));

        List<DeliveryAttemptEntity> attempts = deliveryAttemptRepository
                .findByDeliveryIdOrderByAttemptNumberAsc(deliveryA);

        assertThat(attempts)
                .extracting(DeliveryAttemptEntity::getId)
                .containsExactly(firstAttempt.getId(), secondAttempt.getId());
    }
}
