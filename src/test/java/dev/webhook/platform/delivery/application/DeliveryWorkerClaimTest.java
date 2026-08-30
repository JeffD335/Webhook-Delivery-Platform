package dev.webhook.platform.delivery.application;

import dev.webhook.platform.delivery.claim.DeliveryClaimer;
import dev.webhook.platform.delivery.domain.DeliveryStatus;
import dev.webhook.platform.delivery.domain.RetryPolicy;
import dev.webhook.platform.delivery.persistence.DeliveryAttemptEntity;
import dev.webhook.platform.delivery.persistence.DeliveryAttemptRepository;
import dev.webhook.platform.delivery.persistence.DeliveryEntity;
import dev.webhook.platform.delivery.persistence.DeliveryRepository;
import dev.webhook.platform.delivery.sender.SendResult;
import dev.webhook.platform.delivery.sender.WebhookSender;
import dev.webhook.platform.endpoint.persistence.EndpointEntity;
import dev.webhook.platform.endpoint.persistence.EndpointRepository;
import dev.webhook.platform.event.persistence.EventEntity;
import dev.webhook.platform.event.persistence.EventRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DeliveryWorkerClaimTest {

    private DeliveryRepository deliveryRepository;
    private DeliveryAttemptRepository deliveryAttemptRepository;
    private EventRepository eventRepository;
    private EndpointRepository endpointRepository;
    private WebhookSender webhookSender;

    @BeforeEach
    void setUp() {
        deliveryRepository = mock(DeliveryRepository.class);
        deliveryAttemptRepository = mock(DeliveryAttemptRepository.class);
        eventRepository = mock(EventRepository.class);
        endpointRepository = mock(EndpointRepository.class);
        webhookSender = mock(WebhookSender.class);
    }

    @Test
    void processOne_clearsClaimAfterSuccess() {
        UUID deliveryId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        UUID endpointId = UUID.randomUUID();
        Instant createdAt = Instant.parse("2026-01-01T00:00:00Z");
        String payload = "{\"orderId\":\"ord_claim_success\"}";
        String endpointUrl = "https://receiver.example/webhook";

        DeliveryEntity delivery = new DeliveryEntity(
                deliveryId,
                eventId,
                endpointId,
                DeliveryStatus.PENDING,
                createdAt);
        delivery.claim(
                "worker-1",
                Instant.parse("2026-01-01T00:05:00Z"),
                Instant.parse("2026-01-01T00:01:00Z"));

        EventEntity event = new EventEntity(eventId, "order.created", payload, createdAt);
        EndpointEntity endpoint = new EndpointEntity(endpointId, "Receiver", endpointUrl, createdAt);
        DeliveryClaimer deliveryClaimer = mock(DeliveryClaimer.class);

        when(deliveryClaimer.claimNextDueDelivery(anyString(), any(Instant.class), any(Duration.class)))
                .thenReturn(Optional.of(delivery));
        when(eventRepository.findById(eventId)).thenReturn(Optional.of(event));
        when(endpointRepository.findById(endpointId)).thenReturn(Optional.of(endpoint));
        when(webhookSender.send(endpointUrl, payload)).thenReturn(SendResult.success(204));

        DeliveryWorker worker = new DeliveryWorker(
                "worker-1",
                deliveryRepository,
                deliveryAttemptRepository,
                eventRepository,
                endpointRepository,
                webhookSender,
                new RetryPolicy(),
                deliveryClaimer);

        ProcessResult result = worker.processOne();

        assertThat(result.outcome()).isEqualTo(ProcessOutcome.SUCCEEDED);
        assertThat(result.deliveryId()).isEqualTo(deliveryId);
        assertThat(delivery.getStatus()).isEqualTo(DeliveryStatus.SUCCEEDED);
        assertThat(delivery.getClaimedBy()).isNull();
        assertThat(delivery.getClaimExpiresAt()).isNull();

        verify(webhookSender).send(endpointUrl, payload);
        verify(deliveryAttemptRepository).save(any(DeliveryAttemptEntity.class));
        verify(deliveryRepository).save(delivery);
    }
}
