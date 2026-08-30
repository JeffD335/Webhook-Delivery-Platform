package dev.webhook.platform.delivery.domain;

import dev.webhook.platform.endpoint.persistence.EndpointEntity;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for converting registered endpoints into delivery work.
 *
 * Run only this class with:
 * mvn -Dtest=DeliveryPlannerTest test
 */

class DeliveryPlannerTest {

    private final DeliveryPlanner planner = new DeliveryPlanner();

    @Test
    void zeroEndpoints_returnsEmptyPlan() {
        List<DeliveryPlan> plans = planner.plan(List.of());

        assertThat(plans)
                .as("No endpoint means no delivery work.")
                .isEmpty();
    }

    @Test
    void twoEndpoints_returnsTwoPlans() {
        UUID firstId = UUID.randomUUID();
        UUID secondId = UUID.randomUUID();

        List<DeliveryPlan> plans = planner.plan(List.of(
                endpoint(firstId, "first", "https://example.com/first"),
                endpoint(secondId, "second", "https://example.com/second")));

        assertThat(plans).hasSize(2);
        assertThat(plans)
                .extracting(DeliveryPlan::endpointId)
                .containsExactly(firstId, secondId);
    }

    @Test
    void duplicateEndpointUrls_areNotCollapsed() {
        String sharedUrl = "https://example.com/shared";
        UUID firstId = UUID.randomUUID();
        UUID secondId = UUID.randomUUID();

        List<DeliveryPlan> plans = planner.plan(List.of(
                endpoint(firstId, "first", sharedUrl),
                endpoint(secondId, "second", sharedUrl)));

        assertThat(plans)
                .extracting(DeliveryPlan::endpointId)
                .containsExactly(firstId, secondId);
    }

    @Test
    void newPlans_startPending() {
        List<DeliveryPlan> plans = planner.plan(List.of(
                endpoint(UUID.randomUUID(), "first", "https://example.com/first"),
                endpoint(UUID.randomUUID(), "second", "https://example.com/second")));

        assertThat(plans)
                .extracting(DeliveryPlan::status)
                .containsOnly(DeliveryStatus.PENDING);
    }

    private EndpointEntity endpoint(UUID id, String name, String url) {
        return new EndpointEntity(id, name, url, Instant.parse("2026-07-15T00:00:00Z"));
    }
}
