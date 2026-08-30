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
import java.util.ArrayDeque;
import java.util.List;
import java.util.Queue;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ReliableDeliveryIntegrationTest extends ApiIntegrationTestSupport {

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
    void successOnFirstAttempt_marksDeliverySucceededAndRecordsOneAttempt() throws Exception {
        UUID deliveryId = createDeliveryThroughApi();
        RecordingWebhookSender sender = new RecordingWebhookSender(SendResult.success(204));

        ProcessResult result = worker(sender).processOne();

        assertThat(result.outcome()).isEqualTo(ProcessOutcome.SUCCEEDED);
        assertDelivery(deliveryId, "SUCCEEDED", 1, null, null);
        assertThat(assertAttempts(deliveryId))
                .containsExactly(new AttemptRow(1, "SUCCEEDED", 204, null));
        assertThat(sender.callCount()).isEqualTo(1);
    }

    @Test
    void firstTimeout_schedulesRetryAndRecordsFailedAttempt() throws Exception {
        UUID deliveryId = createDeliveryThroughApi();
        RecordingWebhookSender sender = new RecordingWebhookSender(
                SendResult.timeOut("read timed out"));

        ProcessResult result = worker(sender).processOne();

        assertThat(result.outcome()).isEqualTo(ProcessOutcome.FAILED);
        assertDelivery(deliveryId, "RETRY_SCHEDULED", 1, "read timed out", true);
        assertThat(assertAttempts(deliveryId))
                .containsExactly(new AttemptRow(1, "FAILED", null, "read timed out"));
    }

    @Test
    void scheduledRetryNotDue_workerDoesNothing() {
        UUID deliveryId = createScheduledRetryDelivery(1, Instant.now().plusSeconds(60));
        RecordingWebhookSender sender = new RecordingWebhookSender(SendResult.success());

        ProcessResult result = worker(sender).processOne();

        assertThat(result.outcome()).isEqualTo(ProcessOutcome.NO_PENDING_DELIVERY);
        assertDelivery(deliveryId, "RETRY_SCHEDULED", 1, "previous failure", true);
        assertThat(countRows("webhook_delivery_attempts")).isZero();
        assertThat(sender.callCount()).isZero();
    }

    @Test
    void dueScheduledRetryCanSucceedOnSecondAttempt() {
        UUID deliveryId = createScheduledRetryDelivery(1, Instant.now().minusSeconds(1));
        RecordingWebhookSender sender = new RecordingWebhookSender(SendResult.success(200));

        ProcessResult result = worker(sender).processOne();

        assertThat(result.outcome()).isEqualTo(ProcessOutcome.SUCCEEDED);
        assertDelivery(deliveryId, "SUCCEEDED", 2, null, null);
        assertThat(assertAttempts(deliveryId))
                .containsExactly(new AttemptRow(2, "SUCCEEDED", 200, null));
    }

    @Test
    void thirdFailure_marksDeliveryFailedAndRecordsThreeAttempts() {
        UUID deliveryId = createScheduledRetryDelivery(2, Instant.now().minusSeconds(1));
        insertAttempt(deliveryId, 1, "FAILED", null, "timeout");
        insertAttempt(deliveryId, 2, "FAILED", 503, "service unavailable");
        RecordingWebhookSender sender = new RecordingWebhookSender(
                SendResult.httpFailure(500, "still failing"));

        ProcessResult result = worker(sender).processOne();

        assertThat(result.outcome()).isEqualTo(ProcessOutcome.FAILED);
        assertDelivery(deliveryId, "FAILED", 3, "still failing", null);
        assertThat(assertAttempts(deliveryId))
                .containsExactly(
                        new AttemptRow(1, "FAILED", null, "timeout"),
                        new AttemptRow(2, "FAILED", 503, "service unavailable"),
                        new AttemptRow(3, "FAILED", 500, "still failing"));
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

    private UUID createDeliveryThroughApi() throws Exception {
        createEndpointAndReadId("Receiver", "https://receiver.example/webhook");
        postEvent("order.created", "{\"orderId\":\"ord_1\"}");
        return onlyDeliveryId();
    }

    private UUID createScheduledRetryDelivery(int attemptCount, Instant nextAttemptAt) {
        UUID eventId = insertEvent("order.created", "{\"orderId\":\"ord_retry\"}");
        UUID endpointId = insertEndpoint("Receiver", "https://receiver.example/webhook");
        UUID deliveryId = UUID.randomUUID();
        Instant createdAt = Instant.now().minusSeconds(120);
        Instant lastAttemptAt = Instant.now().minusSeconds(30);
        jdbcTemplate.update("""
                        INSERT INTO webhook_deliveries(
                            id, event_id, endpoint_id, status, created_at, attempt_count,
                            next_attempt_at, last_attempt_at, last_error, updated_at
                        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                deliveryId,
                eventId,
                endpointId,
                "RETRY_SCHEDULED",
                createdAt,
                attemptCount,
                nextAttemptAt,
                lastAttemptAt,
                "previous failure",
                lastAttemptAt);
        return deliveryId;
    }

    private UUID onlyDeliveryId() {
        return jdbcTemplate.queryForObject(
                "SELECT id FROM webhook_deliveries",
                (resultSet, rowNumber) -> UUID.fromString(resultSet.getString("id")));
    }

    private void insertAttempt(
            UUID deliveryId,
            int attemptNumber,
            String status,
            Integer httpStatus,
            String errorMessage) {
        jdbcTemplate.update("""
                        INSERT INTO webhook_delivery_attempts(
                            id, delivery_id, attempt_number, status, http_status,
                            error_message, started_at, finished_at
                        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                UUID.randomUUID(),
                deliveryId,
                attemptNumber,
                status,
                httpStatus,
                errorMessage,
                Instant.now().minusSeconds(30),
                Instant.now().minusSeconds(29));
    }

    private void assertDelivery(
            UUID deliveryId,
            String status,
            int attemptCount,
            String lastError,
            Boolean hasNextAttemptAt) {
        jdbcTemplate.queryForObject(
                """
                        SELECT status, attempt_count, last_error, next_attempt_at
                        FROM webhook_deliveries
                        WHERE id = ?
                        """,
                (resultSet, rowNumber) -> {
                    assertThat(resultSet.getString("status")).isEqualTo(status);
                    assertThat(resultSet.getInt("attempt_count")).isEqualTo(attemptCount);
                    assertThat(resultSet.getString("last_error")).isEqualTo(lastError);
                    Object nextAttemptAt = resultSet.getObject("next_attempt_at");
                    if (hasNextAttemptAt == null) {
                        assertThat(nextAttemptAt).isNull();
                    } else if (hasNextAttemptAt) {
                        assertThat(nextAttemptAt).isNotNull();
                    } else {
                        assertThat(nextAttemptAt).isNull();
                    }
                    return null;
                },
                deliveryId);
    }

    private List<AttemptRow> assertAttempts(UUID deliveryId) {
        return jdbcTemplate.query(
                """
                        SELECT attempt_number, status, http_status, error_message
                        FROM webhook_delivery_attempts
                        WHERE delivery_id = ?
                        ORDER BY attempt_number
                        """,
                (resultSet, rowNumber) -> new AttemptRow(
                        resultSet.getInt("attempt_number"),
                        resultSet.getString("status"),
                        (Integer) resultSet.getObject("http_status"),
                        resultSet.getString("error_message")),
                deliveryId);
    }

    private record AttemptRow(
            int attemptNumber,
            String status,
            Integer httpStatus,
            String errorMessage) {
    }

    private static class RecordingWebhookSender implements WebhookSender {

        private final Queue<SendResult> results;
        private int callCount;

        private RecordingWebhookSender(SendResult... results) {
            this.results = new ArrayDeque<>(List.of(results));
        }

        @Override
        public SendResult send(String url, String payload) {
            callCount++;
            return results.remove();
        }

        private int callCount() {
            return callCount;
        }
    }
}
