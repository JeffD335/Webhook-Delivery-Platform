package dev.webhook.platform.event.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.webhook.platform.common.exception.BadRequestException;
import dev.webhook.platform.delivery.domain.DeliveryPlan;
import dev.webhook.platform.delivery.domain.DeliveryPlanner;
import dev.webhook.platform.delivery.persistence.DeliveryEntity;
import dev.webhook.platform.delivery.persistence.DeliveryRepository;
import dev.webhook.platform.endpoint.persistence.EndpointRepository;
import dev.webhook.platform.event.api.CreateEventRequest;
import dev.webhook.platform.event.api.EventResponse;
import dev.webhook.platform.event.domain.EventTypeValidator;
import dev.webhook.platform.event.persistence.EventEntity;
import dev.webhook.platform.event.persistence.EventRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import dev.webhook.platform.endpoint.persistence.EndpointEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class EventIngestionServiceImpl implements EventIngestionService {
    private static final Logger log = LoggerFactory.getLogger(EventIngestionServiceImpl.class);
    private final EventRepository eventRepository;
    private final DeliveryRepository deliveryRepository;
    private final EndpointRepository endpointRepository;
    private final DeliveryPlanner deliveryPlanner;
    private final ObjectMapper objectMapper;

    public EventIngestionServiceImpl(
            EventRepository eventRepository,
            DeliveryRepository deliveryRepository,
            EndpointRepository endpointRepository,
            DeliveryPlanner deliveryPlanner,
            ObjectMapper objectMapper) {
        this.eventRepository = eventRepository;
        this.deliveryRepository = deliveryRepository;
        this.endpointRepository = endpointRepository;
        this.deliveryPlanner = deliveryPlanner;
        this.objectMapper = objectMapper;
    }

    @Override
    @Transactional
    public EventResponse ingest(CreateEventRequest request) {
        validate((request));
        List<EndpointEntity> endpoints = endpointRepository.findAllByEnabledTrue();
        List<DeliveryPlan> deliveryPlans = deliveryPlanner.plan(endpoints);
        String payload = serializePayload(request.payload());
        UUID eventUuid = UUID.randomUUID();
        Instant created_At = Instant.now();
        eventRepository.save(new EventEntity(
                eventUuid,
                request.type(),
                payload,
                created_At
        ));

        log.info(
                "event delivery planning eventId={} type={} endpointCount={} deliveryCount={}",
                eventUuid,
                request.type(),
                endpoints.size(),
                deliveryPlans.size());

        List<DeliveryEntity> deliveryEntities = deliveryPlans.stream().map(plan -> new DeliveryEntity(
                UUID.randomUUID(),
                eventUuid,
                plan.endpointId(),
                plan.status(),
                Instant.now()
        )).toList();

        deliveryRepository.saveAll(deliveryEntities);
        return new EventResponse(
                eventUuid,
                request.type(),
                request.payload(),
                created_At,
                deliveryEntities.size()
        );
    }

    private void validate(CreateEventRequest request) {
        if (request == null) {
            throw new BadRequestException("INVALID_EVENT_REQUEST", "Event request is required");
        }

        if (!EventTypeValidator.isValid(request.type())) {
            throw new BadRequestException("INVALID_EVENT_TYPE", "Event type is invalid");
        }

        JsonNode payload = request.payload();
        if (payload == null || payload.isNull() || !payload.isObject()) {
            throw new BadRequestException("INVALID_EVENT_PAYLOAD", "Event payload must be a JSON object");
        }
    }

    private String serializePayload(JsonNode payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to serialize event payload", exception);
        }
    }
}
