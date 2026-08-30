package dev.webhook.platform.event.persistence;

import dev.webhook.platform.testsupport.ApiIntegrationTestSupport;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Schema and durable-state tests for Event ingestion.
 *
 * Enable this after V3 migration compiles cleanly.
 * Run only this class with:
 * mvn -Dtest=EventSchemaTest test
 */
class EventSchemaTest extends ApiIntegrationTestSupport {

    @Test
    void schema_hasEventTableRequiredColumns() throws Exception {
        assertThat(tableExists("webhook_events")).isTrue();
        assertThat(columnExists("webhook_events", "id")).isTrue();
        assertThat(columnExists("webhook_events", "event_type")).isTrue();
        assertThat(columnExists("webhook_events", "payload")).isTrue();
        assertThat(columnExists("webhook_events", "created_at")).isTrue();
    }

    @Test
    void schema_hasDeliveryTableRequiredColumns() throws Exception {
        assertThat(tableExists("webhook_deliveries")).isTrue();
        assertThat(columnExists("webhook_deliveries", "id")).isTrue();
        assertThat(columnExists("webhook_deliveries", "event_id")).isTrue();
        assertThat(columnExists("webhook_deliveries", "endpoint_id")).isTrue();
        assertThat(columnExists("webhook_deliveries", "status")).isTrue();
        assertThat(columnExists("webhook_deliveries", "created_at")).isTrue();
    }

    @Test
    void schema_supportsFindingDeliveriesByStatus() throws Exception {
        assertThat(indexOnColumnExists("webhook_deliveries", "status")).isTrue();
    }

    @Test
    void duplicateEventEndpointDelivery_isRejectedByDatabase() throws Exception {
        clearWebhookTables();
        UUID eventId = insertEvent();
        UUID endpointId = insertEndpoint();

        insertDelivery(eventId, endpointId);

        assertThatThrownBy(() -> insertDelivery(eventId, endpointId))
                .isInstanceOf(Exception.class);
    }

    private UUID insertEvent() {
        UUID eventId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO webhook_events(id, event_type, payload, created_at) VALUES (?, ?, ?, ?)",
                eventId,
                "order.created",
                "{}",
                Instant.now());
        return eventId;
    }

    private UUID insertEndpoint() {
        UUID endpointId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO webhook_endpoints(id, name, url, created_at) VALUES (?, ?, ?, ?)",
                endpointId,
                "orders",
                "https://example.com/webhook",
                Instant.now());
        return endpointId;
    }

    private void insertDelivery(UUID eventId, UUID endpointId) {
        jdbcTemplate.update(
                "INSERT INTO webhook_deliveries(id, event_id, endpoint_id, status, created_at) VALUES (?, ?, ?, ?, ?)",
                UUID.randomUUID(),
                eventId,
                endpointId,
                "PENDING",
                Instant.now());
    }
}
