package dev.webhook.platform.endpoint.api;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.webhook.platform.endpoint.persistence.EndpointRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.hamcrest.Matchers.startsWith;
@ActiveProfiles("test")
@SpringBootTest
@AutoConfigureMockMvc
public class EndpointApiContractTest {
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
    void validRequest_whenCreatingEndpoint_returnsCreatedResource()  throws Exception{
        CreateEndpointRequest request = new CreateEndpointRequest("Test Order", "https://example.com/hooks");
        mockMvc.perform(
                post("/api/endpoints")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
                )
                .andExpect(status().isCreated())
                .andExpect(header().string(
                        "Location",
                        startsWith("/api/endpoints/")))
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.createdAt").exists())
                .andExpect(jsonPath("$.name").value("Test Order"))
                .andExpect(jsonPath("$.url").value("https://example.com/hooks")
        );
    }

    @Test
    void createdEndpoint_whenFetchedById_returnsSameResource() throws Exception{
        CreateEndpointRequest request = new CreateEndpointRequest("Test Order", "https://example.com/hooks");
        MvcResult mvcResult = mockMvc.perform(
                        post("/api/endpoints")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request))
                )
                .andReturn();
        JsonNode responseJson = objectMapper.readTree(mvcResult.getResponse().getContentAsString()
        );
        String id = responseJson.get("id").asText();
        mockMvc.perform(get("/api/endpoints/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.createdAt").exists())
                .andExpect(jsonPath("$.name").value("Test Order"))
                .andExpect(jsonPath("$.url").value("https://example.com/hooks"));
    }

    @Test
    void noEndpoints_whenListing_returnsEmptyArray() throws Exception {
        mockMvc.perform(
                get("/api/endpoints")
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$").isEmpty()
        );
    }

    @Test
    void relativeUrl_whenCreatingEndpoint_returnsValidationError() throws Exception {
        CreateEndpointRequest request = new CreateEndpointRequest("Test Order", "/relative/path");
            mockMvc.perform(
                            post("/api/endpoints")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(objectMapper.writeValueAsString(request))
                    )
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                    .andExpect(jsonPath("$.message").value("Request validation failed"))
                    .andExpect(jsonPath("$.fieldErrors.url").exists());
        }
    }
