package dev.webhook.platform.event.api;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * Public request body for ingesting a business event.
 */
public record CreateEventRequest(
        String type,
        JsonNode payload) {
}
