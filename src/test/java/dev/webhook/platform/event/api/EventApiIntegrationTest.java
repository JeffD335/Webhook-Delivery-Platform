package dev.webhook.platform.event.api;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import dev.webhook.platform.testsupport.ApiIntegrationTestSupport;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for the public Event ingestion API.
 *
 * Enable this after the event schema and ingestion service are ready enough for Spring to start.
 * Run only this class with:
 * mvn -Dtest=EventApiIntegrationTest test
 */
class EventApiIntegrationTest extends ApiIntegrationTestSupport {

    @BeforeEach
    void clearDatabase() {
        clearWebhookTables();
    }

    @Test
    void createEvent_whenEndpointIsDisabled_skipsDisabledEndpoint() throws Exception {
        UUID endpointId = createEndpointAndReadId("Test Disabled Endpoint", "https://receiver.example/webhook");

        mockMvc.perform(patch("/api/endpoints/{id}/enabled", endpointId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                        """
                        {
                            "enabled": false
                        }
                        """
                )).andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(false));

        postEvent("test.type",
                """
                        {
                            "message": "Test payload"
                        }
                        """
        )
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.deliveryCount").value(0));

        assertThat(countRows("webhook_events")).isEqualTo(1);
        assertThat(countRows("webhook_deliveries")).isZero();
    }

    @Test
    void validEvent_whenNoEndpoints_persistsEventAndReturnsDeliveryCountZero() throws Exception {
        MvcResult result = postEvent("order.created", "{\"orderId\":\"ord_123\"}")
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", startsWith("/api/events/")))
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.type").value("order.created"))
                .andExpect(jsonPath("$.payload.orderId").value("ord_123"))
                .andExpect(jsonPath("$.createdAt").exists())
                .andExpect(jsonPath("$.deliveryCount").value(0))
                .andReturn();

        UUID id = UUID.fromString(responseBody(result).get("id").asText());
        assertThat(result.getResponse().getHeader("Location"))
                .isEqualTo("/api/events/" + id);
        assertThat(countRows("webhook_events")).isEqualTo(1);
        assertThat(countRows("webhook_deliveries")).isZero();
    }

    @Test
    void validEvent_responseMatchesPersistedEventRow() throws Exception {
        MvcResult result = postEvent("order.created", "{\"orderId\":\"ord_123\",\"amount\":1000}")
                .andExpect(status().isCreated())
                .andReturn();

        JsonNode response = responseBody(result);
        PersistedEvent event = onlyPersistedEvent();

        assertThat(UUID.fromString(response.required("id").asText())).isEqualTo(event.id());
        assertThat(response.required("type").asText()).isEqualTo(event.eventType());
        assertThat(response.required("payload")).isEqualTo(event.payload());
        assertThat(Instant.parse(response.required("createdAt").asText())).isEqualTo(event.createdAt());
    }

    @Test
    void invalidType_returnsBadRequestAndCreatesNoRows() throws Exception {
        postEvent("Order.Created", "{}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_EVENT_TYPE"));

        assertThat(countRows("webhook_events")).isZero();
        assertThat(countRows("webhook_deliveries")).isZero();
    }

    @Test
    void missingPayload_returnsBadRequestAndCreatesNoRows() throws Exception {
        postRawJson("/api/events", """
                {
                  "type": "order.created"
                }
                """)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_EVENT_PAYLOAD"));

        assertThat(countRows("webhook_events")).isZero();
        assertThat(countRows("webhook_deliveries")).isZero();
    }

    @Test
    void validEvent_whenTwoEndpoints_createsTwoPendingDeliveries() throws Exception {
        UUID firstEndpointId = createEndpointAndReadId("orders-a", "https://a.example/webhook");
        UUID secondEndpointId = createEndpointAndReadId("orders-b", "https://b.example/webhook");

        postEvent("order.created", "{\"orderId\":\"ord_123\"}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.deliveryCount").value(2));

        assertThat(countRows("webhook_events")).isEqualTo(1);
        assertThat(countRows("webhook_deliveries")).isEqualTo(2);
        assertThat(pendingDeliveryCount()).isEqualTo(2);
        assertThat(deliveryEndpointIds())
                .containsExactlyInAnyOrder(firstEndpointId, secondEndpointId);
    }

    private int pendingDeliveryCount() {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM webhook_deliveries WHERE status = 'PENDING'",
                Integer.class);
    }

    private java.util.List<UUID> deliveryEndpointIds() {
        return jdbcTemplate.query(
                "SELECT endpoint_id FROM webhook_deliveries",
                (resultSet, rowNumber) -> UUID.fromString(resultSet.getString("endpoint_id")));
    }

    private PersistedEvent onlyPersistedEvent() {
        return jdbcTemplate.queryForObject(
                "SELECT id, event_type, payload, created_at FROM webhook_events",
                (resultSet, rowNumber) -> new PersistedEvent(
                        UUID.fromString(resultSet.getString("id")),
                        resultSet.getString("event_type"),
                        parsePayload(resultSet.getString("payload")),
                        toInstant(resultSet.getObject("created_at"))));
    }

    private JsonNode parsePayload(String payload) {
        try {
            return objectMapper.readTree(payload);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Persisted payload is not valid JSON", exception);
        }
    }

    private Instant toInstant(Object value) {
        if (value instanceof Instant instant) {
            return instant;
        }
        if (value instanceof OffsetDateTime offsetDateTime) {
            return offsetDateTime.toInstant();
        }
        if (value instanceof Timestamp timestamp) {
            return timestamp.toInstant();
        }
        throw new IllegalArgumentException("Unsupported timestamp value: " + value);
    }

    private record PersistedEvent(
            UUID id,
            String eventType,
            JsonNode payload,
            Instant createdAt) {
    }
}
