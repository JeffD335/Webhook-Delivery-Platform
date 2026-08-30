package dev.webhook.platform.event.api;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.UUID;

public record EventResponse(
        UUID id,
        String type,
        JsonNode payload,
        Instant createdAt,
        int deliveryCount) {
}
