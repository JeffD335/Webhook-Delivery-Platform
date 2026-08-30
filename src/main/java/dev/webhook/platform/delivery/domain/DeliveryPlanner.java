package dev.webhook.platform.delivery.domain;

import dev.webhook.platform.endpoint.persistence.EndpointEntity;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Component;

/**
 * Pure business rule for converting registered endpoints into delivery work.
 */
@Component
public class DeliveryPlanner {

    public List<DeliveryPlan> plan(List<EndpointEntity> endpoints) {
        return endpoints.stream()
                .map(endpoint -> new DeliveryPlan(
                        endpoint.getId(),
                        DeliveryStatus.PENDING
                )).toList();
    }
}
