package dev.webhook.platform.endpoint.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

@ActiveProfiles("test")
@DataJpaTest
class EndpointRepositoryTest {

    @Autowired
    private EndpointRepository repository;

    @BeforeEach
    void clearDatabase() {
        repository.deleteAll();
    }

    @Test
    void findAll_whenNoEndpoints_returnsEmptyList() {
        List<EndpointEntity> endpoints = repository.findAll();

        assertThat(endpoints).isEmpty();
    }

    @Test
    void save_whenEndpointIsValid_persistsAllFields() {
        UUID id = UUID.randomUUID();
        Instant createdAt = Instant.parse("2026-07-09T10:15:30Z");

        EndpointEntity endpoint = new EndpointEntity(
                id,
                "Order Service",
                "https://example.com/webhooks/orders",
                createdAt
        );

        repository.save(endpoint);

        Optional<EndpointEntity> found = repository.findById(id);

        assertThat(found).isPresent();
        assertThat(found.get().getId()).isEqualTo(id);
        assertThat(found.get().getName()).isEqualTo("Order Service");
        assertThat(found.get().getUrl()).isEqualTo("https://example.com/webhooks/orders");
        assertThat(found.get().getCreatedAt()).isEqualTo(createdAt);
    }

    @Test
    void findById_whenEndpointDoesNotExist_returnsEmptyOptional() {
        UUID unknownId = UUID.randomUUID();

        Optional<EndpointEntity> found = repository.findById(unknownId);

        assertThat(found).isEmpty();
    }

    @Test
    void save_whenTwoEndpointsHaveSameUrl_allowsDuplicateUrl() {
        String sameUrl = "https://example.com/webhooks/orders";

        EndpointEntity first = new EndpointEntity(
                UUID.randomUUID(),
                "First Endpoint",
                sameUrl,
                Instant.parse("2026-07-09T10:15:30Z")
        );

        EndpointEntity second = new EndpointEntity(
                UUID.randomUUID(),
                "Second Endpoint",
                sameUrl,
                Instant.parse("2026-07-09T10:16:30Z")
        );

        repository.save(first);
        repository.save(second);

        List<EndpointEntity> endpoints = repository.findAll();

        assertThat(endpoints).hasSize(2);
        assertThat(endpoints)
                .extracting(EndpointEntity::getUrl)
                .containsExactlyInAnyOrder(sameUrl, sameUrl);
    }
}