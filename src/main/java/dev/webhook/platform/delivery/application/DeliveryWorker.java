package dev.webhook.platform.delivery.application;

import dev.webhook.platform.delivery.claim.DeliveryClaimer;
import dev.webhook.platform.delivery.domain.DeliveryAttemptStatus;
import dev.webhook.platform.delivery.domain.FailureResult;
import dev.webhook.platform.delivery.domain.RetryDecision;
import dev.webhook.platform.delivery.domain.RetryPolicy;
import dev.webhook.platform.delivery.persistence.DeliveryAttemptEntity;
import dev.webhook.platform.delivery.persistence.DeliveryAttemptRepository;
import dev.webhook.platform.delivery.persistence.DeliveryEntity;
import dev.webhook.platform.delivery.persistence.DeliveryRepository;
import dev.webhook.platform.delivery.sender.SendResult;
import dev.webhook.platform.delivery.sender.WebhookSender;
import dev.webhook.platform.endpoint.persistence.EndpointRepository;
import dev.webhook.platform.event.persistence.EventRepository;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
public class DeliveryWorker {
    private static final Duration DEFAULT_LEASE_DURATION = Duration.ofSeconds(10);
    private static final Logger log = LoggerFactory.getLogger(DeliveryWorker.class);
    private final String workerId;
    private final DeliveryRepository deliveryRepository;
    private final DeliveryAttemptRepository deliveryAttemptRepository;
    private final EventRepository eventRepository;
    private final EndpointRepository endpointRepository;
    private final WebhookSender webhookSender;

    private final RetryPolicy retryPolicy;
    private final DeliveryClaimer deliveryClaimer;

    public DeliveryWorker(
            String workerId,
            DeliveryRepository deliveryRepository,
            DeliveryAttemptRepository deliveryAttemptRepository,
            EventRepository eventRepository,
            EndpointRepository endpointRepository,
            WebhookSender webhookSender,
            RetryPolicy retryPolicy,
            DeliveryClaimer deliveryClaimer) {
        this.workerId = Objects.requireNonNull(workerId);
        this.deliveryRepository = Objects.requireNonNull(deliveryRepository);
        this.deliveryAttemptRepository = Objects.requireNonNull(deliveryAttemptRepository);
        this.eventRepository = Objects.requireNonNull(eventRepository);
        this.endpointRepository = Objects.requireNonNull(endpointRepository);
        this.webhookSender = Objects.requireNonNull(webhookSender);
        this.retryPolicy = Objects.requireNonNull(retryPolicy);
        this.deliveryClaimer = Objects.requireNonNull(deliveryClaimer);
    }

    public ProcessResult processOne() {
        Optional<DeliveryEntity> claimedDelivery = deliveryClaimer.claimNextDueDelivery(
                workerId,
                Instant.now(),
                DEFAULT_LEASE_DURATION);
        if (claimedDelivery.isEmpty()) {
            return ProcessResult.noPendingDelivery();
        }

        DeliveryEntity delivery = claimedDelivery.get();
        String payload = payloadFor(delivery);
        String url = endpointUrlFor(delivery);
        DeliveryAttemptEntity attempt = newStartedAttempt(delivery);

        log.info(
                "worker claimed delivery workerId={} deliveryId={} eventId={} endpointId={} attempt={}",
                workerId,
                delivery.getId(),
                delivery.getEventId(),
                delivery.getEndpointId(),
                attempt.getAttemptNumber());

        delivery.recordAttemptStarted(Instant.now());

        log.info(
                "worker sending delivery workerId={} deliveryId={} endpointId={} attempt={} url={}",
                workerId,
                delivery.getId(),
                delivery.getEndpointId(),
                attempt.getAttemptNumber(),
                url);

        SendResult result = webhookSender.send(url, payload);

        if (result.succeeded()) {
            return finishSuccess(delivery, attempt, result);
        }

        return finishFailure(delivery, attempt, result);
    }

    private String payloadFor(DeliveryEntity delivery) {
        UUID eventId = delivery.getEventId();
        return eventRepository.findById(eventId)
                .orElseThrow(() -> missingEvent(delivery, eventId))
                .getPayload();
    }

    private String endpointUrlFor(DeliveryEntity delivery) {
        UUID endpointId = delivery.getEndpointId();
        return endpointRepository.findById(endpointId)
                .orElseThrow(() -> missingEndpoint(delivery, endpointId))
                .getUrl();
    }

    private IllegalStateException missingEvent(DeliveryEntity delivery, UUID eventId) {
        log.error(
                "worker missing event row workerId={} deliveryId={} eventId={}",
                workerId,
                delivery.getId(),
                eventId);
        return new IllegalStateException(
                "Delivery " + delivery.getId() + " references missing event " + eventId);
    }

    private IllegalStateException missingEndpoint(DeliveryEntity delivery, UUID endpointId) {
        log.error(
                "worker missing endpoint row workerId={} deliveryId={} endpointId={}",
                workerId,
                delivery.getId(),
                endpointId);
        return new IllegalStateException(
                "Delivery " + delivery.getId() + " references missing endpoint " + endpointId);
    }

    private DeliveryAttemptEntity newStartedAttempt(DeliveryEntity delivery) {
        return new DeliveryAttemptEntity(
                UUID.randomUUID(),
                delivery.getId(),
                delivery.nextAttemptNumber(),
                DeliveryAttemptStatus.STARTED,
                null,
                null,
                Instant.now()
        );
    }

    private ProcessResult finishSuccess(
            DeliveryEntity delivery,
            DeliveryAttemptEntity attempt,
            SendResult result) {
        attempt.markedSucceeded(result.httpStatus());
        delivery.markSucceeded();
        deliveryAttemptRepository.save(attempt);

        log.info(
                "worker delivery succeeded workerId={} deliveryId={} attempt={} httpStatus={}",
                workerId,
                delivery.getId(),
                attempt.getAttemptNumber(),
                result.httpStatus());

        deliveryRepository.save(delivery);
        return ProcessResult.succeeded(delivery.getId());
    }

    private ProcessResult finishFailure(
            DeliveryEntity delivery,
            DeliveryAttemptEntity attempt,
            SendResult result) {
        attempt.markedFailed(result.httpStatus(), result.errorMessage());
        RetryDecision decision = retryPolicy.decideAfterFailure(
                attempt.getAttemptNumber(),
                new FailureResult(result.httpStatus(), result.errorMessage()),
                Instant.now()
        );



        if (decision.shouldRetry()) {
            log.warn(
                    "worker delivery failed retry scheduled workerId={} deliveryId={} attempt={} httpStatus={} error={} nextAttemptAt={}",
                    workerId,
                    delivery.getId(),
                    attempt.getAttemptNumber(),
                    result.httpStatus(),
                    result.errorMessage(),
                    decision.nextAttemptAt());
            delivery.scheduleRetry(decision.nextAttemptAt(), result.errorMessage(), Instant.now());
        } else {
            log.warn(
                    "worker delivery failed permanently workerId={} deliveryId={} attempt={} httpStatus={} error={}",
                    workerId,
                    delivery.getId(),
                    attempt.getAttemptNumber(),
                    result.httpStatus(),
                    result.errorMessage());
            delivery.markFailed(result.errorMessage(), Instant.now());
        }

        deliveryRepository.save(delivery);
        deliveryAttemptRepository.save(attempt);
        return ProcessResult.failed(delivery.getId());
    }
}
