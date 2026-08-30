package dev.webhook.platform.endpoint.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;


public interface EndpointRepository extends JpaRepository<EndpointEntity, UUID> {
}
