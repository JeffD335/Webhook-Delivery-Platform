package dev.webhook.platform.delivery.persistence;

import dev.webhook.platform.delivery.domain.DeliveryStatus;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DeliveryRepository extends JpaRepository<DeliveryEntity, UUID> {

    int countByEventId(UUID eventId);

    List<DeliveryEntity> findByStatus(DeliveryStatus status);

    Optional<DeliveryEntity> findFirstByStatusOrderByCreatedAtAsc(DeliveryStatus status);

    @Query("""
            select d
            from DeliveryEntity d
            where d.status = :pendingStatus
               or (d.status = :retryScheduledStatus and d.nextAttemptAt <= :now)
            order by d.createdAt asc
            """)
    List<DeliveryEntity> findDueDeliveries(
            @Param("pendingStatus") DeliveryStatus pendingStatus,
            @Param("retryScheduledStatus") DeliveryStatus retryScheduledStatus,
            @Param("now") Instant now,
            Pageable pageable);

    default Optional<DeliveryEntity> findFirstDueDelivery(Instant now) {
        return findDueDeliveries(
                        DeliveryStatus.PENDING,
                        DeliveryStatus.RETRY_SCHEDULED,
                        now,
                        PageRequest.of(0, 1))
                .stream()
                .findFirst();
    }

    /**
     * call inside a short transaction, then mark the returned row as claimed and
     * commit before doing any outbound HTTP work.
     *
     * This is PostgreSQL-specific because H2 does not model the same worker-queue locking behavior.
     */
    @Query(value = """
            SELECT *
            FROM webhook_deliveries
            WHERE status = 'PENDING'
               OR (status = 'RETRY_SCHEDULED' AND next_attempt_at <= :now)
               OR (status = 'IN_PROGRESS' AND claim_expires_at <= :now)
            ORDER BY created_at ASC
            LIMIT 1
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    Optional<DeliveryEntity> findFirstClaimableDeliveryForUpdate(@Param("now") Instant now);

    List<DeliveryEntity> findByStatusOrderByUpdatedAtDesc(DeliveryStatus status);

    List<DeliveryEntity> findAllByOrderByCreatedAtDesc();

}
