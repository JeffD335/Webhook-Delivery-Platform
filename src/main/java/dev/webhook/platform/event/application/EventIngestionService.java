package dev.webhook.platform.event.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import dev.webhook.platform.event.api.CreateEventRequest;
import dev.webhook.platform.event.api.EventResponse;

public interface EventIngestionService {

    EventResponse ingest(CreateEventRequest request);
}
