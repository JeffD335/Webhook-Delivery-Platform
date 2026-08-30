package dev.webhook.platform.endpoint.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.webhook.platform.endpoint.persistence.EndpointEntity;
import dev.webhook.platform.endpoint.persistence.EndpointRepository;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Strict Lab 1 API tests.
 *
 * These tests focus on edge cases and error boundaries. They are expected to fail until the
 * baseline behavior and validation/error handling are implemented.
 * Run with:
 *
 *     mvn -Dtest=EndpointApiStrictTest test
 */
@ActiveProfiles("test")
@SpringBootTest
@AutoConfigureMockMvc
class EndpointApiStrictTest {

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

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidNames")
    void invalidName_whenCreatingEndpoint_returnsValidationErrorAndDoesNotInsertRow(String caseName, String invalidName) throws Exception {
        long before = repository.count();

        CreateEndpointRequest request = new CreateEndpointRequest(invalidName, "https://example.com/webhooks/orders");

        mockMvc.perform(post("/api/endpoints")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").value("Request validation failed"))
                .andExpect(jsonPath("$.fieldErrors.name").exists());

        assertThat(repository.count())
                .as(caseName + " must not modify durable state")
                .isEqualTo(before);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidUrls")
    void invalidUrl_whenCreatingEndpoint_returnsValidationErrorAndDoesNotInsertRow( String caseName, String invalidUrl) throws Exception {
        long before = repository.count();

        CreateEndpointRequest request = new CreateEndpointRequest("Order Service", invalidUrl);

        mockMvc.perform(post("/api/endpoints")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").value("Request validation failed"))
                .andExpect(jsonPath("$.fieldErrors.url").exists());

        assertThat(repository.count())
                .as(caseName + " must not modify durable state")
                .isEqualTo(before);
    }

    @Test
    void hundredCharacterNameWithOuterWhitespace_whenCreatingEndpoint_isAcceptedAfterTrim()
            throws Exception {
        String name = " " + "a".repeat(100) + " ";

        MvcResult result = mockMvc.perform(post("/api/endpoints")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CreateEndpointRequest(name, "https://example.com/webhooks/orders"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("a".repeat(100)))
                .andReturn();

        String id = objectMapper.readTree(result.getResponse().getContentAsString())
                .get("id")
                .asText();

        EndpointEntity saved = repository.findAll().stream()
                .filter(endpoint -> endpoint.getId().toString().equals(id))
                .findFirst()
                .orElseThrow();

        assertThat(saved.getName()).isEqualTo("a".repeat(100));
    }

    @Test
    void uppercaseHttpScheme_whenCreatingEndpoint_isAccepted() throws Exception {
        mockMvc.perform(post("/api/endpoints")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CreateEndpointRequest("Order Service", "HTTPS://example.com/webhooks/orders"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.url").value("HTTPS://example.com/webhooks/orders"));
    }

    @Test
    void malformedJson_whenCreatingEndpoint_returnsBadRequestAndDoesNotInsertRow() throws Exception {
        long before = repository.count();

        MvcResult result = mockMvc.perform(post("/api/endpoints")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Order Service","url":
                                """))
                .andExpect(status().isBadRequest())
                .andReturn();

        assertThat(repository.count()).isEqualTo(before);
        assertResponseDoesNotLeakInternals(result);
    }

    @Test
    void malformedUuid_whenFetchingEndpoint_returnsBadRequestAndDoesNotLeakInternals()
            throws Exception {
        MvcResult result = mockMvc.perform(get("/api/endpoints/not-a-uuid"))
                .andExpect(status().isBadRequest())
                .andReturn();

        assertResponseDoesNotLeakInternals(result);
    }

    @Test
    void validationErrorResponse_whenInputInvalid_doesNotLeakInternals() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/endpoints")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CreateEndpointRequest("Order Service", "/relative/path"))))
                .andExpect(status().isBadRequest())
                .andReturn();

        assertResponseDoesNotLeakInternals(result);
    }

    private static Stream<Arguments> invalidNames() {
        return Stream.of(
                Arguments.of("null name", null),
                Arguments.of("empty name", ""),
                Arguments.of("whitespace-only name", "   "),
                Arguments.of("trimmed name longer than 100 characters", "a".repeat(101))
        );
    }

    private static Stream<Arguments> invalidUrls() {
        return Stream.of(
                Arguments.of("null url", null),
                Arguments.of("empty url", ""),
                Arguments.of("whitespace-only url", "   "),
                Arguments.of("relative url", "/relative/path"),
                Arguments.of("unsupported scheme", "ftp://example.com/webhooks/orders"),
                Arguments.of("missing host", "https:///webhooks/orders"),
                Arguments.of("url with surrounding whitespace", " https://example.com/webhooks/orders "),
                Arguments.of("url longer than 2048 characters", "https://example.com/" + "a".repeat(2049))
        );
    }

    private void assertResponseDoesNotLeakInternals(MvcResult result) throws Exception {
        String body = result.getResponse().getContentAsString();

        assertThat(body)
                .doesNotContain("java.")
                .doesNotContain("Exception")
                .doesNotContain("StackTrace")
                .doesNotContain("stackTrace")
                .doesNotContain("org.springframework")
                .doesNotContain("SQL")
                .doesNotContain("webhook_platform");
    }
}
