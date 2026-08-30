package dev.webhook.platform.delivery.application;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
@Component
public class DeliveryWorkerPool {
    private static final Logger log = LoggerFactory.getLogger(DeliveryWorkerPool.class);
    private static final String WORKER_ID_PREFIX = "worker-";

    private final DeliveryWorkerFactory deliveryWorkerFactory;
    private final ExecutorService deliveryWorkerExecutor;
    private final int workerCount;

    public DeliveryWorkerPool(
            DeliveryWorkerFactory deliveryWorkerFactory,
            ExecutorService deliveryWorkerExecutor,
            @Value("${webhook.delivery.worker-count:3}") int workerCount) {
        this.deliveryWorkerFactory = Objects.requireNonNull(deliveryWorkerFactory);
        this.deliveryWorkerExecutor = Objects.requireNonNull(deliveryWorkerExecutor);
        if (workerCount < 1) {
            throw new IllegalArgumentException("workerCount must be at least 1");
        }
        this.workerCount = workerCount;
    }

    public DeliveryWorkerPoolResult processBatchOnce() {
        List<Future<ProcessResult>> futures = new ArrayList<>();

        for (int workerNumber = 1; workerNumber <= workerCount; workerNumber++) {
            String workerId = workerIdFor(workerNumber);
            Future<ProcessResult> future = deliveryWorkerExecutor.submit(
                    () -> deliveryWorkerFactory.create(workerId).processOne());
            futures.add(future);
        }

        List<ProcessResult> results = new ArrayList<>();
        for (Future<ProcessResult> future : futures) {
            results.add(resultFrom(future));
        }
        DeliveryWorkerPoolResult result = DeliveryWorkerPoolResult.from(workerCount, results);

        if (result.processedCount() > 0) {
            log.info( "worker pool batch finished workerCount={} processed={} succeeded={} failed={} noWork={}",
                    result.workerCount(),
                    result.processedCount(),
                    result.succeededCount(),
                    result.failedCount(),
                    result.noWorkCount());
        } else {
            log.debug(
                    "worker pool batch finished workerCount={} processed=0 noWork={}",
                    result.workerCount(),
                    result.noWorkCount());
        }
        return result;
    }

    private ProcessResult resultFrom(Future<ProcessResult> future) {
        try {
            return future.get();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for delivery workers", exception);
        } catch (ExecutionException exception) {
            throw new IllegalStateException("Delivery worker failed while processing batch", exception.getCause());
        }
    }

    String workerIdFor(int workerNumber) {
        if (workerNumber < 1) {
            throw new IllegalArgumentException("workerNumber must be at least 1");
        }
        return WORKER_ID_PREFIX + workerNumber;
    }
}
