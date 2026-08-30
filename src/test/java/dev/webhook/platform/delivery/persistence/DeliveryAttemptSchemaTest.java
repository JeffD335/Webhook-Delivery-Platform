package dev.webhook.platform.delivery.persistence;

import dev.webhook.platform.testsupport.ApiIntegrationTestSupport;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DeliveryAttemptSchemaTest extends ApiIntegrationTestSupport {

    @BeforeEach
    void clearDatabase() {
        clearWebhookTables();
    }

    @Test
    void webhookDeliveryAttemptsTable_hasRequiredColumns() throws Exception {
        assertThat(tableExists("webhook_delivery_attempts")).isTrue();
        assertThat(columnExists("webhook_delivery_attempts", "id")).isTrue();
        assertThat(columnExists("webhook_delivery_attempts", "delivery_id")).isTrue();
        assertThat(columnExists("webhook_delivery_attempts", "attempt_number")).isTrue();
        assertThat(columnExists("webhook_delivery_attempts", "status")).isTrue();
        assertThat(columnExists("webhook_delivery_attempts", "http_status")).isTrue();
        assertThat(columnExists("webhook_delivery_attempts", "error_message")).isTrue();
        assertThat(columnExists("webhook_delivery_attempts", "started_at")).isTrue();
        assertThat(columnExists("webhook_delivery_attempts", "finished_at")).isTrue();
    }

    @Test
    void attemptNumber_isUniqueWithinOneDelivery() {
        UUID eventId = insertEvent("order.created", "{\"orderId\":\"ord_1\"}");
        UUID endpointId = insertEndpoint("Receiver", "https://receiver.example/webhook");
        UUID deliveryId = insertDelivery(eventId, endpointId, "PENDING");

        insertAttempt(deliveryId, 1, "STARTED");

        assertThatThrownBy(() -> insertAttempt(deliveryId, 1, "FAILED"))
                .isInstanceOf(Exception.class);
    }

    @Test
    void webhookDeliveries_hasRetryColumns() throws Exception {
        assertThat(columnExists("webhook_deliveries", "attempt_count")).isTrue();
        assertThat(columnExists("webhook_deliveries", "next_attempt_at")).isTrue();
        assertThat(columnExists("webhook_deliveries", "last_attempt_at")).isTrue();
        assertThat(columnExists("webhook_deliveries", "last_error")).isTrue();
        assertThat(columnExists("webhook_deliveries", "updated_at")).isTrue();
    }

    private void insertAttempt(UUID deliveryId, int attemptNumber, String status) {
        jdbcTemplate.update("""
                        INSERT INTO webhook_delivery_attempts(
                            id, delivery_id, attempt_number, status, started_at
                        ) VALUES (?, ?, ?, ?, ?)
                        """,
                UUID.randomUUID(),
                deliveryId,
                attemptNumber,
                status,
                Instant.now());
    }
}
