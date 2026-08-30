package dev.webhook.platform.testsupport;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.webhook.platform.endpoint.api.CreateEndpointRequest;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.UUID;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ActiveProfiles("test")
@SpringBootTest
@AutoConfigureMockMvc
public abstract class ApiIntegrationTestSupport {

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected ObjectMapper objectMapper;

    @Autowired
    protected JdbcTemplate jdbcTemplate;

    @Autowired
    protected DataSource dataSource;

    protected ResultActions postJson(String path, Object body) throws Exception {
        return postRawJson(path, objectMapper.writeValueAsString(body));
    }

    protected ResultActions postRawJson(String path, String json) throws Exception {
        return mockMvc.perform(post(path)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json));
    }

    protected ResultActions postEvent(String type, String payloadJson) throws Exception {
        return postRawJson("/api/events", """
                {
                  "type": "%s",
                  "payload": %s
                }
                """.formatted(type, payloadJson));
    }

    protected UUID createEndpointAndReadId(String name, String url) throws Exception {
        MvcResult result = postJson("/api/endpoints", new CreateEndpointRequest(name, url))
                .andExpect(status().isCreated())
                .andReturn();

        return UUID.fromString(responseBody(result).get("id").asText());
    }

    protected JsonNode responseBody(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    protected int countRows(String tableName) {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + tableName, Integer.class);
    }

    protected void clearWebhookTables() {
        jdbcTemplate.update("DELETE FROM webhook_delivery_attempts");
        jdbcTemplate.update("DELETE FROM webhook_deliveries");
        jdbcTemplate.update("DELETE FROM webhook_events");
        jdbcTemplate.update("DELETE FROM webhook_endpoints");
    }

    protected boolean tableExists(String tableName) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             ResultSet tables = connection.getMetaData().getTables(null, null, null, new String[]{"TABLE"})) {
            while (tables.next()) {
                if (tableName.equalsIgnoreCase(tables.getString("TABLE_NAME"))) {
                    return true;
                }
            }
        }
        return false;
    }

    protected boolean columnExists(String tableName, String columnName) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             ResultSet columns = connection.getMetaData().getColumns(null, null, null, null)) {
            while (columns.next()) {
                if (tableName.equalsIgnoreCase(columns.getString("TABLE_NAME"))
                        && columnName.equalsIgnoreCase(columns.getString("COLUMN_NAME"))) {
                    return true;
                }
            }
        }
        return false;
    }

    protected boolean indexOnColumnExists(String tableName, String columnName) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             ResultSet indexes = connection.getMetaData().getIndexInfo(null, null, tableName, false, false)) {
            while (indexes.next()) {
                if (columnName.equalsIgnoreCase(indexes.getString("COLUMN_NAME"))) {
                    return true;
                }
            }
        }
        return false;
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
    protected UUID insertDeliveryWithUpdatedAt(
            UUID eventId,
            UUID endpointId,
            String status,
            Instant updatedAt) {

        UUID deliveryId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO webhook_deliveries(id, event_id, endpoint_id, status,created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?)",
                deliveryId,
                eventId,
                endpointId,
                status,
                updatedAt,
                updatedAt
        );
        return deliveryId;
    }
}
