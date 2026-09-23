package dev.webhook.platform.endpoint.api;

import dev.webhook.platform.endpoint.api.validation.ValidEndpointUrl;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateEndpointRequest(
        @NotBlank
        @Size(max = 100)
        String name,
        @NotBlank
        @Size(max = 2048)
        @ValidEndpointUrl
        String url) {

        public CreateEndpointRequest {
                name = name == null ? null : name.trim();
        }

}
