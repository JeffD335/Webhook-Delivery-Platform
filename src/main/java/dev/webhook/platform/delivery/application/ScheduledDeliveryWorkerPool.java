package dev.webhook.platform.delivery.application;

import lombok.val;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
@Component
@ConditionalOnProperty(prefix = "webhook.delivery.scheduler", name = "enabled", havingValue = "true")
public class ScheduledDeliveryWorkerPool {
    private static final Logger log = LoggerFactory.getLogger(ScheduledDeliveryWorkerPool.class);
    private final DeliveryWorkerPool deliveryWorkerPool;

    public ScheduledDeliveryWorkerPool(DeliveryWorkerPool deliveryWorkerPool) {
        this.deliveryWorkerPool = Objects.requireNonNull(deliveryWorkerPool);
    }

    @Scheduled(fixedDelayString = "${webhook.delivery.scheduler.fixed-delay-ms:1000}")
    public void processBatch() {
        DeliveryWorkerPoolResult result = deliveryWorkerPool.processBatchOnce();

        if (result.processedCount() > 0) {
            log.info(
                    "scheduler delivery batch processed workerCount={} processed={} succeeded={} failed={}",
                    result.workerCount(),
                    result.processedCount(),
                    result.succeededCount(),
                    result.failedCount());
        }
    }
}
