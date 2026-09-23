package dev.webhook.platform.endpoint.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.webhook.platform.endpoint.persistence.EndpointEntity;
import dev.webhook.platform.endpoint.persistence.EndpointRepository;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import lombok.val;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Baseline Lab 1 API tests.
 *
 * These tests cover the main behavior that should work before moving to stricter edge cases.
 * Run with:
 *
 *     mvn -Dtest=EndpointApiBaselineTest test
 */
@ActiveProfiles("test")
@SpringBootTest
@AutoConfigureMockMvc
class EndpointApiBaselineTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private EndpointRepository repository;

    @BeforeEach
    void clearDatabase() {
        repository.deleteAll();
    }

    @Test
    void validRequest_whenCreatingEndpoint_returns201BodyLocationAndPersistsRow() throws Exception {
        MvcResult result = createEndpoint("Order Service", "https://example.com/webhooks/orders")
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", startsWith("/api/endpoints/")))
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.name").value("Order Service"))
                .andExpect(jsonPath("$.url").value("https://example.com/webhooks/orders"))
                .andExpect(jsonPath("$.createdAt").exists())
                .andExpect(jsonPath("$.enabled").value(true))
                .andReturn();

        JsonNode body = responseBody(result);
        UUID id = UUID.fromString(body.get("id").asText());
        Instant createdAt = Instant.parse(body.get("createdAt").asText());

        assertThat(result.getResponse().getHeader("Location"))
                .isEqualTo("/api/endpoints/" + id);

        EndpointEntity saved = repository.findById(id).orElseThrow();
        assertThat(saved.getId()).isEqualTo(id);
        assertThat(saved.getName()).isEqualTo("Order Service");
        assertThat(saved.getUrl()).isEqualTo("https://example.com/webhooks/orders");
        assertThat(saved.getCreatedAt()).isEqualTo(createdAt);
        assertThat(saved.isEnabled()).isTrue();
    }

    @Test
    void validRequest_whenNameHasOuterWhitespace_persistsTrimmedNameOnly() throws Exception {
        MvcResult result = createEndpoint("  Acme  Orders  ", "https://example.com/webhooks/orders")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Acme  Orders"))
                .andReturn();

        UUID id = UUID.fromString(responseBody(result).get("id").asText());
        EndpointEntity saved = repository.findById(id).orElseThrow();

        assertThat(saved.getName()).isEqualTo("Acme  Orders");
    }

    @Test
    void sameUrl_whenPostedTwice_createsTwoDifferentEndpointRecords() throws Exception {
        String sameUrl = "https://example.com/webhooks/orders";

        UUID firstId = createEndpointAndReadId("First Endpoint", sameUrl);
        UUID secondId = createEndpointAndReadId("Second Endpoint", sameUrl);

        assertThat(firstId).isNotEqualTo(secondId);
        assertThat(repository.findAll()).hasSize(2);
        assertThat(repository.findAll())
                .extracting(EndpointEntity::getUrl)
                .containsExactlyInAnyOrder(sameUrl, sameUrl);
    }

    @Test
    void createdEndpoint_whenFetchedById_returnsSameResource() throws Exception {
        MvcResult createResult = createEndpoint("Order Service", "https://example.com/webhooks/orders")
                .andExpect(status().isCreated())
                .andReturn();

        JsonNode created = responseBody(createResult);
        String id = created.get("id").asText();

        mockMvc.perform(get("/api/endpoints/{endpointId}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.name").value("Order Service"))
                .andExpect(jsonPath("$.url").value("https://example.com/webhooks/orders"))
                .andExpect(jsonPath("$.createdAt").value(created.get("createdAt").asText()));
    }

    @Test
    void noEndpoints_whenListing_returnsEmptyArray() throws Exception {
        mockMvc.perform(get("/api/endpoints"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void multipleEndpoints_whenListing_returnsAllRecordsWithoutAssumingOrder() throws Exception {
        UUID firstId = createEndpointAndReadId("Order Service", "https://example.com/webhooks/orders");
        UUID secondId = createEndpointAndReadId("Billing Service", "https://example.com/webhooks/billing");

        MvcResult listResult = mockMvc.perform(get("/api/endpoints"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andReturn();

        JsonNode array = responseBody(listResult);
        Set<String> ids = new HashSet<>();
        for (JsonNode item : array) {
            ids.add(item.get("id").asText());
            assertThat(item.hasNonNull("name")).isTrue();
            assertThat(item.hasNonNull("url")).isTrue();
            assertThat(item.hasNonNull("createdAt")).isTrue();
        }

        assertThat(array).hasSize(2);
        assertThat(ids).containsExactlyInAnyOrder(firstId.toString(), secondId.toString());
    }

    @Test
    void unknownUuid_whenFetchingEndpoint_returnsNotFoundContract() throws Exception {
        UUID unknownId = UUID.randomUUID();

        mockMvc.perform(get("/api/endpoints/{endpointId}", unknownId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ENDPOINT_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("Endpoint does not exist"));
    }

    @Test
    void setEnabledToFalse_whenEndpointExists_updatesAndReturnsEndpoint() throws Exception {
        MvcResult endpoint = createEndpoint("Taobao", "https://www.taobao.com")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.enabled").value(true))
                .andReturn();
        JsonNode created = responseBody(endpoint);
        UUID id = UUID.fromString(created.get("id").asText());
        assertThat(repository.findById(id).orElseThrow().isEnabled()).isTrue();

        mockMvc.perform(patch("/api/endpoints/{id}/enabled", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"enabled": false}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.name").value("Taobao"))
                .andExpect(jsonPath("$.url").value("https://www.taobao.com"))
                .andExpect(jsonPath("$.createdAt").value(created.get("createdAt").asText()))
                .andExpect(jsonPath("$.enabled").value(false));

        EndpointEntity updated = repository.findById(id).orElseThrow();
        assertThat(updated.isEnabled()).isFalse();
        assertThat(updated.getName()).isEqualTo("Taobao");
        assertThat(updated.getUrl()).isEqualTo("https://www.taobao.com");
        assertThat(repository.count()).isEqualTo(1);
    }

    private org.springframework.test.web.servlet.ResultActions createEndpoint(String name, String url) throws Exception {
        CreateEndpointRequest request = new CreateEndpointRequest(name, url);
        return mockMvc.perform(post("/api/endpoints")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)));
    }

    private UUID createEndpointAndReadId(String name, String url) throws Exception {
        MvcResult result = createEndpoint(name, url)
                .andExpect(status().isCreated())
                .andReturn();

        return UUID.fromString(responseBody(result).get("id").asText());
    }

    private JsonNode responseBody(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }
}
