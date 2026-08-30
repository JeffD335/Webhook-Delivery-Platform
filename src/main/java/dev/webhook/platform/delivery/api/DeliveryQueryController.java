package dev.webhook.platform.delivery.api;

import dev.webhook.platform.delivery.persistence.DeliveryAttemptRepository;
import dev.webhook.platform.delivery.persistence.DeliveryRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/deliveries")
public class DeliveryQueryController {
    private final DeliveryRepository deliveryRepository;
    private final DeliveryAttemptRepository deliveryAttemptRepository;

    public DeliveryQueryController(
            DeliveryRepository deliveryRepository,
            DeliveryAttemptRepository deliveryAttemptRepository) {
        this.deliveryRepository = deliveryRepository;
        this.deliveryAttemptRepository = deliveryAttemptRepository;
    }
    @GetMapping
    public List<DeliveryResponse> listDeliveries() {
        return deliveryRepository.findAllByOrderByCreatedAtDesc().stream().map(DeliveryResponse::from).toList();
    }

    @GetMapping("/{id}/attempts")
    public List<DeliveryAttemptResponse> listAttempts(@PathVariable UUID id) {
        return deliveryAttemptRepository.findByDeliveryIdOrderByAttemptNumberAsc(id).stream().map(DeliveryAttemptResponse::from).toList();
    }
}
