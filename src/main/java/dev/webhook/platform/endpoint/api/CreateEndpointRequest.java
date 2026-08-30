package dev.webhook.platform.endpoint.api;

import dev.webhook.platform.endpoint.api.validation.ValidEndpointUrl;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * The fixed public request shape for Lab 1.
 *
 * Add validation annotations as part of Checkpoint 1C. Do not add fields.
 */
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
