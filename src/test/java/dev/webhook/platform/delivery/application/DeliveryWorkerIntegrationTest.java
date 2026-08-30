package dev.webhook.platform.delivery.application;

import dev.webhook.platform.delivery.claim.DeliveryClaimer;
import dev.webhook.platform.delivery.domain.RetryPolicy;
import dev.webhook.platform.delivery.persistence.DeliveryAttemptRepository;
import dev.webhook.platform.delivery.persistence.DeliveryRepository;
import dev.webhook.platform.delivery.sender.SendResult;
import dev.webhook.platform.delivery.sender.WebhookSender;
import dev.webhook.platform.endpoint.persistence.EndpointRepository;
import dev.webhook.platform.event.persistence.EventRepository;
import dev.webhook.platform.testsupport.ApiIntegrationTestSupport;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Lab03 integration tests for Delivery Worker v0.
 *
 * Keep this class disabled until the worker unit tests pass. Then enable one integration test at a
 * time and wire the real repositories through DeliveryWorker.
 */

class DeliveryWorkerIntegrationTest extends ApiIntegrationTestSupport {

    @Autowired
    private DeliveryRepository deliveryRepository;

    @Autowired
    private DeliveryAttemptRepository deliveryAttemptRepository;

    @Autowired
    private EventRepository eventRepository;

    @Autowired
    private EndpointRepository endpointRepository;

    @BeforeEach
    void clearDatabase() {
        clearWebhookTables();
    }

    @Test
    void processOne_whenNoPendingDelivery_doesNothing() {
        RecordingWebhookSender sender = new RecordingWebhookSender(SendResult.success());
        DeliveryWorker worker = worker(sender);

        ProcessResult result = worker.processOne();

        assertThat(result.outcome()).isEqualTo(ProcessOutcome.NO_PENDING_DELIVERY);
        assertThat(result.deliveryId()).isNull();
        assertThat(countRows("webhook_deliveries")).isZero();
        assertThat(sender.callCount()).isZero();
    }

    @Test
    void processOne_whenSenderSucceeds_marksDeliverySucceeded() {
        String payload = "{\"orderId\":\"ord_100\"}";
        String endpointUrl = "https://receiver.example/webhook";
        UUID eventId = insertEvent("order.created", payload);
        UUID endpointId = insertEndpoint("Receiver", endpointUrl);
        UUID deliveryId = insertDelivery(eventId, endpointId, "PENDING");
        RecordingWebhookSender sender = new RecordingWebhookSender(SendResult.success());
        DeliveryWorker worker = worker(sender);

        ProcessResult result = worker.processOne();

        assertThat(result.outcome()).isEqualTo(ProcessOutcome.SUCCEEDED);
        assertThat(result.deliveryId()).isEqualTo(deliveryId);
        assertThat(deliveryStatus(deliveryId)).isEqualTo("SUCCEEDED");
        assertThat(sender.callCount()).isEqualTo(1);
        assertThat(sender.lastUrl()).isEqualTo(endpointUrl);
        assertThat(sender.lastPayload()).isEqualTo(payload);
        assertNoExtraEventsCreated();
    }

    @Test
    void processOne_whenSenderFails_schedulesRetry() {
        String payload = "{\"orderId\":\"ord_200\"}";
        String endpointUrl = "https://receiver.example/webhook";
        UUID eventId = insertEvent("order.created", payload);
        UUID endpointId = insertEndpoint("Receiver", endpointUrl);
        UUID deliveryId = insertDelivery(eventId, endpointId, "PENDING");
        RecordingWebhookSender sender = new RecordingWebhookSender(SendResult.failure("timeout"));
        DeliveryWorker worker = worker(sender);

        ProcessResult result = worker.processOne();

        assertThat(result.outcome()).isEqualTo(ProcessOutcome.FAILED);
        assertThat(result.deliveryId()).isEqualTo(deliveryId);
        assertThat(deliveryStatus(deliveryId)).isEqualTo("RETRY_SCHEDULED");
        assertThat(sender.callCount()).isEqualTo(1);
        assertThat(sender.lastUrl()).isEqualTo(endpointUrl);
        assertThat(sender.lastPayload()).isEqualTo(payload);
    }

    @Test
    void processOne_processesOnlyOnePendingDelivery() {
        UUID eventId = insertEvent("order.created", "{\"orderId\":\"ord_300\"}");
        UUID endpointA = insertEndpoint("A", "https://a.example/webhook");
        UUID endpointB = insertEndpoint("B", "https://b.example/webhook");
        insertDelivery(eventId, endpointA, "PENDING");
        insertDelivery(eventId, endpointB, "PENDING");
        RecordingWebhookSender sender = new RecordingWebhookSender(SendResult.success());
        DeliveryWorker worker = worker(sender);

        ProcessResult result = worker.processOne();

        assertThat(result.outcome()).isEqualTo(ProcessOutcome.SUCCEEDED);
        assertThat(deliveryCountByStatus("SUCCEEDED")).isEqualTo(1);
        assertThat(deliveryCountByStatus("PENDING")).isEqualTo(1);
        assertThat(sender.callCount()).isEqualTo(1);
    }

    private DeliveryWorker worker(WebhookSender sender) {
        DeliveryClaimer deliveryClaimer = mock(DeliveryClaimer.class);
        when(deliveryClaimer.claimNextDueDelivery(anyString(), any(Instant.class), any(Duration.class)))
                .thenAnswer(invocation -> deliveryRepository.findFirstDueDelivery(invocation.getArgument(1)));

        return new DeliveryWorker(
                "worker-test",
                deliveryRepository,
                deliveryAttemptRepository,
                eventRepository,
                endpointRepository,
                sender,
                new RetryPolicy(),
                deliveryClaimer);
    }

    protected UUID insertEvent(String type, String payloadJson) {
        UUID eventId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO webhook_events(id, event_type, payload, created_at) VALUES (?, ?, ?, ?)",
                eventId,
                type,
                payloadJson,
                Instant.now());
        return eventId;
    }

    protected UUID insertEndpoint(String name, String url) {
        UUID endpointId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO webhook_endpoints(id, name, url, created_at) VALUES (?, ?, ?, ?)",
                endpointId,
                name,
                url,
                Instant.now());
        return endpointId;
    }

    protected UUID insertDelivery(UUID eventId, UUID endpointId, String status) {
        UUID deliveryId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO webhook_deliveries(id, event_id, endpoint_id, status, created_at) VALUES (?, ?, ?, ?, ?)",
                deliveryId,
                eventId,
                endpointId,
                status,
                Instant.now());
        return deliveryId;
    }

    protected String deliveryStatus(UUID deliveryId) {
        return jdbcTemplate.queryForObject(
                "SELECT status FROM webhook_deliveries WHERE id = ?",
                String.class,
                deliveryId);
    }

    protected int deliveryCountByStatus(String status) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM webhook_deliveries WHERE status = ?",
                Integer.class,
                status);
    }

    protected void assertNoExtraEventsCreated() {
        assertThat(countRows("webhook_events")).isEqualTo(1);
    }

    private static class RecordingWebhookSender implements WebhookSender {

        private final SendResult result;
        private int callCount;
        private String lastUrl;
        private String lastPayload;

        private RecordingWebhookSender(SendResult result) {
            this.result = result;
        }

        @Override
        public SendResult send(String url, String payload) {
            callCount++;
            lastUrl = url;
            lastPayload = payload;
            return result;
        }

        private int callCount() {
            return callCount;
        }

        private String lastUrl() {
            return lastUrl;
        }

        private String lastPayload() {
            return lastPayload;
        }
    }
}
