package dev.webhook.platform.delivery.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface DeliveryAttemptRepository extends JpaRepository<DeliveryAttemptEntity, UUID> {

    List<DeliveryAttemptEntity> findByDeliveryIdOrderByAttemptNumberAsc(UUID deliveryId);

 }
