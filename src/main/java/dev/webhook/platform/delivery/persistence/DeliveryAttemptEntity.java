package dev.webhook.platform.delivery.persistence;

import dev.webhook.platform.delivery.domain.DeliveryAttemptStatus;
import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "webhook_delivery_attempts")
public class DeliveryAttemptEntity  {
    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;
    @Column(name = "delivery_id", nullable = false)
    private UUID deliveryId;
    @Column(name = "attempt_number", nullable = false)
    private Integer attemptNumber;
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private DeliveryAttemptStatus status;
    @Column(name = "http_status")
    private Integer httpStatus;
    @Column(name = "error_message", length = 2048)
    private String errorMessage;
    @Column(name = "started_at", nullable = false, updatable = false)
    private Instant startedAt;
    @Column(name = "finished_at")
    private Instant finishedAt;


    protected DeliveryAttemptEntity(){
        //required by JPA
    }

    public DeliveryAttemptEntity(UUID id,
                                 UUID deliveryId,
                                 Integer attemptNumber,
                                 DeliveryAttemptStatus status,
                                 Integer httpStatus, String errorMessage, Instant startedAt) {
        this.id = id;
        this.deliveryId = deliveryId;
        this.attemptNumber = attemptNumber;
        this.status = status;
        this.httpStatus = httpStatus;
        this.errorMessage = errorMessage;
        this.startedAt = startedAt;
    }

    public UUID getId() {
        return id;
    }

    public UUID getDeliveryId() {
        return deliveryId;
    }

    public Integer getAttemptNumber() {
        return attemptNumber;
    }

    public DeliveryAttemptStatus getStatus() {
        return status;
    }

    public Integer getHttpStatus() {
        return httpStatus;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public Instant getFinishedAt() {
        return finishedAt;
    }

    public void markedSucceeded(Integer httpStatus){
        this.httpStatus = httpStatus;
        this.status = DeliveryAttemptStatus.SUCCEEDED;
        this.finishedAt = Instant.now();
    }
    public void markedFailed(Integer httpStatus, String errorMessage){
        this.httpStatus = httpStatus;
        this.status = DeliveryAttemptStatus.FAILED;
        this.errorMessage = errorMessage;
        this.finishedAt = Instant.now();
    }
}
