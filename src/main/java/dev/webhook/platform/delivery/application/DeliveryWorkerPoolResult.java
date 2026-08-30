package dev.webhook.platform.delivery.application;

import java.util.List;
import java.util.Objects;

/**
 * Summary of one worker-pool batch.
 */
public record DeliveryWorkerPoolResult(
        int workerCount,
        int processedCount,
        int noWorkCount,
        int succeededCount,
        int failedCount,
        List<ProcessResult> results) {

    public DeliveryWorkerPoolResult {
        if (workerCount < 0) {
            throw new IllegalArgumentException("workerCount must not be negative");
        }
        if (processedCount < 0 || noWorkCount < 0 || succeededCount < 0 || failedCount < 0) {
            throw new IllegalArgumentException("result counts must not be negative");
        }
        results = List.copyOf(Objects.requireNonNull(results));
    }

    public static DeliveryWorkerPoolResult from(int workerCount, List<ProcessResult> results) {
        Objects.requireNonNull(results);

        int noWorkCount = 0;
        int succeededCount = 0;
        int failedCount = 0;

        for (ProcessResult result : results) {
            ProcessOutcome outcome = result.outcome();
            if (outcome == ProcessOutcome.NO_PENDING_DELIVERY) {
                noWorkCount++;
            } else if (outcome == ProcessOutcome.SUCCEEDED) {
                succeededCount++;
            } else if (outcome == ProcessOutcome.FAILED) {
                failedCount++;
            }
        }

        int processedCount = results.size() - noWorkCount;
        return new DeliveryWorkerPoolResult(
                workerCount,
                processedCount,
                noWorkCount,
                succeededCount,
                failedCount,
                results);
    }
}
