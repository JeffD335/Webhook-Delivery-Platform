package dev.webhook.platform.delivery.api;

import dev.webhook.platform.delivery.application.DeliveryWorkerPool;
import dev.webhook.platform.delivery.application.DeliveryWorkerPoolResult;
import dev.webhook.platform.delivery.application.DeliveryWorkerRunner;
import dev.webhook.platform.delivery.application.ProcessResult;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/delivery")
@ConditionalOnProperty(
        prefix = "webhook.delivery.manual-runner",
        name = "enabled",
        havingValue = "true")
public class ManualDeliveryWorkerController {

    private final DeliveryWorkerRunner deliveryWorkerRunner;
    private final DeliveryWorkerPool deliveryWorkerPool;

    public ManualDeliveryWorkerController(
            DeliveryWorkerRunner deliveryWorkerRunner,
            DeliveryWorkerPool deliveryWorkerPool) {
        this.deliveryWorkerRunner = deliveryWorkerRunner;
        this.deliveryWorkerPool = deliveryWorkerPool;
    }

    @PostMapping("/process-one")
    public ProcessResult processOne() {
        return deliveryWorkerRunner.processOne();
    }

    @PostMapping("/process-batch")
    public DeliveryWorkerPoolResult processBatch() {
        return deliveryWorkerPool.processBatchOnce();
    }
}
