package dev.webhook.platform.delivery.claim;

import dev.webhook.platform.delivery.persistence.DeliveryEntity;
import dev.webhook.platform.delivery.persistence.DeliveryRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;


@Component
public class DeliveryClaimer {

    private final DeliveryRepository deliveryRepository;

    public DeliveryClaimer(DeliveryRepository deliveryRepository) {
        this.deliveryRepository = Objects.requireNonNull(deliveryRepository);
    }

    @Transactional
    public Optional<DeliveryEntity> claimNextDueDelivery(String workerId, Instant now, Duration leaseDuration) {
        Objects.requireNonNull(workerId);
        Objects.requireNonNull(now);
        Objects.requireNonNull(leaseDuration);

        Optional<DeliveryEntity> delivery = deliveryRepository.findFirstClaimableDeliveryForUpdate(now);
        if (delivery.isEmpty()) {
            return Optional.empty();
        }
        Instant claimExpiresAt = now.plus(leaseDuration);
        DeliveryEntity claimed = delivery.get();
        claimed.claim(workerId, claimExpiresAt, now);

        return Optional.of(deliveryRepository.save(claimed));
    }
}
