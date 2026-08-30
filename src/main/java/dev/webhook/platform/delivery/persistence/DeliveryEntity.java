package dev.webhook.platform.delivery.persistence;

import dev.webhook.platform.delivery.domain.DeliveryStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "webhook_deliveries")
public class DeliveryEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "event_id", nullable = false, updatable = false)
    private UUID eventId;

    @Column(name = "endpoint_id", nullable = false, updatable = false)
    private UUID endpointId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private DeliveryStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;

    @Column(name = "next_attempt_at")
    private Instant nextAttemptAt;

    @Column(name = "last_attempt_at")
    private Instant lastAttemptAt;

    @Column(name = "last_error", columnDefinition = "TEXT")
    private String lastError;

    @Column(name = "claimed_by", length = 100)
    private String claimedBy;

    @Column(name = "claim_expires_at")
    private Instant claimExpiresAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected DeliveryEntity() {
        // Required by JPA.
    }

    public DeliveryEntity(
            UUID id,
            UUID eventId,
            UUID endpointId,
            DeliveryStatus status,
            Instant createdAt) {
        this(
                id,
                eventId,
                endpointId,
                status,
                createdAt,
                0,
                null,
                null,
                null,
                createdAt);
    }

    public DeliveryEntity(
            UUID id,
            UUID eventId,
            UUID endpointId,
            DeliveryStatus status,
            Instant createdAt,
            int attemptCount,
            Instant nextAttemptAt,
            Instant lastAttemptAt,
            String lastError,
            Instant updatedAt) {
        this.id = id;
        this.eventId = eventId;
        this.endpointId = endpointId;
        this.status = status;
        this.createdAt = createdAt;
        this.attemptCount = attemptCount;
        this.nextAttemptAt = nextAttemptAt;
        this.lastAttemptAt = lastAttemptAt;
        this.lastError = lastError;
        this.updatedAt = updatedAt;
    }

    public UUID getId() {
        return id;
    }

    public UUID getEventId() {
        return eventId;
    }

    public UUID getEndpointId() {
        return endpointId;
    }

    public DeliveryStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public int getAttemptCount() {
        return attemptCount;
    }

    public Instant getNextAttemptAt() {
        return nextAttemptAt;
    }

    public Instant getLastAttemptAt() {
        return lastAttemptAt;
    }

    public String getLastError() {
        return lastError;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public String getClaimedBy() {
        return claimedBy;
    }

    public Instant getClaimExpiresAt() {
        return claimExpiresAt;
    }

    public int nextAttemptNumber() {
        return attemptCount + 1;
    }

    public void claim(String workerId, Instant claimExpiresAt, Instant updatedAt) {
        this.status = DeliveryStatus.IN_PROGRESS;
        this.claimedBy = workerId;
        this.claimExpiresAt = claimExpiresAt;
        this.updatedAt = updatedAt;
    }

    public void recordAttemptStarted(Instant startedAt) {
        this.attemptCount += 1;
        this.lastAttemptAt = startedAt;
        this.updatedAt = startedAt;
    }

    public void markSucceeded() {
        markSucceeded(Instant.now());
    }

    public void markSucceeded(Instant updatedAt) {
        this.status = DeliveryStatus.SUCCEEDED;
        this.nextAttemptAt = null;
        this.lastError = null;
        clearClaimFields();
        this.updatedAt = updatedAt;
    }

    public void markFailed() {
        markFailed(null, Instant.now());
    }

    public void markFailed(String errorMessage, Instant updatedAt) {
        this.status = DeliveryStatus.FAILED;
        this.nextAttemptAt = null;
        this.lastError = errorMessage;
        clearClaimFields();
        this.updatedAt = updatedAt;
    }

    public void scheduleRetry(Instant nextAttemptAt, String errorMessage, Instant updatedAt) {
        this.status = DeliveryStatus.RETRY_SCHEDULED;
        this.nextAttemptAt = nextAttemptAt;
        this.lastError = errorMessage;
        clearClaimFields();
        this.updatedAt = updatedAt;
    }

    private void clearClaimFields() {
        this.claimedBy = null;
        this.claimExpiresAt = null;
    }
}
