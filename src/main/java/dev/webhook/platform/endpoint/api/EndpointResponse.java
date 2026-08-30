package dev.webhook.platform.endpoint.api;

import java.time.Instant;
import java.util.UUID;

/**
 * The fixed public response shape for Lab 1.
 */
public record EndpointResponse(
        UUID id,
        String name,
        String url,
        Instant createdAt) {
}
