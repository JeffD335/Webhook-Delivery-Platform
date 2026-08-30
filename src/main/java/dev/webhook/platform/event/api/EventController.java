package dev.webhook.platform.event.api;

import com.fasterxml.jackson.core.JsonProcessingException;
import dev.webhook.platform.event.application.EventIngestionService;
import jakarta.validation.Valid;
import java.net.URI;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
/**
 * HTTP boundary for event ingestion.
 */
@RestController
@RequestMapping("/api/events")
public class EventController {
    private static final Logger log = LoggerFactory.getLogger(EventController.class);
    private final EventIngestionService eventIngestionService;

    public EventController(EventIngestionService eventIngestionService) {
        this.eventIngestionService = eventIngestionService;
    }

    @PostMapping
    public ResponseEntity<EventResponse> create(@Valid @RequestBody CreateEventRequest request) {
        log.info("api event.create requested type={}", request.type());

        EventResponse eventResponse = eventIngestionService.ingest(request);
        log.info(
                "api event.created eventId={} type={} deliveryCount={}",
                eventResponse.id(),
                eventResponse.type(),
                eventResponse.deliveryCount());

        return ResponseEntity.created(URI.create("/api/events/" + eventResponse.id()))
                .body(eventResponse);
    }
}
