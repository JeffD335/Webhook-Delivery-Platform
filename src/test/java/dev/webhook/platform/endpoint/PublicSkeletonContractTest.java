package dev.webhook.platform.endpoint;

import static org.assertj.core.api.Assertions.assertThat;

import dev.webhook.platform.endpoint.api.CreateEndpointRequest;
import dev.webhook.platform.endpoint.api.EndpointResponse;
import dev.webhook.platform.endpoint.application.EndpointService;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Public smoke tests protect only the staff-provided type contract.
 * They are not Lab 1 behavior tests and do not contribute to Lab 1 completion.
 */
class PublicSkeletonContractTest {

    @Test
    void requestDto_hasFixedFields() {
        CreateEndpointRequest request = new CreateEndpointRequest("receiver", "https://example.com/hook");

        assertThat(request.name()).isEqualTo("receiver");
        assertThat(request.url()).isEqualTo("https://example.com/hook");
    }

    @Test
    void responseDto_hasFixedFields() {
        UUID id = UUID.randomUUID();
        Instant createdAt = Instant.parse("2026-07-07T08:00:00Z");

        EndpointResponse response = new EndpointResponse(
                id, "receiver", "https://example.com/hook", createdAt, true);

        assertThat(response.id()).isEqualTo(id);
        assertThat(response.name()).isEqualTo("receiver");
        assertThat(response.url()).isEqualTo("https://example.com/hook");
        assertThat(response.createdAt()).isEqualTo(createdAt);
    }

    @Test
    void service_exposesFixedOperations() throws NoSuchMethodException {
        assertThat(EndpointService.class.getMethod("create", CreateEndpointRequest.class))
                .isNotNull();
        assertThat(EndpointService.class.getMethod("get", UUID.class)).isNotNull();
        assertThat(EndpointService.class.getMethod("list")).isNotNull();
    }
}
