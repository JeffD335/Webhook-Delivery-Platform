package dev.webhook.platform.delivery.application;

import dev.webhook.platform.delivery.claim.DeliveryClaimer;
import dev.webhook.platform.delivery.domain.DeliveryStatus;
import dev.webhook.platform.delivery.domain.RetryPolicy;
import dev.webhook.platform.delivery.observability.DeliveryMetrics;
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
import io.micrometer.core.instrument.MockClock;
import io.micrometer.core.instrument.simple.SimpleConfig;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Lab03 unit tests for DeliveryWorker.
 *
 * Keep the class disabled until you implement DeliveryWorker.processOne(). Then enable one test at
 * a time and make the smallest production change that satisfies that behavior.
 */

class DeliveryWorkerTest {



    @Test
    void processOne_whenNoPendingDelivery_doesNotCallSender() {
        DeliveryRepository deliveryRepository = mock(DeliveryRepository.class);
        DeliveryAttemptRepository deliveryAttemptRepository = mock(DeliveryAttemptRepository.class);
        EventRepository eventRepository = mock(EventRepository.class);
        EndpointRepository endpointRepository = mock(EndpointRepository.class);
        WebhookSender webhookSender = mock(WebhookSender.class);
        DeliveryClaimer deliveryClaimer = mock(DeliveryClaimer.class);
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();

        when(deliveryClaimer.claimNextDueDelivery(anyString(), any(Instant.class), any(Duration.class)))
                .thenReturn(Optional.empty());
        RetryPolicy retryPolicy = new RetryPolicy();
        DeliveryWorker worker = new DeliveryWorker(
                "worker-test",
                deliveryRepository,
                deliveryAttemptRepository,
                eventRepository,
                endpointRepository,
                webhookSender,
                retryPolicy,
                deliveryClaimer,
                new DeliveryMetrics(meterRegistry));

        ProcessResult result = worker.processOne();

        assertThat(result.outcome()).isEqualTo(ProcessOutcome.NO_PENDING_DELIVERY);
        assertThat(result.deliveryId()).isNull();

        verify(deliveryClaimer).claimNextDueDelivery(anyString(), any(Instant.class), any(Duration.class));
        verify(deliveryRepository, never()).save(any());
        verifyNoInteractions(deliveryAttemptRepository, eventRepository, endpointRepository, webhookSender);
        assertNoSendOrTransitionMetrics(meterRegistry);
        assertThat(workerErrorCount(meterRegistry)).isZero();
    }

    @ExtendWith(OutputCaptureExtension.class)
    @Test
    void processOne_whenSenderSucceeds_updatesDeliveryToSucceeded(CapturedOutput output) {
        UUID deliveryId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        UUID endpointId = UUID.randomUUID();
        Instant createdAt = Instant.now();
        String payload = "{\"orderId\":\"sensitive-payload-value\"}";
        String endpointUrl = "https://receiver.example/webhooks/orders?token=secret-test-value";

        DeliveryEntity delivery = new DeliveryEntity(
                deliveryId,
                eventId,
                endpointId,
                DeliveryStatus.PENDING,
                createdAt);
        EventEntity event = new EventEntity(
                eventId,
                "order.created",
                payload,
                createdAt);
        EndpointEntity endpoint = new EndpointEntity(
                endpointId,
                "Orders receiver",
                endpointUrl,
                createdAt);

        DeliveryRepository deliveryRepository = mock(DeliveryRepository.class);
        DeliveryAttemptRepository deliveryAttemptRepository = mock(DeliveryAttemptRepository.class);
        EventRepository eventRepository = mock(EventRepository.class);
        EndpointRepository endpointRepository = mock(EndpointRepository.class);
        WebhookSender webhookSender = mock(WebhookSender.class);
        DeliveryClaimer deliveryClaimer = mock(DeliveryClaimer.class);

        when(deliveryClaimer.claimNextDueDelivery(anyString(), any(Instant.class), any(Duration.class)))
                .thenReturn(Optional.of(delivery));
        when(eventRepository.findById(eventId)).thenReturn(Optional.of(event));
        when(endpointRepository.findById(endpointId)).thenReturn(Optional.of(endpoint));
        MockClock clock = new MockClock();
        when(webhookSender.send(endpointUrl, payload)).thenAnswer(invocation -> {
            clock.add(Duration.ofMillis(75));
            return SendResult.success();
        });

        RetryPolicy retryPolicy = new RetryPolicy();
        DeliveryWorker worker = new DeliveryWorker(
                "worker-test",
                deliveryRepository,
                deliveryAttemptRepository,
                eventRepository,
                endpointRepository,
                webhookSender,
                retryPolicy,
                deliveryClaimer,
                new DeliveryMetrics(new SimpleMeterRegistry(SimpleConfig.DEFAULT, clock)));

        ProcessResult result = worker.processOne();

        assertThat(result.outcome()).isEqualTo(ProcessOutcome.SUCCEEDED);
        assertThat(result.deliveryId()).isEqualTo(deliveryId);
        assertThat(delivery.getStatus()).isEqualTo(DeliveryStatus.SUCCEEDED);
        assertThat(delivery.getAttemptCount()).isEqualTo(1);

        verify(webhookSender).send(endpointUrl, payload);
        verify(deliveryAttemptRepository).save(any(DeliveryAttemptEntity.class));
        verify(deliveryRepository).save(delivery);
        assertThat(output.getAll())
                .contains("webhook send completed workerId=worker-test")
                .contains("deliveryId=" + deliveryId)
                .contains("eventId=" + eventId)
                .contains("endpointId=" + endpointId)
                .contains("attempt=1 result=success httpStatus=200 durationMs=75")
                .doesNotContain(endpointUrl, "secret-test-value", payload);
    }

    @ExtendWith(OutputCaptureExtension.class)
    @Test
    void processOne_whenSenderFails_schedulesRetry(CapturedOutput output) {
        UUID deliveryId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        UUID endpointId = UUID.randomUUID();
        Instant createdAt = Instant.now();
        String payload = "{\"orderId\":\"ord_456\"}";
        String endpointUrl = "https://receiver.example/webhooks/orders?token=secret-test-value";

        DeliveryEntity delivery = new DeliveryEntity(
                deliveryId,
                eventId,
                endpointId,
                DeliveryStatus.PENDING,
                createdAt);
        EventEntity event = new EventEntity(
                eventId,
                "order.created",
                payload,
                createdAt);
        EndpointEntity endpoint = new EndpointEntity(
                endpointId,
                "Orders receiver",
                endpointUrl,
                createdAt);

        DeliveryRepository deliveryRepository = mock(DeliveryRepository.class);
        DeliveryAttemptRepository deliveryAttemptRepository = mock(DeliveryAttemptRepository.class);
        EventRepository eventRepository = mock(EventRepository.class);
        EndpointRepository endpointRepository = mock(EndpointRepository.class);
        WebhookSender webhookSender = mock(WebhookSender.class);
        DeliveryClaimer deliveryClaimer = mock(DeliveryClaimer.class);

        when(deliveryClaimer.claimNextDueDelivery(anyString(), any(Instant.class), any(Duration.class)))
                .thenReturn(Optional.of(delivery));
        when(eventRepository.findById(eventId)).thenReturn(Optional.of(event));
        when(endpointRepository.findById(endpointId)).thenReturn(Optional.of(endpoint));
        when(webhookSender.send(endpointUrl, payload)).thenReturn(
                SendResult.failure("connection error at " + endpointUrl));

        RetryPolicy retryPolicy = new RetryPolicy();
        DeliveryWorker worker = new DeliveryWorker(
                "worker-test",
                deliveryRepository,
                deliveryAttemptRepository,
                eventRepository,
                endpointRepository,
                webhookSender,
                retryPolicy,
                deliveryClaimer,
                new DeliveryMetrics(new SimpleMeterRegistry()));

        ProcessResult result = worker.processOne();

        assertThat(result.outcome()).isEqualTo(ProcessOutcome.FAILED);
        assertThat(result.deliveryId()).isEqualTo(deliveryId);
        assertThat(delivery.getStatus()).isEqualTo(DeliveryStatus.RETRY_SCHEDULED);
        assertThat(delivery.getAttemptCount()).isEqualTo(1);
        assertThat(delivery.getNextAttemptAt()).isNotNull();
        assertThat(delivery.getLastError()).isEqualTo("connection error at " + endpointUrl);

        verify(webhookSender).send(endpointUrl, payload);
        verify(deliveryAttemptRepository).save(any(DeliveryAttemptEntity.class));
        verify(deliveryRepository).save(delivery);
        assertThat(output.getAll())
                .contains("webhook send completed workerId=worker-test")
                .contains("result=http_error httpStatus=500 durationMs=")
                .doesNotContain(endpointUrl, "secret-test-value", payload);
    }

    @Test
    void processOne_loadsEventPayloadAndEndpointUrlBeforeSending() {
        UUID deliveryId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        UUID endpointId = UUID.randomUUID();
        Instant createdAt = Instant.now();
        String payload = "{\"customerId\":\"cus_789\"}";
        String endpointUrl = "https://receiver.example/webhooks/customers";

        DeliveryEntity delivery = new DeliveryEntity(
                deliveryId,
                eventId,
                endpointId,
                DeliveryStatus.PENDING,
                createdAt);
        EventEntity event = new EventEntity(
                eventId,
                "customer.created",
                payload,
                createdAt);
        EndpointEntity endpoint = new EndpointEntity(
                endpointId,
                "Customers receiver",
                endpointUrl,
                createdAt);

        DeliveryRepository deliveryRepository = mock(DeliveryRepository.class);
        DeliveryAttemptRepository deliveryAttemptRepository = mock(DeliveryAttemptRepository.class);
        EventRepository eventRepository = mock(EventRepository.class);
        EndpointRepository endpointRepository = mock(EndpointRepository.class);
        WebhookSender webhookSender = mock(WebhookSender.class);
        DeliveryClaimer deliveryClaimer = mock(DeliveryClaimer.class);

        when(deliveryClaimer.claimNextDueDelivery(anyString(), any(Instant.class), any(Duration.class)))
                .thenReturn(Optional.of(delivery));
        when(eventRepository.findById(eventId)).thenReturn(Optional.of(event));
        when(endpointRepository.findById(endpointId)).thenReturn(Optional.of(endpoint));
        when(webhookSender.send(endpointUrl, payload)).thenReturn(SendResult.success());

        RetryPolicy retryPolicy = new RetryPolicy();
        DeliveryWorker worker = new DeliveryWorker(
                "worker-test",
                deliveryRepository,
                deliveryAttemptRepository,
                eventRepository,
                endpointRepository,
                webhookSender,
                retryPolicy,
                deliveryClaimer,
                new DeliveryMetrics(new SimpleMeterRegistry()));

        ProcessResult result = worker.processOne();

        assertThat(result.outcome()).isEqualTo(ProcessOutcome.SUCCEEDED);
        verify(eventRepository).findById(eventId);
        verify(endpointRepository).findById(endpointId);
        verify(webhookSender).send(endpointUrl, payload);
    }

    @Test
    void processOne_whenClaimThrows_recordsWorkerErrorAndRethrowsSameException() {
        DeliveryRepository deliveryRepository = mock(DeliveryRepository.class);
        DeliveryAttemptRepository deliveryAttemptRepository = mock(DeliveryAttemptRepository.class);
        EventRepository eventRepository = mock(EventRepository.class);
        EndpointRepository endpointRepository = mock(EndpointRepository.class);
        WebhookSender webhookSender = mock(WebhookSender.class);
        DeliveryClaimer deliveryClaimer = mock(DeliveryClaimer.class);
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        IllegalStateException failure = new IllegalStateException("claim failed");

        when(deliveryClaimer.claimNextDueDelivery(anyString(), any(Instant.class), any(Duration.class)))
                .thenThrow(failure);

        DeliveryWorker worker = new DeliveryWorker(
                "worker-test",
                deliveryRepository,
                deliveryAttemptRepository,
                eventRepository,
                endpointRepository,
                webhookSender,
                new RetryPolicy(),
                deliveryClaimer,
                new DeliveryMetrics(meterRegistry));

        assertThatThrownBy(worker::processOne).isSameAs(failure);

        assertThat(workerErrorCount(meterRegistry)).isEqualTo(1);
        assertNoSendOrTransitionMetrics(meterRegistry);
        verifyNoInteractions(
                deliveryRepository,
                deliveryAttemptRepository,
                eventRepository,
                endpointRepository,
                webhookSender);
    }

    @Test
    void processOne_whenSenderThrows_recordsSendExceptionAndWorkerErrorAndRethrowsSameException() {
        UUID deliveryId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        UUID endpointId = UUID.randomUUID();
        Instant createdAt = Instant.now();
        String payload = "{\"orderId\":\"ord_sender_exception\"}";
        String endpointUrl = "https://receiver.example/webhooks/orders";

        DeliveryEntity delivery = new DeliveryEntity(
                deliveryId,
                eventId,
                endpointId,
                DeliveryStatus.PENDING,
                createdAt);
        EventEntity event = new EventEntity(eventId, "order.created", payload, createdAt);
        EndpointEntity endpoint = new EndpointEntity(
                endpointId,
                "Orders receiver",
                endpointUrl,
                createdAt);
        DeliveryRepository deliveryRepository = mock(DeliveryRepository.class);
        DeliveryAttemptRepository deliveryAttemptRepository = mock(DeliveryAttemptRepository.class);
        EventRepository eventRepository = mock(EventRepository.class);
        EndpointRepository endpointRepository = mock(EndpointRepository.class);
        WebhookSender webhookSender = mock(WebhookSender.class);
        DeliveryClaimer deliveryClaimer = mock(DeliveryClaimer.class);
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        IllegalStateException failure = new IllegalStateException("sender failed");

        when(deliveryClaimer.claimNextDueDelivery(anyString(), any(Instant.class), any(Duration.class)))
                .thenReturn(Optional.of(delivery));
        when(eventRepository.findById(eventId)).thenReturn(Optional.of(event));
        when(endpointRepository.findById(endpointId)).thenReturn(Optional.of(endpoint));
        when(webhookSender.send(endpointUrl, payload)).thenThrow(failure);

        DeliveryWorker worker = new DeliveryWorker(
                "worker-test",
                deliveryRepository,
                deliveryAttemptRepository,
                eventRepository,
                endpointRepository,
                webhookSender,
                new RetryPolicy(),
                deliveryClaimer,
                new DeliveryMetrics(meterRegistry));

        assertThatThrownBy(worker::processOne).isSameAs(failure);

        assertThat(meterRegistry.get("webhook.delivery.send")
                .tag("result", "exception")
                .timer()
                .count()).isEqualTo(1);
        assertThat(workerErrorCount(meterRegistry)).isEqualTo(1);
        verify(deliveryRepository, never()).save(any());
        verifyNoInteractions(deliveryAttemptRepository);
    }

    @Test
    void processOne_whenEventIsMissing_recordsWorkerErrorWithoutSendOrTransition() {
        UUID deliveryId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        UUID endpointId = UUID.randomUUID();
        DeliveryEntity delivery = new DeliveryEntity(
                deliveryId,
                eventId,
                endpointId,
                DeliveryStatus.PENDING,
                Instant.now());
        DeliveryRepository deliveryRepository = mock(DeliveryRepository.class);
        DeliveryAttemptRepository deliveryAttemptRepository = mock(DeliveryAttemptRepository.class);
        EventRepository eventRepository = mock(EventRepository.class);
        EndpointRepository endpointRepository = mock(EndpointRepository.class);
        WebhookSender webhookSender = mock(WebhookSender.class);
        DeliveryClaimer deliveryClaimer = mock(DeliveryClaimer.class);
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();

        when(deliveryClaimer.claimNextDueDelivery(anyString(), any(Instant.class), any(Duration.class)))
                .thenReturn(Optional.of(delivery));
        when(eventRepository.findById(eventId)).thenReturn(Optional.empty());

        DeliveryWorker worker = new DeliveryWorker(
                "worker-test",
                deliveryRepository,
                deliveryAttemptRepository,
                eventRepository,
                endpointRepository,
                webhookSender,
                new RetryPolicy(),
                deliveryClaimer,
                new DeliveryMetrics(meterRegistry));

        assertThatThrownBy(worker::processOne)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(eventId.toString());

        assertThat(workerErrorCount(meterRegistry)).isEqualTo(1);
        assertNoSendOrTransitionMetrics(meterRegistry);
        verifyNoInteractions(
                deliveryRepository,
                deliveryAttemptRepository,
                endpointRepository,
                webhookSender);
    }

    private void assertNoSendOrTransitionMetrics(SimpleMeterRegistry meterRegistry) {
        for (String result : List.of("success", "http_error", "no_response", "exception")) {
            assertThat(meterRegistry.get("webhook.delivery.send")
                    .tag("result", result)
                    .timer()
                    .count()).isZero();
        }
        for (String outcome : List.of("succeeded", "retry_scheduled", "failed")) {
            assertThat(meterRegistry.get("webhook.delivery.transitions")
                    .tag("outcome", outcome)
                    .counter()
                    .count()).isZero();
        }
    }

    private double workerErrorCount(SimpleMeterRegistry meterRegistry) {
        return meterRegistry.get("webhook.delivery.worker.errors")
                .counter()
                .count();
    }
}
