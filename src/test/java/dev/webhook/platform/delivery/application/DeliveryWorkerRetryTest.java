package dev.webhook.platform.delivery.application;

import dev.webhook.platform.delivery.claim.DeliveryClaimer;
import dev.webhook.platform.delivery.domain.DeliveryAttemptStatus;
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
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DeliveryWorkerRetryTest {

    private DeliveryRepository deliveryRepository;
    private DeliveryAttemptRepository deliveryAttemptRepository;
    private EventRepository eventRepository;
    private EndpointRepository endpointRepository;
    private WebhookSender webhookSender;
    private DeliveryClaimer deliveryClaimer;
    private SimpleMeterRegistry meterRegistry;

    @BeforeEach
    void setUp() {
        deliveryRepository = mock(DeliveryRepository.class);
        deliveryAttemptRepository = mock(DeliveryAttemptRepository.class);
        eventRepository = mock(EventRepository.class);
        endpointRepository = mock(EndpointRepository.class);
        webhookSender = mock(WebhookSender.class);
        deliveryClaimer = mock(DeliveryClaimer.class);
        meterRegistry = new SimpleMeterRegistry();
    }

    @Test
    void success_createsSucceededAttemptAndMarksDeliverySucceeded() {
        TestFixture fixture = fixture(0, DeliveryStatus.PENDING);
        when(webhookSender.send(fixture.endpointUrl(), fixture.payload()))
                .thenReturn(SendResult.success(204));

        ProcessResult result = worker().processOne();

        DeliveryAttemptEntity attempt = savedAttempt();
        assertThat(result.outcome()).isEqualTo(ProcessOutcome.SUCCEEDED);
        assertThat(attempt.getDeliveryId()).isEqualTo(fixture.deliveryId());
        assertThat(attempt.getAttemptNumber()).isEqualTo(1);
        assertThat(attempt.getStatus()).isEqualTo(DeliveryAttemptStatus.SUCCEEDED);
        assertThat(attempt.getHttpStatus()).isEqualTo(204);
        assertThat(attempt.getErrorMessage()).isNull();
        assertThat(attempt.getStartedAt()).isNotNull();
        assertThat(attempt.getFinishedAt()).isNotNull();
        assertThat(fixture.delivery().getStatus()).isEqualTo(DeliveryStatus.SUCCEEDED);
        assertThat(fixture.delivery().getAttemptCount()).isEqualTo(1);
        assertThat(fixture.delivery().getNextAttemptAt()).isNull();
        assertThat(fixture.delivery().getLastError()).isNull();
        assertThat(transitionCount("succeeded")).isEqualTo(1);
        assertThat(transitionCount("retry_scheduled")).isZero();
        assertThat(transitionCount("failed")).isZero();
    }

    @Test
    void firstFailure_createsFailedAttemptAndSchedulesRetry() {
        TestFixture fixture = fixture(0, DeliveryStatus.PENDING);
        when(webhookSender.send(fixture.endpointUrl(), fixture.payload()))
                .thenReturn(SendResult.timeOut("read timed out"));

        ProcessResult result = worker().processOne();

        DeliveryAttemptEntity attempt = savedAttempt();
        assertThat(result.outcome()).isEqualTo(ProcessOutcome.FAILED);
        assertThat(attempt.getDeliveryId()).isEqualTo(fixture.deliveryId());
        assertThat(attempt.getAttemptNumber()).isEqualTo(1);
        assertThat(attempt.getStatus()).isEqualTo(DeliveryAttemptStatus.FAILED);
        assertThat(attempt.getHttpStatus()).isNull();
        assertThat(attempt.getErrorMessage()).isEqualTo("read timed out");
        assertThat(attempt.getFinishedAt()).isNotNull();
        assertThat(fixture.delivery().getStatus()).isEqualTo(DeliveryStatus.RETRY_SCHEDULED);
        assertThat(fixture.delivery().getAttemptCount()).isEqualTo(1);
        assertThat(fixture.delivery().getNextAttemptAt()).isNotNull();
        assertThat(fixture.delivery().getLastError()).isEqualTo("read timed out");
        assertThat(transitionCount("succeeded")).isZero();
        assertThat(transitionCount("retry_scheduled")).isEqualTo(1);
        assertThat(transitionCount("failed")).isZero();
        assertThat(workerErrorCount()).isZero();
        assertThat(sendCount("no_response")).isEqualTo(1);
        assertThat(sendCount("http_error")).isZero();
    }

    @Test
    void thirdFailure_marksDeliveryFailedWithoutNextAttempt() {
        TestFixture fixture = fixture(2, DeliveryStatus.RETRY_SCHEDULED);
        when(webhookSender.send(fixture.endpointUrl(), fixture.payload()))
                .thenReturn(SendResult.httpFailure(503, "service unavailable"));

        ProcessResult result = worker().processOne();

        DeliveryAttemptEntity attempt = savedAttempt();
        assertThat(result.outcome()).isEqualTo(ProcessOutcome.FAILED);
        assertThat(attempt.getDeliveryId()).isEqualTo(fixture.deliveryId());
        assertThat(attempt.getAttemptNumber()).isEqualTo(3);
        assertThat(attempt.getStatus()).isEqualTo(DeliveryAttemptStatus.FAILED);
        assertThat(attempt.getHttpStatus()).isEqualTo(503);
        assertThat(attempt.getErrorMessage()).isEqualTo("service unavailable");
        assertThat(fixture.delivery().getStatus()).isEqualTo(DeliveryStatus.FAILED);
        assertThat(fixture.delivery().getAttemptCount()).isEqualTo(3);
        assertThat(fixture.delivery().getNextAttemptAt()).isNull();
        assertThat(fixture.delivery().getLastError()).isEqualTo("service unavailable");
        assertThat(transitionCount("succeeded")).isZero();
        assertThat(transitionCount("retry_scheduled")).isZero();
        assertThat(transitionCount("failed")).isEqualTo(1);
        assertThat(sendCount("http_error")).isEqualTo(1);
        assertThat(workerErrorCount()).isZero();
    }
    @Test
    void permanentHttpFailure_marksDeliveryFailedAfterOneAttempt() {
        TestFixture fixture = fixture(0, DeliveryStatus.PENDING);
        when(webhookSender.send(fixture.endpointUrl(), fixture.payload))
                .thenReturn(SendResult.httpFailure(400, "bad request"));

        ProcessResult result = worker().processOne();
        DeliveryAttemptEntity attempt = savedAttempt();

        assertThat(result.outcome()).isEqualTo(ProcessOutcome.FAILED);
        assertThat(attempt.getDeliveryId()).isEqualTo(fixture.deliveryId());
        assertThat(attempt.getAttemptNumber()).isEqualTo(1);
        assertThat(attempt.getStatus()).isEqualTo(DeliveryAttemptStatus.FAILED);
        assertThat(attempt.getHttpStatus()).isEqualTo(400);
        assertThat(attempt.getErrorMessage()).isEqualTo("bad request");
        assertThat(attempt.getFinishedAt()).isNotNull();
        assertThat(fixture.delivery().getStatus()).isEqualTo(DeliveryStatus.FAILED);
        assertThat(fixture.delivery().getAttemptCount()).isEqualTo(1);
        assertThat(fixture.delivery().getNextAttemptAt()).isNull();
        assertThat(fixture.delivery().getLastError()).isEqualTo("bad request");
        assertThat(transitionCount("succeeded")).isZero();
        assertThat(transitionCount("retry_scheduled")).isZero();
        assertThat(transitionCount("failed")).isEqualTo(1);
        assertThat(sendCount("http_error")).isEqualTo(1);
        assertThat(workerErrorCount()).isZero();
    }

    @Test
    void consecutiveRetryableFailures_recordEverySendAndOnlyFinalFailure() {
        TestFixture fixture = fixture(0, DeliveryStatus.PENDING);
        when(webhookSender.send(fixture.endpointUrl(), fixture.payload()))
                .thenReturn(SendResult.httpFailure(503, "service unavailable"));
        DeliveryWorker worker = worker();

        worker.processOne();
        worker.processOne();
        worker.processOne();

        assertThat(fixture.delivery().getStatus()).isEqualTo(DeliveryStatus.FAILED);
        assertThat(fixture.delivery().getAttemptCount()).isEqualTo(3);
        assertThat(sendCount("http_error")).isEqualTo(3);
        assertThat(transitionCount("succeeded")).isZero();
        assertThat(transitionCount("retry_scheduled")).isEqualTo(2);
        assertThat(transitionCount("failed")).isEqualTo(1);
        assertThat(workerErrorCount()).isZero();
    }

    @Test
    void success_whenSecondSaveFails_doesNotRecordTransitionAndRecordsWorkerError() {
        TestFixture fixture = fixture(0, DeliveryStatus.PENDING);
        when(webhookSender.send(fixture.endpointUrl(), fixture.payload()))
                .thenReturn(SendResult.success(204));
        IllegalStateException failure = new IllegalStateException("delivery save failed");
        when(deliveryRepository.save(fixture.delivery())).thenThrow(failure);

        assertThatThrownBy(() -> worker().processOne()).isSameAs(failure);

        assertThat(transitionCount("succeeded")).isZero();
        assertThat(transitionCount("retry_scheduled")).isZero();
        assertThat(transitionCount("failed")).isZero();
        assertThat(workerErrorCount()).isEqualTo(1);
        verify(deliveryAttemptRepository).save(any(DeliveryAttemptEntity.class));
    }

    @ExtendWith(OutputCaptureExtension.class)
    @Test
    void retry_whenAttemptSaveThrows_doesNotLogDeliveryCompleted(CapturedOutput output) {
        TestFixture fixture = fixture(0, DeliveryStatus.PENDING);
        when(webhookSender.send(fixture.endpointUrl(), fixture.payload()))
                .thenReturn(SendResult.timeOut("read timed out"));
        IllegalStateException failure = new IllegalStateException("attempt save failed");
        when(deliveryAttemptRepository.save(any(DeliveryAttemptEntity.class))).thenThrow(failure);

        assertThatThrownBy(() -> worker().processOne()).isSameAs(failure);

        assertThat(sendCount("no_response")).isEqualTo(1);
        assertThat(transitionCount("succeeded")).isZero();
        assertThat(transitionCount("retry_scheduled")).isZero();
        assertThat(transitionCount("failed")).isZero();
        assertThat(workerErrorCount()).isEqualTo(1);
        verify(deliveryRepository).save(fixture.delivery());

        //log assertion
        assertThat(output.getAll()).contains("webhook send completed");
        assertThat(output.getAll()).doesNotContain("worker delivery completed");
    }

    private DeliveryWorker worker() {
        return new DeliveryWorker(
                "worker-test",
                deliveryRepository,
                deliveryAttemptRepository,
                eventRepository,
                endpointRepository,
                webhookSender,
                new RetryPolicy(),
                deliveryClaimer,
                new DeliveryMetrics(meterRegistry));
    }

    private TestFixture fixture(int attemptCount, DeliveryStatus status) {
        UUID deliveryId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        UUID endpointId = UUID.randomUUID();
        Instant createdAt = Instant.parse("2026-01-01T00:00:00Z");
        String payload = "{\"orderId\":\"ord_retry\"}";
        String endpointUrl = "https://receiver.example/webhook";
        DeliveryEntity delivery = new DeliveryEntity(
                deliveryId,
                eventId,
                endpointId,
                status,
                createdAt,
                attemptCount,
                status == DeliveryStatus.RETRY_SCHEDULED ? createdAt.minusSeconds(1) : null,
                attemptCount > 0 ? createdAt.minusSeconds(10) : null,
                attemptCount > 0 ? "previous failure" : null,
                createdAt);
        EventEntity event = new EventEntity(eventId, "order.created", payload, createdAt);
        EndpointEntity endpoint = new EndpointEntity(endpointId, "Receiver", endpointUrl, createdAt);

        when(deliveryClaimer.claimNextDueDelivery(anyString(), any(Instant.class), any(Duration.class)))
                .thenReturn(Optional.of(delivery));
        when(eventRepository.findById(eventId)).thenReturn(Optional.of(event));
        when(endpointRepository.findById(endpointId)).thenReturn(Optional.of(endpoint));

        return new TestFixture(deliveryId, delivery, payload, endpointUrl);
    }

    private DeliveryAttemptEntity savedAttempt() {
        ArgumentCaptor<DeliveryAttemptEntity> captor =
                ArgumentCaptor.forClass(DeliveryAttemptEntity.class);
        verify(deliveryAttemptRepository).save(captor.capture());
        return captor.getValue();
    }

    private double transitionCount(String outcome) {
        return meterRegistry.get("webhook.delivery.transitions")
                .tag("outcome", outcome)
                .counter()
                .count();
    }

    private long sendCount(String result) {
        return meterRegistry.get("webhook.delivery.send")
                .tag("result", result)
                .timer()
                .count();
    }

    private double workerErrorCount() {
        return meterRegistry.get("webhook.delivery.worker.errors")
                .counter()
                .count();
    }

    private record TestFixture(
            UUID deliveryId,
            DeliveryEntity delivery,
            String payload,
            String endpointUrl) {
    }
}
