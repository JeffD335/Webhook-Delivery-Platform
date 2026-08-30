package dev.webhook.platform.event.persistence;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EventRepository extends JpaRepository<EventEntity, UUID> {
}
