package dev.webhook.platform.event.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.webhook.platform.delivery.domain.DeliveryPlanner;
import dev.webhook.platform.delivery.persistence.DeliveryRepository;
import dev.webhook.platform.endpoint.persistence.EndpointRepository;
import dev.webhook.platform.event.api.CreateEventRequest;
import dev.webhook.platform.event.api.EventResponse;
import dev.webhook.platform.event.persistence.EventEntity;
import dev.webhook.platform.event.persistence.EventRepository;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EventIngestionServiceImplTest {

    @Mock
    private EventRepository eventRepository;

    @Mock
    private DeliveryRepository deliveryRepository;

    @Mock
    private EndpointRepository endpointRepository;

    @Mock
    private DeliveryPlanner deliveryPlanner;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private EventIngestionServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new EventIngestionServiceImpl(
                eventRepository,
                deliveryRepository,
                endpointRepository,
                deliveryPlanner,
                objectMapper);
    }

    @Test
    void ingest_responseMatchesPersistedEventFields() throws Exception {
        JsonNode payload = objectMapper.readTree("""
                {
                  "orderId": "ord_123",
                  "amount": 1000
                }
                """);

        when(endpointRepository.findAll()).thenReturn(List.of());
        when(deliveryPlanner.plan(anyList())).thenReturn(List.of());
        when(eventRepository.save(any(EventEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(deliveryRepository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));

        EventResponse response = service.ingest(new CreateEventRequest("order.created", payload));

        ArgumentCaptor<EventEntity> eventCaptor = ArgumentCaptor.forClass(EventEntity.class);
        verify(eventRepository).save(eventCaptor.capture());
        EventEntity attemptedEvent = eventCaptor.getValue();

        assertThat(response.id()).isEqualTo(attemptedEvent.getId());
        assertThat(response.type()).isEqualTo(attemptedEvent.getEventType());
        assertThat(response.payload()).isEqualTo(objectMapper.readTree(attemptedEvent.getPayload()));
        assertThat(response.createdAt()).isEqualTo(attemptedEvent.getCreatedAt());
    }
}
