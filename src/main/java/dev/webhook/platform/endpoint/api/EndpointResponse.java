package dev.webhook.platform.endpoint.api;

import java.time.Instant;
import java.util.UUID;


public record EndpointResponse(
        UUID id,
        String name,
        String url,
        Instant createdAt,
        boolean enabled
        ) {
}
