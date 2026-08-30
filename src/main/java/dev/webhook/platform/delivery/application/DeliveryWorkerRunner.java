package dev.webhook.platform.delivery.application;

import java.util.Objects;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class DeliveryWorkerRunner {

    private final DeliveryWorkerFactory deliveryWorkerFactory;
    private final String workerId;
    public DeliveryWorkerRunner(
            DeliveryWorkerFactory deliveryWorkerFactory,
            @Value("${webhook.delivery.worker-id:worker-1}") String workerId) {
        this.deliveryWorkerFactory = Objects.requireNonNull(deliveryWorkerFactory);
        this.workerId = Objects.requireNonNull(workerId);
    }

    public ProcessResult processOne() {
        return deliveryWorkerFactory.create(workerId).processOne();
    }

}
