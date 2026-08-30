package dev.webhook.platform.delivery.application;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class DeliveryWorkerExecutorConfig {

    @Bean(destroyMethod = "shutdown")
    ExecutorService deliveryWorkerExecutor(
            @Value("${webhook.delivery.worker-count:3}") int workerCount) {
        if (workerCount < 1) {
            throw new IllegalArgumentException("workerCount must be at least 1");
        }
        return Executors.newFixedThreadPool(workerCount, deliveryWorkerThreadFactory());
    }

    private ThreadFactory deliveryWorkerThreadFactory() {
        AtomicInteger counter = new AtomicInteger();
        return runnable -> new Thread(runnable, "delivery-worker-" + counter.incrementAndGet());
    }
}
